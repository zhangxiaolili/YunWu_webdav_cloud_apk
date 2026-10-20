package com.clouddock.webdav.data.sync

import android.content.Context
import android.util.Log
import com.clouddock.webdav.data.AccountRepository
import com.clouddock.webdav.data.local.CacheStore
import com.clouddock.webdav.data.local.LocalEntry
import com.clouddock.webdav.data.model.DavEntry
import com.clouddock.webdav.data.model.SyncBaseline
import com.clouddock.webdav.data.model.SyncProgress
import com.clouddock.webdav.data.model.SyncSummary
import com.clouddock.webdav.data.model.WebDavAccount
import com.clouddock.webdav.data.remote.WebDavClient
import com.clouddock.webdav.data.remote.WebDavClientFactory
import com.clouddock.webdav.data.remote.WebDavException
import com.clouddock.webdav.ui.util.mimeTypeFor
import com.clouddock.webdav.ui.util.normalizeRelativePath
import com.clouddock.webdav.ui.util.parentPath
import com.clouddock.webdav.ui.util.timesClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Two-way, non-destructive reconciliation.
 *
 * Newer content wins. A file present only locally is uploaded and a file present only remotely is
 * downloaded. Automatic sync never deletes a file on either side; explicit delete operations in the
 * file browser remove both copies immediately.
 */
class SyncEngine(
    private val context: Context,
    private val accountRepository: AccountRepository,
    private val clientFactory: WebDavClientFactory,
    private val baselineStore: SyncBaselineStore
) {
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun sync(
        account: WebDavAccount,
        onProgress: (SyncProgress) -> Unit = {}
    ): SyncSummary {
        val mutex = mutexes.getOrPut(account.id) { Mutex() }
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                doSync(account, onProgress)
            }
        }
    }

    private suspend fun doSync(
        account: WebDavAccount,
        onProgress: (SyncProgress) -> Unit
    ): SyncSummary {
        val password = accountRepository.passwordFor(account)
        val client = clientFactory.get(account, password)
        val cache = CacheStore(context, account)
        val baselines = baselineStore.load(account.id)
        val errors = mutableListOf<String>()
        var downloaded = 0
        var uploaded = 0
        var conflicts = 0

        onProgress(SyncProgress("读取本地缓存"))
        val localEntries = cache.listRecursive()
        val localByPath = localEntries.associateBy { it.path }
        val localDirs = localEntries.filter { it.isDirectory }
            .sortedBy { it.path.count { char -> char == '/' } }

        val remoteFiles = LinkedHashMap<String, DavEntry>()
        val remoteDirs = linkedSetOf("")
        onProgress(SyncProgress("扫描云端文件"))
        scanRemote(
            client = client,
            path = "",
            files = remoteFiles,
            directories = remoteDirs,
            onDirectory = { path ->
                onProgress(SyncProgress("扫描云端", currentPath = path))
            }
        )

        val totalFiles = remoteFiles.size + localEntries.count { !it.isDirectory }
        var processed = 0

        onProgress(SyncProgress("准备本地文件夹", 0, totalFiles))
        for (dir in remoteDirs.filter { it.isNotEmpty() }.sortedBy { it.count { c -> c == '/' } }) {
            try {
                cache.ensureDirectory(dir)
            } catch (e: Exception) {
                addError(errors, dir, e)
            }
        }

        // Mirror local-only directory structure to the server before uploading files.
        for (dir in localDirs) {
            if (dir.path in remoteDirs) continue
            if (remoteFiles.containsKey(dir.path)) {
                addError(errors, dir.path, IllegalStateException("本地为文件夹，云端为文件"))
                continue
            }
            try {
                ensureRemoteDirectory(client, dir.path, remoteDirs, remoteFiles)
            } catch (e: Exception) {
                addError(errors, dir.path, e)
            }
        }

        val remoteFileList = remoteFiles.values.sortedBy { it.path }
        for (remote in remoteFileList) {
            processed++
            val local = localByPath[remote.path]
            if (local?.isDirectory == true) {
                addError(errors, remote.path, IllegalStateException("本地为文件夹，云端为文件"))
                continue
            }
            val localOnly = local
            val baseline = baselines[remote.path]
            val decision = decide(remote, localOnly, baseline)
            if (decision.conflict) conflicts++
            try {
                when (decision.action) {
                    Action.NONE -> {
                        if (localOnly != null) {
                            baselines[remote.path] = baselineFor(remote, localOnly)
                        }
                    }

                    Action.DOWNLOAD -> {
                        onProgress(
                            SyncProgress(
                                phase = "下载 ${remote.name}",
                                current = processed,
                                total = totalFiles,
                                currentPath = remote.path
                            )
                        )
                        val localAfter = downloadOne(
                            client, cache, account, remote,
                            onBytes = { fraction ->
                                onProgress(
                                    SyncProgress(
                                        phase = "下载 ${remote.name}",
                                        current = processed,
                                        total = totalFiles,
                                        currentPath = remote.path,
                                        bytesProgress = fraction
                                    )
                                )
                            }
                        )
                        baselines[remote.path] = baselineFor(remote, localAfter)
                        downloaded++
                    }

                    Action.UPLOAD -> {
                        onProgress(
                            SyncProgress(
                                phase = "上传 ${remote.name}",
                                current = processed,
                                total = totalFiles,
                                currentPath = remote.path
                            )
                        )
                        val remoteAfter = uploadOne(
                            client, cache, localOnly ?: continue, remote.mimeType,
                            onBytes = { fraction ->
                                onProgress(
                                    SyncProgress(
                                        phase = "上传 ${remote.name}",
                                        current = processed,
                                        total = totalFiles,
                                        currentPath = remote.path,
                                        bytesProgress = fraction
                                    )
                                )
                            }
                        )
                        if (remoteAfter != null) {
                            val currentLocal = localEntry(cache, remote.path) ?: localOnly
                            if (currentLocal != null) baselines[remote.path] = baselineFor(remoteAfter, currentLocal)
                        } else {
                            baselines.remove(remote.path)
                        }
                        uploaded++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addError(errors, remote.path, e)
            }
        }

        val localOnlyFileList = localEntries
            .filter { !it.isDirectory && it.path !in remoteFiles }
            .filterNot { it.name.startsWith(".clouddock-tmp-") }
            .sortedBy { it.path }
        for (local in localOnlyFileList) {
            processed++
            if (remoteDirs.contains(local.path)) {
                addError(errors, local.path, IllegalStateException("云端为文件夹，本地为文件"))
                continue
            }
            try {
                onProgress(
                    SyncProgress(
                        phase = "上传 ${local.name}",
                        current = processed,
                        total = totalFiles,
                        currentPath = local.path
                    )
                )
                val parent = parentPath(local.path)
                if (parent.isNotEmpty()) {
                    ensureRemoteDirectory(client, parent, remoteDirs, remoteFiles)
                }
                val remoteAfter = uploadOne(
                    client, cache, local, mimeTypeFor(local.name, null),
                    onBytes = { fraction ->
                        onProgress(
                            SyncProgress(
                                phase = "上传 ${local.name}",
                                current = processed,
                                total = totalFiles,
                                currentPath = local.path,
                                bytesProgress = fraction
                            )
                        )
                    }
                )
                if (remoteAfter != null) {
                    val currentLocal = localEntry(cache, local.path) ?: local
                    baselines[local.path] = baselineFor(remoteAfter, currentLocal)
                }
                remoteFiles[local.path] = remoteAfter ?: DavEntry(
                    path = local.path,
                    name = local.name,
                    isDirectory = false,
                    size = local.size,
                    lastModified = local.lastModified,
                    mimeType = mimeTypeFor(local.name, null)
                )
                uploaded++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addError(errors, local.path, e)
            }
        }

        val livePaths = remoteFiles.keys + localByPath.keys
        baselines.keys.retainAll(livePaths)
        baselineStore.save(account.id, baselines)

        val summary = SyncSummary(
            downloaded = downloaded,
            uploaded = uploaded,
            conflicts = conflicts,
            errors = errors.size,
            scanned = totalFiles,
            errorDetails = errors.take(8)
        )
        onProgress(
            SyncProgress(
                phase = if (summary.errors > 0) "同步完成，部分文件失败" else "同步完成",
                current = totalFiles,
                total = totalFiles
            )
        )
        return summary
    }

    private suspend fun scanRemote(
        client: WebDavClient,
        path: String,
        files: MutableMap<String, DavEntry>,
        directories: MutableSet<String>,
        onDirectory: (String) -> Unit
    ) {
        if (path.count { it == '/' } > 64) {
            throw WebDavException("云端目录层级过深，已停止扫描")
        }
        onDirectory(path)
        val children = try {
            client.list(path)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw WebDavException(
                "无法扫描云端文件夹 ${path.ifEmpty { "/" }}：${friendlyError(e)}",
                cause = e
            )
        }
        for (child in children) {
            if (child.isDirectory) {
                if (directories.add(child.path)) {
                    scanRemote(client, child.path, files, directories, onDirectory)
                }
            } else {
                files[child.path] = child
            }
        }
    }

    private suspend fun downloadOne(
        client: WebDavClient,
        cache: CacheStore,
        account: WebDavAccount,
        remote: DavEntry,
        onBytes: (Float?) -> Unit
    ): LocalEntry {
        val tempDir = File(context.cacheDir, "clouddock/sync-${account.id}").apply { mkdirs() }
        val temp = File(tempDir, "download-${UUID.randomUUID()}.part")
        try {
            client.download(remote.path, temp) { done, total ->
                onBytes(if (total > 0) (done.toDouble() / total).toFloat() else null)
            }
            cache.importFile(temp, remote.path)
            return localEntry(cache, remote.path) ?: LocalEntry(
                path = remote.path,
                name = remote.name,
                isDirectory = false,
                size = remote.size,
                lastModified = System.currentTimeMillis()
            )
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private suspend fun uploadOne(
        client: WebDavClient,
        cache: CacheStore,
        local: LocalEntry,
        remoteMime: String?,
        onBytes: (Float?) -> Unit
    ): DavEntry? {
        val length = cache.size(local.path).takeIf { it > 0 } ?: local.size
        val mime = mimeTypeFor(local.name, remoteMime)
        val stream = cache.openInput(local.path)
        try {
            client.upload(
                relativePath = local.path,
                input = stream,
                length = length,
                mimeType = mime,
                onProgress = { done, total -> onBytes(if (total > 0) (done.toDouble() / total).toFloat() else null) }
            )
        } finally {
            runCatching { stream.close() }
        }
        return runCatching { client.stat(local.path, directory = false) }.getOrNull()
    }

    private fun decide(
        remote: DavEntry?,
        local: LocalEntry?,
        baseline: SyncBaseline?
    ): Decision {
        if (remote == null) return Decision(Action.NONE)
        if (local == null) return Decision(Action.DOWNLOAD)

        if (baseline == null) {
            if (remote.size == local.size && timesClose(remote.lastModified, local.lastModified)) {
                return Decision(Action.NONE)
            }
            return if (remoteIsClearlyNewer(remote, local)) {
                Decision(Action.DOWNLOAD)
            } else if (localIsClearlyNewer(remote, local)) {
                Decision(Action.UPLOAD)
            } else {
                Decision(Action.DOWNLOAD)
            }
        }

        val remoteChanged = if (!baseline.remoteEtag.isNullOrBlank() && !remote.etag.isNullOrBlank()) {
            baseline.remoteEtag != remote.etag
        } else {
            remote.size != baseline.remoteSize || !timesClose(remote.lastModified, baseline.remoteModified)
        }
        val localChanged = if (baseline.localSize >= 0 && local.size != baseline.localSize) {
            true
        } else {
            !timesClose(local.lastModified, baseline.localModified)
        }

        return when {
            !remoteChanged && !localChanged -> Decision(Action.NONE)
            remoteChanged && !localChanged -> Decision(Action.DOWNLOAD)
            !remoteChanged && localChanged -> Decision(Action.UPLOAD)
            else -> {
                val uploadWins = remote.lastModified > 0L &&
                    local.lastModified > 0L &&
                    local.lastModified > remote.lastModified + 2_000L
                Decision(if (uploadWins) Action.UPLOAD else Action.DOWNLOAD, conflict = true)
            }
        }
    }

    private fun remoteIsClearlyNewer(remote: DavEntry, local: LocalEntry): Boolean {
        if (remote.lastModified <= 0L) return false
        if (local.lastModified <= 0L) return true
        return remote.lastModified > local.lastModified + 2_000L
    }

    private fun localIsClearlyNewer(remote: DavEntry, local: LocalEntry): Boolean {
        if (local.lastModified <= 0L) return false
        if (remote.lastModified <= 0L) return false
        return local.lastModified > remote.lastModified + 2_000L
    }

    private fun baselineFor(remote: DavEntry, local: LocalEntry): SyncBaseline = SyncBaseline(
        remoteEtag = remote.etag,
        remoteModified = remote.lastModified,
        remoteSize = remote.size,
        localModified = local.lastModified,
        localSize = local.size
    )

    private fun localEntry(cache: CacheStore, path: String): LocalEntry? {
        if (!cache.contains(path) || cache.isDirectory(path)) return null
        return LocalEntry(
            path = path,
            name = path.substringAfterLast('/'),
            isDirectory = false,
            size = cache.size(path),
            lastModified = cache.lastModified(path)
        )
    }

    private suspend fun ensureRemoteDirectory(
        client: WebDavClient,
        path: String,
        remoteDirs: MutableSet<String>,
        remoteFiles: Map<String, DavEntry>
    ) {
        val normalized = normalizeRelativePath(path)
        if (normalized.isEmpty() || normalized in remoteDirs) return
        if (remoteFiles.containsKey(normalized)) {
            throw WebDavException("云端同名路径已是文件：$normalized")
        }
        val parent = parentPath(normalized)
        if (parent.isNotEmpty()) {
            ensureRemoteDirectory(client, parent, remoteDirs, remoteFiles)
        }
        client.makeDirectory(normalized)
        remoteDirs.add(normalized)
    }

    private fun addError(errors: MutableList<String>, path: String, error: Exception) {
        if (errors.size < 30) {
            errors += "${path.ifEmpty { "/" }}：${friendlyError(error)}"
        }
        Log.w(TAG, "Sync failure for $path", error)
    }

    private fun friendlyError(error: Throwable): String = when (error) {
        is WebDavException -> error.message ?: "WebDAV 请求失败"
        is IOException -> error.message?.takeIf { it.isNotBlank() }?.let { "网络错误：$it" } ?: "网络错误"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "未知错误"
    }

    private enum class Action { NONE, DOWNLOAD, UPLOAD }

    private data class Decision(val action: Action, val conflict: Boolean = false)

    private companion object {
        const val TAG = "CloudDockSync"
    }
}
