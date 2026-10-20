package com.clouddock.webdav.data.local

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.clouddock.webdav.data.model.WebDavAccount
import com.clouddock.webdav.ui.util.normalizeRelativePath
import com.clouddock.webdav.ui.util.parentPath
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.UUID

data class LocalEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

data class CachedBytes(
    val bytes: ByteArray,
    val truncated: Boolean,
    val fullSize: Long
)

/**
 * Maps the remote root of an account to either:
 *  - a user-selected SAF directory (persisted read/write grant), or
 *  - a private app directory when no folder was selected.
 *
 * All relative WebDAV paths map one-to-one to relative local paths.
 */
class CacheStore(
    private val context: Context,
    private val account: WebDavAccount
) {
    val usesTreeUri: Boolean = !account.cacheTreeUri.isNullOrBlank()
    private val treeUri: Uri? = account.cacheTreeUri?.let(Uri::parse)
    private val internalRoot: File? = if (!usesTreeUri) {
        File(context.filesDir, "clouddock_cache/${account.id}").also { it.mkdirs() }
    } else {
        null
    }

    init {
        if (usesTreeUri && !SafPermissions.hasReadWrite(context, account.cacheTreeUri)) {
            throw CacheAccessException("本地文件夹的访问权限已失效，请在账户设置中重新选择")
        }
    }

    fun locationLabel(): String {
        if (!usesTreeUri) return "应用私有存储"
        return account.cacheLocationName?.takeIf { it.isNotBlank() }
            ?: SafPermissions.displayName(context, account.cacheTreeUri)
            ?: "已选文件夹"
    }

    fun contains(path: String): Boolean {
        val normalized = normalizeRelativePath(path)
        return if (!usesTreeUri) {
            internalFile(normalized, mustExist = false).exists()
        } else {
            resolveDocument(normalized) != null
        }
    }

    fun isDirectory(path: String): Boolean {
        val normalized = normalizeRelativePath(path)
        return if (!usesTreeUri) {
            internalFile(normalized, mustExist = false).isDirectory
        } else {
            resolveDocument(normalized)?.isDirectory == true
        }
    }

    fun lastModified(path: String): Long {
        val normalized = normalizeRelativePath(path)
        return if (!usesTreeUri) {
            internalFile(normalized, mustExist = false).let { if (it.exists()) it.lastModified() else 0L }
        } else {
            resolveDocument(normalized)?.lastModified() ?: 0L
        }
    }

    fun size(path: String): Long {
        val normalized = normalizeRelativePath(path)
        return if (!usesTreeUri) {
            internalFile(normalized, mustExist = false).let { if (it.isFile) it.length() else 0L }
        } else {
            val doc = resolveDocument(normalized) ?: return 0L
            if (doc.isDirectory) 0L else doc.length()
        }
    }

    fun ensureDirectory(path: String): Boolean {
        val normalized = normalizeRelativePath(path)
        if (normalized.isEmpty()) {
            if (!usesTreeUri) return internalRoot!!.exists() || internalRoot.mkdirs()
            return rootDocument()?.isDirectory == true
        }
        val segments = normalized.split('/')
        if (!usesTreeUri) {
            val dir = internalFile(normalized, mustExist = false)
            if (dir.isFile) throw CacheAccessException("本地存在同名文件：$normalized")
            return dir.isDirectory || dir.mkdirs()
        }
        var current = rootDocument() ?: throw CacheAccessException("无法访问本地缓存目录")
        if (!current.isDirectory) throw CacheAccessException("本地缓存目录不可用")
        for (segment in segments) {
            val existing = current.findFile(segment)
            current = when {
                existing == null -> current.createDirectory(segment)
                    ?: throw CacheAccessException("无法创建本地文件夹：$segment")
                existing.isDirectory -> existing
                else -> throw CacheAccessException("本地存在同名文件：$segment")
            }
        }
        return true
    }

    fun openInput(path: String): InputStream {
        val normalized = normalizeRelativePath(path)
        require(normalized.isNotEmpty()) { "文件路径为空" }
        if (!usesTreeUri) {
            val file = internalFile(normalized, mustExist = true)
            if (!file.isFile) throw CacheAccessException("文件不存在：$normalized")
            return FileInputStream(file)
        }
        val doc = resolveDocument(normalized)
            ?: throw CacheAccessException("本地缓存中不存在该文件")
        if (doc.isDirectory) throw CacheAccessException("无法读取文件夹")
        return context.contentResolver.openInputStream(doc.uri)
            ?: throw CacheAccessException("无法读取本地文件")
    }

    fun openOutput(path: String): OutputStream {
        val normalized = normalizeRelativePath(path)
        require(normalized.isNotEmpty()) { "文件路径为空" }
        if (!usesTreeUri) {
            val file = internalFile(normalized, mustExist = false)
            ensureLocalParent(file)
            if (file.isDirectory) throw CacheAccessException("无法写入文件夹")
            return FileOutputStream(file, false)
        }
        ensureDirectory(parentPath(normalized))
        val parent = resolveDocument(parentPath(normalized))
            ?: throw CacheAccessException("无法访问本地父文件夹")
        val existing = parent.findFile(basename(normalized))
        if (existing?.isDirectory == true) throw CacheAccessException("本地存在同名文件夹")
        val doc = existing ?: parent.createFile(mimeForName(basename(normalized)), basename(normalized))
        ?: throw CacheAccessException("无法创建本地文件")
        return openTruncatingOutputStream(doc.uri)
    }

    fun writeBytes(path: String, bytes: ByteArray): Boolean {
        openOutput(path).use { it.write(bytes) }
        return true
    }

    fun importFile(source: File, path: String) {
        val normalized = normalizeRelativePath(path)
        require(normalized.isNotEmpty()) { "文件路径为空" }
        if (!usesTreeUri) {
            val destination = internalFile(normalized, mustExist = false)
            ensureLocalParent(destination)
            if (destination.isDirectory) throw CacheAccessException("目标位置已存在同名文件夹")
            val temp = File(destination.parentFile, ".clouddock-tmp-${UUID.randomUUID()}")
            try {
                source.inputStream().use { input ->
                    FileOutputStream(temp, false).use { output -> input.copyTo(output, 128 * 1024) }
                }
                if (destination.exists() && !destination.delete()) {
                    throw CacheAccessException("无法替换本地旧文件")
                }
                if (!temp.renameTo(destination)) {
                    throw CacheAccessException("无法保存本地文件")
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
            return
        }

        ensureDirectory(parentPath(normalized))
        val parent = resolveDocument(parentPath(normalized))
            ?: throw CacheAccessException("无法访问本地父文件夹")
        val name = basename(normalized)
        val existing = parent.findFile(name)
        if (existing?.isDirectory == true) throw CacheAccessException("目标位置已存在同名文件夹")
        val document = existing ?: parent.createFile(mimeForName(name), name)
            ?: throw CacheAccessException("无法在本地创建文件")
        try {
            openTruncatingOutputStream(document.uri).use { output ->
                source.inputStream().use { input -> input.copyTo(output, 128 * 1024) }
            }
        } catch (e: Exception) {
            if (existing == null) runCatching { document.delete() }
            throw CacheAccessException("写入本地文件失败：${e.message}", e)
        }
    }

    fun readBytes(path: String, maxBytes: Int = Int.MAX_VALUE): CachedBytes {
        openInput(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            val output = java.io.ByteArrayOutputStream()
            var total = 0L
            var truncated = false
            while (true) {
                val remaining = maxBytes - output.size()
                if (remaining <= 0) {
                    truncated = input.read() != -1
                    break
                }
                val read = input.read(buffer, 0, minOf(buffer.size, remaining.coerceAtLeast(1)))
                if (read < 0) break
                output.write(buffer, 0, read)
                total += read
                if (output.size() >= maxBytes) {
                    truncated = input.read() != -1
                    break
                }
            }
            return CachedBytes(output.toByteArray(), truncated, maxOf(total, output.size().toLong()))
        }
    }

    fun listChildren(path: String): List<LocalEntry> {
        val normalized = normalizeRelativePath(path)
        if (!usesTreeUri) {
            val dir = internalFile(normalized, mustExist = false)
            if (!dir.isDirectory) return emptyList()
            return dir.listFiles().orEmpty().map { file ->
                LocalEntry(
                    path = childPath(normalized, file.name),
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                    lastModified = file.lastModified()
                )
            }
        }
        val dir = resolveDocument(normalized) ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles().mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            LocalEntry(
                path = childPath(normalized, name),
                name = name,
                isDirectory = doc.isDirectory,
                size = if (doc.isDirectory) 0L else doc.length(),
                lastModified = doc.lastModified()
            )
        }
    }

    fun listRecursive(): List<LocalEntry> {
        if (!usesTreeUri) {
            val root = internalRoot ?: return emptyList()
            if (!root.isDirectory) return emptyList()
            val result = mutableListOf<LocalEntry>()
            val stack = ArrayDeque<File>()
            stack.add(root)
            var guard = 0
            while (stack.isNotEmpty() && guard++ < 100_000) {
                val dir = stack.removeLast()
                val children = dir.listFiles().orEmpty()
                for (file in children) {
                    if (file.name.startsWith(".clouddock-tmp-")) continue
                    val relative = file.absolutePath.removePrefix(root.absolutePath).trimStart(File.separatorChar)
                        .replace(File.separatorChar, '/')
                    result += LocalEntry(
                        path = normalizeRelativePath(relative),
                        name = file.name,
                        isDirectory = file.isDirectory,
                        size = if (file.isFile) file.length() else 0L,
                        lastModified = file.lastModified()
                    )
                    if (file.isDirectory) stack.add(file)
                }
            }
            return result
        }

        val root = rootDocument() ?: return emptyList()
        val result = mutableListOf<LocalEntry>()
        val stack = ArrayDeque<Pair<String, DocumentFile>>()
        stack.add("" to root)
        var guard = 0
        while (stack.isNotEmpty() && guard++ < 100_000) {
            val (parent, dir) = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrElse { emptyArray() }
            for (doc in children) {
                val name = doc.name ?: continue
                if (name.startsWith(".clouddock-tmp-")) continue
                val path = childPath(parent, name)
                result += LocalEntry(
                    path = path,
                    name = name,
                    isDirectory = doc.isDirectory,
                    size = if (doc.isDirectory) 0L else doc.length(),
                    lastModified = doc.lastModified()
                )
                if (doc.isDirectory) stack.add(path to doc)
            }
        }
        return result
    }

    fun delete(path: String): Boolean {
        val normalized = normalizeRelativePath(path)
        require(normalized.isNotEmpty()) { "不能删除缓存根目录" }
        if (!usesTreeUri) return deleteFileRecursively(internalFile(normalized, mustExist = false))
        val doc = resolveDocument(normalized) ?: return true
        return runCatching { doc.delete() }.getOrDefault(false)
    }

    fun rename(path: String, newName: String): Boolean {
        require(newName.isNotBlank() && '/' !in newName) { "名称包含非法字符" }
        val normalized = normalizeRelativePath(path)
        require(normalized.isNotEmpty()) { "不能重命名缓存根目录" }
        val parent = parentPath(normalized)
        val oldName = basename(normalized)
        if (newName == oldName) return true
        if (contains(childPath(parent, newName))) {
            throw CacheAccessException("本地已存在同名文件或文件夹")
        }
        if (!usesTreeUri) {
            val oldFile = internalFile(normalized, mustExist = true)
            val newFile = File(oldFile.parentFile, newName)
            if (oldFile.isDirectory) {
                if (!oldFile.renameTo(newFile)) throw CacheAccessException("无法重命名本地文件夹")
            } else if (!oldFile.renameTo(newFile)) {
                throw CacheAccessException("无法重命名本地文件")
            }
            return true
        }
        val parentDoc = resolveDocument(parent)
            ?: throw CacheAccessException("无法访问本地父文件夹")
        if (parentDoc.findFile(newName) != null) {
            throw CacheAccessException("本地已存在同名文件或文件夹")
        }
        val oldDoc = parentDoc.findFile(oldName)
            ?: throw CacheAccessException("本地缓存中不存在该文件")
        if (!oldDoc.renameTo(newName)) throw CacheAccessException("此存储位置不支持重命名")
        return true
    }

    fun uriFor(path: String): Uri {
        val normalized = normalizeRelativePath(path)
        require(normalized.isNotEmpty())
        if (!usesTreeUri) {
            val file = internalFile(normalized, mustExist = true)
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val doc = resolveDocument(normalized) ?: throw CacheAccessException("本地文件不存在")
        return doc.uri
    }

    fun childPath(parent: String, childName: String): String {
        val normalizedParent = normalizeRelativePath(parent)
        return if (normalizedParent.isEmpty()) normalizeRelativePath(childName)
        else normalizeRelativePath("$normalizedParent/$childName")
    }

    fun cachedTreeSize(): Long {
        if (!usesTreeUri) return internalRoot?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        return listRecursive().filterNot { it.isDirectory }.sumOf { it.size }
    }

    private fun internalFile(path: String, mustExist: Boolean): File {
        val root = internalRoot ?: throw IllegalStateException("Internal cache is not available")
        val file = if (path.isEmpty()) root else File(root, path.replace('/', File.separatorChar))
        val canonicalRoot = root.canonicalFile
        val canonical = file.canonicalFile
        if (canonical != canonicalRoot && !canonical.path.startsWith(canonicalRoot.path + File.separator)) {
            throw CacheAccessException("非法路径")
        }
        if (mustExist && !canonical.exists()) throw CacheAccessException("文件不存在：$path")
        return canonical
    }

    private fun ensureLocalParent(file: File) {
        val parent = file.parentFile ?: throw CacheAccessException("无法访问父目录")
        if (!parent.exists() && !parent.mkdirs()) throw CacheAccessException("无法创建本地文件夹")
    }

    private fun rootDocument(): DocumentFile? {
        if (!usesTreeUri) return null
        if (!SafPermissions.hasReadWrite(context, account.cacheTreeUri)) {
            throw CacheAccessException("本地文件夹的访问权限已失效")
        }
        return DocumentFile.fromTreeUri(context, treeUri!!)
    }

    private fun resolveDocument(path: String): DocumentFile? {
        if (!usesTreeUri) return null
        val root = rootDocument() ?: return null
        if (path.isEmpty()) return root
        var current: DocumentFile = root
        for (segment in path.split('/')) {
            if (segment.isEmpty()) continue
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    private fun openTruncatingOutputStream(uri: Uri): OutputStream {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")
                ?: throw CacheAccessException("无法写入本地文件")
        } catch (_: Exception) {
            context.contentResolver.openOutputStream(uri, "w")
                ?: throw CacheAccessException("无法写入本地文件")
        }
    }

    private fun deleteFileRecursively(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> deleteFileRecursively(child) }
        }
        return file.delete()
    }

    private fun basename(path: String): String = path.substringAfterLast('/')

    private fun mimeForName(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}

class CacheAccessException(message: String, cause: Throwable? = null) : Exception(message, cause)
