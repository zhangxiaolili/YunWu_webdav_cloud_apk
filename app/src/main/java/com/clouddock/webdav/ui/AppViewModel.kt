package com.clouddock.webdav.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clouddock.webdav.CloudDockApplication
import com.clouddock.webdav.data.AccountRepository
import com.clouddock.webdav.data.local.CacheStore
import com.clouddock.webdav.data.local.CachedBytes
import com.clouddock.webdav.data.local.SafPermissions
import com.clouddock.webdav.data.model.AccountFormData
import com.clouddock.webdav.data.model.AppSettings
import com.clouddock.webdav.data.model.DavEntry
import com.clouddock.webdav.data.model.FileKind
import com.clouddock.webdav.data.model.SortKey
import com.clouddock.webdav.data.model.SyncBaseline
import com.clouddock.webdav.data.model.SyncProgress
import com.clouddock.webdav.data.model.SyncSummary
import com.clouddock.webdav.data.model.ThemeMode
import com.clouddock.webdav.data.model.WebDavAccount
import com.clouddock.webdav.data.remote.WebDavClient
import com.clouddock.webdav.data.remote.WebDavUrl
import com.clouddock.webdav.data.sync.SyncWorker
import com.clouddock.webdav.ui.util.decodeText
import com.clouddock.webdav.ui.util.encodeText
import com.clouddock.webdav.ui.util.isTextLike
import com.clouddock.webdav.ui.util.isValidFileName
import com.clouddock.webdav.ui.util.joinPath
import com.clouddock.webdav.ui.util.kindForFile
import com.clouddock.webdav.ui.util.mimeTypeFor
import com.clouddock.webdav.ui.util.normalizeRelativePath
import com.clouddock.webdav.ui.util.parentPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLException

sealed interface AppScreen {
    data object Browser : AppScreen
    data object Settings : AppScreen
    data class Text(val accountId: String, val path: String) : AppScreen
    data class Media(val accountId: String, val path: String, val kind: FileKind, val uri: Uri) : AppScreen
    data class Image(val accountId: String, val path: String, val uri: Uri) : AppScreen
}

data class TransferUi(
    val title: String,
    val progress: Float? = null,
    val detail: String? = null
)

data class AccountSyncUi(
    val running: Boolean = false,
    val progress: SyncProgress? = null
)

data class TextDocumentUi(
    val accountId: String,
    val path: String,
    val name: String,
    val text: String,
    val draft: String,
    val charsetName: String,
    val fileSize: Long,
    val truncated: Boolean,
    val isSaving: Boolean = false
)

data class AccountEditorRequest(val accountId: String?)

data class AppUiState(
    val accounts: List<WebDavAccount> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val selectedAccountId: String? = null,
    val currentPath: String = "",
    val entries: List<DavEntry> = emptyList(),
    val entriesLoading: Boolean = false,
    val entriesError: String? = null,
    val screen: AppScreen = AppScreen.Browser,
    val editorDocument: TextDocumentUi? = null,
    val operation: TransferUi? = null,
    val syncStates: Map<String, AccountSyncUi> = emptyMap(),
    val accountEditor: AccountEditorRequest? = null
) {
    val selectedAccount: WebDavAccount?
        get() = accounts.firstOrNull { it.id == selectedAccountId }
}

sealed interface UiEvent {
    data class Message(val text: String, val isError: Boolean = false) : UiEvent
    data class LaunchIntent(val intent: Intent) : UiEvent
}

private const val MAX_EDITABLE_BYTES = 3_000_000

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CloudDockApplication
    private val container = app.container
    private val repository: AccountRepository = container.accountRepository
    private val settingsRepository = container.settingsRepository

    private val initialSettings = settingsRepository.settings.value
    private val initialAccounts = repository.accounts.value
    private val initialAccount = initialAccounts.firstOrNull { it.id == initialSettings.lastAccountId }
        ?: initialAccounts.firstOrNull()

    private val _uiState = MutableStateFlow(
        AppUiState(
            accounts = initialAccounts,
            settings = initialSettings,
            selectedAccountId = initialAccount?.id
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var entriesJob: Job? = null
    private var foregroundJob: Job? = null
    private var operationGeneration: Long = 0
    private var entriesGeneration: Long = 0
    private val syncJobs = mutableMapOf<String, Job>()
    private val reconcileJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            repository.accounts.collect { accounts -> onAccountsChanged(accounts) }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        initialAccount?.let { account ->
            requestEntries(account.id, "", clear = true)
            if (initialAccount.syncOnOpen) {
                viewModelScope.launch {
                    delay(500)
                    syncAccount(account.id, automatic = true)
                }
            }
        }
    }

    fun selectAccount(accountId: String) {
        val account = repository.find(accountId) ?: return
        cancelForegroundOperation(showMessage = false)
        entriesJob?.cancel()
        operationGeneration++
        _uiState.update {
            it.copy(
                selectedAccountId = accountId,
                currentPath = "",
                entries = emptyList(),
                entriesLoading = true,
                entriesError = null,
                screen = AppScreen.Browser,
                editorDocument = null,
                accountEditor = null
            )
        }
        settingsRepository.update { it.copy(lastAccountId = accountId) }
        requestEntries(account.id, "", clear = true)
        if (account.syncOnOpen) {
            syncAccount(account.id, automatic = true)
        }
    }

    fun openFolder(path: String) {
        val account = repository.find(_uiState.value.selectedAccountId ?: return) ?: return
        val normalized = runCatching { normalizeRelativePath(path) }.getOrElse {
            showMessage(it.message ?: "路径无效", true)
            return
        }
        _uiState.update {
            it.copy(
                currentPath = normalized,
                entries = emptyList(),
                entriesLoading = true,
                entriesError = null,
                screen = AppScreen.Browser
            )
        }
        requestEntries(account.id, normalized, clear = true)
    }

    fun navigateUp() {
        val path = _uiState.value.currentPath
        openFolder(parentPath(path))
    }

    fun refreshEntries() {
        val state = _uiState.value
        val accountId = state.selectedAccountId ?: return
        requestEntries(accountId, state.currentPath, clear = false)
    }

    private fun requestEntries(accountId: String, path: String, clear: Boolean) {
        entriesJob?.cancel()
        val generation = ++entriesGeneration
        _uiState.update {
            if (it.selectedAccountId == accountId && it.currentPath == path) {
                it.copy(
                    entries = if (clear) emptyList() else it.entries,
                    entriesLoading = true,
                    entriesError = null
                )
            } else it
        }
        entriesJob = viewModelScope.launch {
            try {
                val account = repository.find(accountId) ?: return@launch
                val client = withContext(Dispatchers.IO) {
                    container.clientFactory.get(account, repository.passwordFor(account))
                }
                val entries = client.list(path)
                if (generation == entriesGeneration &&
                    _uiState.value.selectedAccountId == accountId &&
                    _uiState.value.currentPath == path
                ) {
                    _uiState.update {
                        it.copy(entries = entries, entriesLoading = false, entriesError = null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == entriesGeneration) {
                    _uiState.update {
                        it.copy(entriesLoading = false, entriesError = readableError(e))
                    }
                }
            }
        }
    }

    fun openAccountEditor(accountId: String?) {
        if (accountId != null && repository.find(accountId) == null) return
        _uiState.update { it.copy(accountEditor = AccountEditorRequest(accountId)) }
    }

    fun closeAccountEditor() {
        _uiState.update { it.copy(accountEditor = null) }
    }

    suspend fun testAccount(form: AccountFormData): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = WebDavUrl.normalizeBaseUrl(form.baseUrl)
            val existing = form.id?.let(repository::find)
            val password = when {
                form.password.isNotEmpty() -> form.password
                existing != null -> repository.passwordFor(existing)
                else -> ""
            }
            val testId = "test-${UUID.randomUUID()}"
            val temp = WebDavAccount(
                id = testId,
                name = form.name.ifBlank { "WebDAV" },
                baseUrl = normalizedUrl,
                username = form.username,
                encryptedPassword = "",
                allowUntrustedTls = form.allowUntrustedTls
            )
            try {
                val client = container.clientFactory.get(temp, password)
                client.testConnection()
            } finally {
                container.clientFactory.evict(testId)
            }
        }.recoverCatching { throw Exception(readableError(it)) }
    }

    suspend fun saveAccount(form: AccountFormData): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (form.name.isBlank() && form.baseUrl.isBlank()) {
                throw IllegalArgumentException("请填写服务器地址")
            }
            val normalizedUrl = WebDavUrl.normalizeBaseUrl(form.baseUrl)
            if (form.cacheTreeUri != null && !SafPermissions.hasReadWrite(app, form.cacheTreeUri)) {
                throw IllegalArgumentException("本地文件夹的访问权限已失效，请重新选择")
            }
            val existing = form.id?.let(repository::find)
            val passwordEncrypted = when {
                form.password.isNotEmpty() -> container.secretCipher.encrypt(form.password)
                existing != null -> existing.encryptedPassword
                else -> container.secretCipher.encrypt("")
            }
            val displayName = form.name.trim().ifBlank {
                val parsed = runCatching { android.net.Uri.parse(normalizedUrl) }.getOrNull()
                val host = parsed?.host ?: "WebDAV"
                val port = parsed?.port ?: -1
                if (port > 0 && port != 80 && port != 443) "$host:$port" else host
            }
            val connectionChanged = existing != null &&
                (existing.baseUrl != normalizedUrl ||
                    existing.username != form.username ||
                    existing.cacheTreeUri != form.cacheTreeUri)
            val account = WebDavAccount(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = displayName,
                baseUrl = normalizedUrl,
                username = form.username.trim(),
                encryptedPassword = passwordEncrypted,
                cacheTreeUri = form.cacheTreeUri,
                cacheLocationName = form.cacheLocationName,
                allowUntrustedTls = form.allowUntrustedTls,
                syncOnOpen = form.syncOnOpen,
                syncIntervalMinutes = form.syncIntervalMinutes,
                lastSyncAt = if (connectionChanged) 0L else existing?.lastSyncAt ?: 0L,
                lastSyncMessage = if (connectionChanged) null else existing?.lastSyncMessage,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                accentIndex = existing?.accentIndex ?: (_uiState.value.accounts.size % 6)
            )
            repository.upsert(account)
            if (connectionChanged) container.baselineStore.clear(account.id)
            container.clientFactory.evict(account.id)
            SyncWorker.schedule(app, account)
            _uiState.update { it.copy(accountEditor = null) }
            val wasSelected = _uiState.value.selectedAccountId == account.id
            if (existing == null) {
                selectAccount(account.id)
            } else if (wasSelected && connectionChanged) {
                selectAccount(account.id)
            }
            showMessage(if (existing == null) "已添加挂载 ${account.name}" else "已更新挂载 ${account.name}")
        }.recoverCatching { throw Exception(readableError(it)) }
    }

    fun deleteAccount(accountId: String) {
        val account = repository.find(accountId) ?: return
        if (_uiState.value.selectedAccountId == accountId) {
            cancelForegroundOperation(showMessage = false)
        }
        syncJobs.remove(accountId)?.cancel()
        reconcileJobs.remove(accountId)?.cancel()
        container.clientFactory.evict(accountId)
        container.baselineStore.clear(accountId)
        SyncWorker.cancel(app, accountId)
        repository.delete(accountId)
        if (account.cacheTreeUri == null) {
            runCatching {
                File(app.filesDir, "clouddock_cache/${account.id}").deleteRecursively()
            }
        }
        _uiState.update {
            if (it.accountEditor?.accountId == accountId) it.copy(accountEditor = null) else it
        }
        showMessage("已移除挂载 ${account.name}")
    }

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        settingsRepository.update { it.copy(dynamicColor = enabled) }
    }

    fun setGridMode(value: Boolean) {
        settingsRepository.update { it.copy(gridMode = value) }
    }

    fun toggleSortDirection() {
        settingsRepository.update { it.copy(sortAscending = !it.sortAscending) }
    }

    fun setSortKey(key: SortKey) {
        settingsRepository.update {
            if (it.sortKey == key) it else it.copy(sortKey = key, sortAscending = true)
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(screen = AppScreen.Settings) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(screen = AppScreen.Browser) }
    }

    fun closeViewer() {
        _uiState.update { it.copy(screen = AppScreen.Browser, editorDocument = null) }
    }

    fun syncAccount(accountId: String, automatic: Boolean = false) {
        if (syncJobs[accountId]?.isActive == true) {
            if (!automatic) showMessage("该挂载正在同步")
            return
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            updateSyncState(accountId) { AccountSyncUi(running = true) }
            try {
                val account = repository.find(accountId) ?: return@launch
                val summary = container.syncEngine.sync(account) { progress ->
                    updateSyncState(accountId) { AccountSyncUi(running = true, progress = progress) }
                }
                repository.updateSyncInfo(accountId, System.currentTimeMillis(), summary.describe())
                if (!automatic || summary.hasChanges || summary.errors > 0) {
                    val firstError = summary.errorDetails.firstOrNull()
                    val message = if (summary.errors > 0) {
                        "${summary.describe()}${firstError?.let { " · $it" }.orEmpty()}"
                    } else {
                        summary.describe()
                    }
                    showMessage(message, summary.errors > 0)
                }
                if (summary.hasChanges && _uiState.value.selectedAccountId == accountId) {
                    requestEntries(accountId, _uiState.value.currentPath, clear = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = "同步失败：${readableError(e)}"
                repository.updateSyncInfo(accountId, System.currentTimeMillis(), message)
                showMessage(message, true)
            } finally {
                _uiState.update { state ->
                    state.copy(syncStates = state.syncStates - accountId)
                }
                syncJobs.remove(accountId)
            }
        }
        syncJobs[accountId] = job
        job.start()
    }

    fun syncAllAccounts() {
        val accounts = repository.accounts.value
        if (accounts.isEmpty()) {
            showMessage("尚未添加 WebDAV 挂载")
            return
        }
        accounts.forEach { syncAccount(it.id, automatic = false) }
    }

    fun openEntry(entry: DavEntry) {
        if (entry.isDirectory) {
            openFolder(entry.path)
            return
        }
        val account = selectedAccountOrNull() ?: return
        launchOperation(account, "准备打开 ${entry.name}") { update ->
            val client = clientFor(account)
            val cache = container.cacheStoreFor(account)
            ensureCached(account, entry, client, cache, force = false, update = update)
            val uri = cache.uriFor(entry.path)
            when (entry.kind) {
                FileKind.TEXT, FileKind.MARKDOWN, FileKind.CODE -> {
                    update("读取文档", null, entry.name)
                    val document = buildTextDocument(account, entry, cache)
                    _uiState.update {
                        it.copy(
                            screen = AppScreen.Text(account.id, entry.path),
                            editorDocument = document
                        )
                    }
                }

                FileKind.IMAGE -> {
                    _uiState.update {
                        it.copy(screen = AppScreen.Image(account.id, entry.path, uri))
                    }
                }

                FileKind.AUDIO, FileKind.VIDEO -> {
                    _uiState.update {
                        it.copy(screen = AppScreen.Media(account.id, entry.path, entry.kind, uri))
                    }
                }

                else -> {
                    emitViewIntent(uri, mimeTypeFor(entry.name, entry.mimeType), entry.name)
                }
            }
        }
    }

    fun openWithExternalApp(entry: DavEntry) {
        if (entry.isDirectory) return
        val account = selectedAccountOrNull() ?: return
        launchOperation(account, "准备打开 ${entry.name}") { update ->
            val client = clientFor(account)
            val cache = container.cacheStoreFor(account)
            ensureCached(account, entry, client, cache, force = false, update = update)
            val uri = cache.uriFor(entry.path)
            emitViewIntent(uri, mimeTypeFor(entry.name, entry.mimeType), entry.name)
        }
    }

    fun shareEntry(entry: DavEntry) {
        if (entry.isDirectory) {
            showMessage("暂时只能分享文件")
            return
        }
        val account = selectedAccountOrNull() ?: return
        launchOperation(account, "准备分享 ${entry.name}") { update ->
            val client = clientFor(account)
            val cache = container.cacheStoreFor(account)
            ensureCached(account, entry, client, cache, force = false, update = update)
            val uri = cache.uriFor(entry.path)
            val mime = mimeTypeFor(entry.name, entry.mimeType)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(entry.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "分享 ${entry.name}").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            _events.tryEmit(UiEvent.LaunchIntent(chooser))
        }
    }

    fun downloadEntry(entry: DavEntry) {
        val account = selectedAccountOrNull() ?: return
        if (entry.isDirectory) {
            downloadFolder(account, entry)
        } else {
            launchOperation(account, "下载 ${entry.name}") { update ->
                val client = clientFor(account)
                val cache = container.cacheStoreFor(account)
                ensureCached(account, entry, client, cache, force = true, update = update)
                showMessage("已下载到 ${cache.locationLabel()} 中的 ${entry.path}")
            }
        }
    }

    private fun downloadFolder(account: WebDavAccount, folder: DavEntry) {
        launchOperation(account, "下载文件夹 ${folder.name}") { update ->
            val client = clientFor(account)
            val cache = container.cacheStoreFor(account)
            val files = mutableListOf<DavEntry>()
            val dirs = linkedSetOf(folder.path)
            update("扫描云端文件夹", null, folder.path)
            collectRemoteTree(client, folder.path, files, dirs, 0)
            if (files.size > 20_000) throw IllegalStateException("文件夹包含过多文件，已停止下载")
            update("准备本地文件夹", 0f, folder.path)
            cache.ensureDirectory(folder.path)
            dirs.sortedBy { it.count { c -> c == '/' } }.forEach { dir ->
                cache.ensureDirectory(dir)
            }
            var failures = 0
            var completed = 0
            for (remote in files.sortedBy { it.path }) {
                val fractionBase = completed.toFloat() / files.size.coerceAtLeast(1)
                val step = 1f / files.size.coerceAtLeast(1)
                update("下载 ${remote.name}", fractionBase, remote.path)
                try {
                    val temp = createTempFile("folder-download")
                    try {
                        client.download(remote.path, temp) { done, total ->
                            val inner = if (total > 0) done.toDouble().toFloat() / total else null
                            update(
                                "下载 ${remote.name}",
                                inner?.let { fractionBase + step * it } ?: fractionBase,
                                remote.path
                            )
                        }
                        cache.importFile(temp, remote.path)
                    } finally {
                        temp.delete()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures++
                }
                completed++
                update(
                    "下载文件夹 ${folder.name}",
                    completed.toFloat() / files.size.coerceAtLeast(1),
                    "${completed}/${files.size}"
                )
            }
            val message = if (failures == 0) {
                "文件夹已下载到 ${cache.locationLabel()}：${folder.path}"
            } else {
                "已下载 ${completed - failures}/${completed} 个文件，${failures} 个失败"
            }
            showMessage(message, failures > 0)
        }
    }

    fun createFolder(name: String) {
        val account = selectedAccountOrNull() ?: return
        val cleanName = validateNewName(name) ?: return
        val parent = _uiState.value.currentPath
        val target = safeJoin(parent, cleanName) ?: return
        launchOperation(account, "新建文件夹") { update ->
            val client = clientFor(account)
            val cache = container.cacheStoreFor(account)
            update("检查名称", null, target)
            if (client.stat(target, directory = true) != null) {
                throw IllegalStateException("云端已存在同名文件或文件夹")
            }
            client.makeDirectory(target)
            try {
                cache.ensureDirectory(target)
            } catch (e: Exception) {
                showMessage("云端文件夹已创建，但本地缓存创建失败：${readableError(e)}", true)
            }
            showMessage("已创建文件夹 $cleanName")
            requestEntries(account.id, parent, clear = false)
        }
    }

    fun createFile(name: String) {
        val account = selectedAccountOrNull() ?: return
        val cleanName = validateNewName(name) ?: return
        val parent = _uiState.value.currentPath
        val target = safeJoin(parent, cleanName) ?: return
        launchOperation(account, "新建文件") { update ->
            val client = clientFor(account)
            val cache = container.cacheStoreFor(account)
            update("检查名称", null, target)
            if (client.stat(target, directory = false) != null) {
                throw IllegalStateException("云端已存在同名文件或文件夹")
            }
            val mime = mimeTypeFor(cleanName, null)
            client.uploadBytes(target, ByteArray(0), mime)
            try {
                cache.ensureDirectory(parent)
                cache.writeBytes(target, ByteArray(0))
            } catch (e: Exception) {
                showMessage("文件已在云端创建，但本地缓存写入失败：${readableError(e)}", true)
            }
            showMessage("已创建文件 $cleanName")
            requestEntries(account.id, parent, clear = false)
            if (isTextLike(kindForFile(cleanName, mime))) {
                val entry = DavEntry(
                    path = target,
                    name = cleanName,
                    isDirectory = false,
                    size = 0L,
                    lastModified = System.currentTimeMillis(),
                    mimeType = mime
                )
                val document = buildTextDocument(account, entry, cache)
                _uiState.update {
                    it.copy(
                        screen = AppScreen.Text(account.id, target),
                        editorDocument = document
                    )
                }
            }
        }
    }

    fun renameEntry(entry: DavEntry, newName: String) {
        val account = selectedAccountOrNull() ?: return
        val cleanName = validateNewName(newName) ?: return
        if (cleanName == entry.name) return
        val target = safeJoin(parentPath(entry.path), cleanName) ?: return
        launchOperation(account, "重命名") { update ->
            val client = clientFor(account)
            val cache = container.cacheStoreFor(account)
            update("检查目标名称", null, target)
            if (client.stat(target, directory = entry.isDirectory) != null) {
                throw IllegalStateException("目标名称已存在")
            }
            val localExists = runCatching {
                cache.contains(entry.path) && cache.isDirectory(entry.path) == entry.isDirectory
            }.getOrDefault(false)
            if (localExists) {
                cache.rename(entry.path, cleanName)
            }
            try {
                client.move(entry.path, target, entry.isDirectory)
            } catch (e: Exception) {
                if (localExists) {
                    runCatching { cache.rename(target, entry.name) }
                }
                throw e
            }
            showMessage("已重命名为 $cleanName")
            requestEntries(account.id, _uiState.value.currentPath, clear = false)
            scheduleReconcile(account.id)
        }
    }

    fun deleteEntry(entry: DavEntry) {
        val account = selectedAccountOrNull() ?: return
        launchOperation(account, "删除 ${entry.name}") { update ->
            val client = clientFor(account)
            val cache = container.cacheStoreFor(account)
            update("删除本地缓存", null, entry.path)
            val localExists = cache.contains(entry.path)
            if (localExists && !cache.delete(entry.path)) {
                throw IllegalStateException("无法删除本地缓存，操作已取消")
            }
            update("删除云端文件", null, entry.path)
            try {
                client.delete(entry.path, entry.isDirectory)
            } catch (e: Exception) {
                showMessage("云端删除失败，本地缓存已删除；下次同步会从云端恢复：${readableError(e)}", true)
                requestEntries(account.id, _uiState.value.currentPath, clear = false)
                return@launchOperation
            }
            showMessage("已删除 ${entry.name}")
            requestEntries(account.id, _uiState.value.currentPath, clear = false)
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val account = selectedAccountOrNull() ?: return
        val parent = _uiState.value.currentPath
        launchOperation(account, "上传文件") { update ->
            val client = clientFor(account)
            val cache = container.cacheStoreFor(account)
            val reserved = _uiState.value.entries.map { it.name }.toMutableSet()
            var uploaded = 0
            val failures = mutableListOf<String>()
            uris.forEachIndexed { index, uri ->
                try {
                    val meta = queryDocumentMeta(uri)
                    update(
                        "上传 ${meta.first}",
                        index.toFloat() / uris.size,
                        meta.first
                    )
                    val localTemp = copyUriToTemp(uri, "upload")
                    try {
                        val remoteName = nextAvailableName(
                            client, parent, meta.first, reserved, directory = false
                        )
                        val target = joinPath(parent, remoteName)
                        val mime = app.contentResolver.getType(uri) ?: mimeTypeFor(remoteName, null)
                        client.upload(
                            target,
                            localTemp.inputStream(),
                            localTemp.length(),
                            mime
                        ) { done, total ->
                            val base = index.toFloat() / uris.size
                            val step = 1f / uris.size
                            update(
                                "上传 ${remoteName}",
                                if (total > 0) base + step * (done.toDouble().toFloat() / total) else null,
                                remoteName
                            )
                        }
                        cache.ensureDirectory(parent)
                        cache.importFile(localTemp, target)
                        uploaded++
                    } finally {
                        localTemp.delete()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += "${queryDisplayNameSafe(uri)}：${readableError(e)}"
                }
            }
            requestEntries(account.id, parent, clear = false)
            scheduleReconcile(account.id)
            if (failures.isEmpty()) {
                showMessage("已上传 $uploaded 个文件")
            } else {
                showMessage("已上传 $uploaded 个，${failures.size} 个失败：${failures.first()}", true)
            }
        }
    }

    fun uploadFolder(treeUri: Uri) {
        val account = selectedAccountOrNull() ?: return
        val parent = _uiState.value.currentPath
        launchOperation(account, "上传文件夹") { update ->
            val root = DocumentFile.fromTreeUri(app, treeUri)
                ?: throw IllegalArgumentException("无法访问所选文件夹")
            val folderName = root.name?.takeIf { it.isNotBlank() } ?: "上传的文件夹"
            val client = clientFor(account)
            val cache = container.cacheStoreFor(account)
            val reserved = _uiState.value.entries.map { it.name }.toMutableSet()
            val remoteRootName = nextAvailableName(
                client, parent, folderName, reserved, directory = true
            )
            val remoteRoot = joinPath(parent, remoteRootName)
            update("创建文件夹", null, folderName)
            client.makeDirectory(remoteRoot)
            cache.ensureDirectory(remoteRoot)
            var uploaded = 0
            var failures = 0
            suspend fun uploadTree(document: DocumentFile, remotePath: String) {
                val children = document.listFiles()
                for (child in children) {
                    val childName = child.name?.takeIf { it.isNotBlank() } ?: continue
                    if (!isValidFileName(childName)) continue
                    val childPath = safeJoin(remotePath, childName) ?: continue
                    if (child.isDirectory) {
                        update("上传文件夹", null, childPath)
                        client.makeDirectory(childPath)
                        cache.ensureDirectory(childPath)
                        uploadTree(child, childPath)
                    } else {
                        update("上传 $childName", null, childPath)
                        try {
                            val temp = copyUriToTemp(child.uri, "tree-upload")
                            try {
                                client.upload(
                                    childPath,
                                    temp.inputStream(),
                                    temp.length(),
                                    app.contentResolver.getType(child.uri)
                                        ?: mimeTypeFor(childName, null)
                                )
                                cache.ensureDirectory(parentPath(childPath))
                                cache.importFile(temp, childPath)
                                uploaded++
                            } finally {
                                temp.delete()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failures++
                        }
                    }
                }
            }
            uploadTree(root, remoteRoot)
            requestEntries(account.id, parent, clear = false)
            scheduleReconcile(account.id)
            showMessage(
                if (failures == 0) "文件夹已上传，共 $uploaded 个文件"
                else "已上传 $uploaded 个文件，$failures 个失败",
                failures > 0
            )
        }
    }

    fun cancelActiveOperation() {
        cancelForegroundOperation(showMessage = true)
    }

    fun postMessage(text: String, isError: Boolean = false) {
        showMessage(text, isError)
    }

    fun updateEditorDraft(text: String) {
        _uiState.update { state ->
            val doc = state.editorDocument ?: return@update state
            if (doc.draft == text) state else state.copy(editorDocument = doc.copy(draft = text))
        }
    }

    fun saveEditor(text: String, onResult: (Result<String>) -> Unit = {}) {
        val state = _uiState.value
        val doc = state.editorDocument ?: run {
            onResult(Result.failure(IllegalStateException("没有打开的文档")))
            return
        }
        val account = repository.find(doc.accountId) ?: run {
            onResult(Result.failure(IllegalStateException("账户不存在")))
            return
        }
        if (doc.isSaving) return
        if (doc.truncated) {
            onResult(Result.failure(IllegalStateException("文件过大，当前为只读预览")))
            return
        }
        _uiState.update {
            it.copy(editorDocument = it.editorDocument?.copy(draft = text, isSaving = true))
        }
        viewModelScope.launch(Dispatchers.IO) {
            val cache = runCatching { container.cacheStoreFor(account) }.getOrElse { error ->
                _uiState.update {
                    it.copy(editorDocument = it.editorDocument?.copy(isSaving = false))
                }
                withContext(Dispatchers.Main) { onResult(Result.failure(Exception(readableError(error)))) }
                return@launch
            }
            val bytes = encodeText(text, doc.charsetName)
            try {
                cache.writeBytes(doc.path, bytes)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(editorDocument = it.editorDocument?.copy(isSaving = false))
                }
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(Exception("保存到本地失败：${readableError(e)}")))
                }
                return@launch
            }

            var remoteError: String? = null
            try {
                val client = clientFor(account)
                client.upload(
                    doc.path,
                    bytes.inputStream(),
                    bytes.size.toLong(),
                    mimeTypeFor(doc.name, null)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                remoteError = readableError(e)
            }

            val localSize = runCatching { cache.size(doc.path) }.getOrDefault(bytes.size.toLong())
            _uiState.update {
                it.copy(
                    editorDocument = it.editorDocument?.copy(
                        text = text,
                        draft = text,
                        fileSize = localSize,
                        isSaving = false
                    )
                )
            }
            if (remoteError == null) {
                recordSyncedFile(account, doc.path)
            }
            requestEntries(account.id, _uiState.value.currentPath, clear = false)
            scheduleReconcile(account.id)
            val result = if (remoteError == null) {
                Result.success("已保存并同步到云端")
            } else {
                Result.success("已保存到本地缓存；云端上传失败，稍后将自动重试：$remoteError")
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun refreshAccountInfo() {
        refreshEntries()
    }

    /**
     * Records a baseline for a file that was just written by this app, so a following full sync
     * does not interpret our own upload as a conflict.
     */
    private suspend fun recordSyncedFile(account: WebDavAccount, path: String) {
        try {
            val client = clientFor(account)
            val remote = client.stat(path, directory = false) ?: return
            val cache = container.cacheStoreFor(account)
            if (!cache.contains(path) || cache.isDirectory(path)) return
            val baselines = container.baselineStore.load(account.id)
            baselines[path] = SyncBaseline(
                remoteEtag = remote.etag,
                remoteModified = remote.lastModified,
                remoteSize = remote.size,
                localModified = cache.lastModified(path),
                localSize = cache.size(path)
            )
            container.baselineStore.save(account.id, baselines)
        } catch (_: Exception) {
            // A failed baseline update only causes an extra comparison on the next sync.
        }
    }

    private suspend fun buildTextDocument(
        account: WebDavAccount,
        entry: DavEntry,
        cache: CacheStore
    ): TextDocumentUi {
        val size = runCatching { cache.size(entry.path) }.getOrDefault(entry.size)
        val bytes: CachedBytes = cache.readBytes(entry.path, MAX_EDITABLE_BYTES)
        val decoded = decodeText(bytes.bytes)
        val truncated = bytes.truncated || size > MAX_EDITABLE_BYTES
        return TextDocumentUi(
            accountId = account.id,
            path = entry.path,
            name = entry.name,
            text = decoded.text,
            draft = decoded.text,
            charsetName = decoded.charsetName,
            fileSize = size,
            truncated = truncated
        )
    }

    private suspend fun ensureCached(
        account: WebDavAccount,
        entry: DavEntry,
        client: WebDavClient,
        cache: CacheStore,
        force: Boolean,
        update: (String, Float?, String?) -> Unit
    ) {
        val exists = cache.contains(entry.path) && !cache.isDirectory(entry.path)
        val localModified = if (exists) cache.lastModified(entry.path) else 0L
        val localSize = if (exists) cache.size(entry.path) else -1L
        val remoteNewer = exists && entry.lastModified > 0L &&
            (localModified <= 0L || entry.lastModified > localModified + 2_000L)
        val localNewer = exists && localModified > 0L && entry.lastModified > 0L &&
            localModified > entry.lastModified + 2_000L
        val sizeMismatch = exists && entry.size >= 0L && localSize != entry.size
        val needsDownload = force || !exists || remoteNewer || (sizeMismatch && !localNewer)
        if (!needsDownload) return

        update(
            if (force) "下载 ${entry.name}" else "获取最新版本 ${entry.name}",
            null,
            entry.path
        )
        val temp = createTempFile("open")
        try {
            client.download(entry.path, temp) { done, total ->
                update(
                    if (force) "下载 ${entry.name}" else "获取最新版本 ${entry.name}",
                    if (total > 0) done.toDouble().toFloat() / total else null,
                    entry.path
                )
            }
            cache.importFile(temp, entry.path)
        } finally {
            temp.delete()
        }
    }

    private suspend fun collectRemoteTree(
        client: WebDavClient,
        path: String,
        files: MutableList<DavEntry>,
        directories: MutableSet<String>,
        depth: Int
    ) {
        if (depth > 64) throw IllegalStateException("目录层级过深")
        if (files.size > 20_000) throw IllegalStateException("文件夹包含过多文件")
        val children = client.list(path)
        for (child in children) {
            if (child.isDirectory) {
                if (directories.add(child.path)) {
                    collectRemoteTree(client, child.path, files, directories, depth + 1)
                }
            } else {
                files += child
            }
        }
    }

    private suspend fun nextAvailableName(
        client: WebDavClient,
        parent: String,
        desired: String,
        reserved: MutableSet<String>,
        directory: Boolean
    ): String {
        val safeName = desired.replace('/', '_').replace('\u0000', '_').trim().ifBlank { "未命名" }
        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val suffix = if (dot > 0) safeName.substring(dot) else ""
        for (index in 0..999) {
            val candidate = when (index) {
                0 -> safeName
                else -> "$base ($index)$suffix"
            }
            if (candidate in reserved) continue
            val target = safeJoin(parent, candidate) ?: continue
            if (client.stat(target, directory = directory) == null) {
                reserved += candidate
                return candidate
            }
        }
        throw IllegalStateException("无法生成可用的文件名")
    }

    private fun queryDocumentMeta(uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val safeName = name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "上传文件"
        return safeName.replace('/', '_') to size
    }

    private fun queryDisplayNameSafe(uri: Uri): String =
        runCatching { queryDocumentMeta(uri).first }.getOrDefault("文件")

    private fun copyUriToTemp(uri: Uri, prefix: String): File {
        val temp = createTempFile(prefix)
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output, 128 * 1024) }
            } ?: throw IOException("无法读取所选文件")
            return temp
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun createTempFile(prefix: String): File {
        val dir = File(app.cacheDir, "clouddock/operations").apply { mkdirs() }
        return File.createTempFile("$prefix-", ".part", dir)
    }

    private suspend fun clientFor(account: WebDavAccount): WebDavClient =
        withContext(Dispatchers.IO) {
            container.clientFactory.get(account, repository.passwordFor(account))
        }

    private fun launchOperation(
        account: WebDavAccount,
        initialTitle: String,
        block: suspend (update: (String, Float?, String?) -> Unit) -> Unit
    ) {
        val generation = ++operationGeneration
        foregroundJob?.cancel()
        _uiState.update { it.copy(operation = TransferUi(initialTitle)) }
        val update: (String, Float?, String?) -> Unit = { title, progress, detail ->
            if (generation == operationGeneration) {
                _uiState.update {
                    it.copy(operation = TransferUi(title, progress, detail))
                }
            }
        }
        foregroundJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                block(update)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(readableError(e), true)
            } finally {
                if (generation == operationGeneration) {
                    _uiState.update { it.copy(operation = null) }
                }
            }
        }
    }

    private fun cancelForegroundOperation(showMessage: Boolean) {
        if (foregroundJob?.isActive == true) {
            operationGeneration++
            foregroundJob?.cancel()
            foregroundJob = null
            _uiState.update { it.copy(operation = null) }
            if (showMessage) showMessage("已取消当前操作")
        }
    }

    private fun scheduleReconcile(accountId: String) {
        reconcileJobs[accountId]?.cancel()
        reconcileJobs[accountId] = viewModelScope.launch {
            delay(2_500)
            syncAccount(accountId, automatic = true)
        }
    }

    private fun onAccountsChanged(accounts: List<WebDavAccount>) {
        val selectedId = _uiState.value.selectedAccountId
        _uiState.update { state ->
            state.copy(
                accounts = accounts,
                accountEditor = state.accountEditor?.takeIf { request ->
                    request.accountId == null || accounts.any { it.id == request.accountId }
                }
            )
        }
        if (selectedId != null && accounts.none { it.id == selectedId }) {
            entriesJob?.cancel()
            cancelForegroundOperation(showMessage = false)
            val next = accounts.firstOrNull()
            if (next != null) {
                selectAccount(next.id)
            } else {
                _uiState.update {
                    it.copy(
                        selectedAccountId = null,
                        currentPath = "",
                        entries = emptyList(),
                        entriesLoading = false,
                        entriesError = null,
                        screen = AppScreen.Browser,
                        editorDocument = null
                    )
                }
                settingsRepository.update { it.copy(lastAccountId = null) }
            }
        }
    }

    private fun updateSyncState(accountId: String, transform: (AccountSyncUi) -> AccountSyncUi) {
        _uiState.update { state ->
            val current = state.syncStates[accountId] ?: AccountSyncUi()
            state.copy(syncStates = state.syncStates + (accountId to transform(current)))
        }
    }

    private fun selectedAccountOrNull(): WebDavAccount? {
        val id = _uiState.value.selectedAccountId ?: return null
        return repository.find(id)
    }

    private fun validateNewName(name: String): String? {
        val trimmed = name.trim()
        if (!isValidFileName(trimmed)) {
            showMessage("请输入有效的名称（不能包含 /）", true)
            return null
        }
        return trimmed
    }

    private fun safeJoin(parent: String, name: String): String? = runCatching {
        joinPath(parent, name)
    }.onFailure {
        showMessage(it.message ?: "名称无效", true)
    }.getOrNull()

    private fun emitViewIntent(uri: Uri, mime: String, name: String) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(view, "打开 $name").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        _events.tryEmit(UiEvent.LaunchIntent(chooser))
    }

    private fun showMessage(text: String, isError: Boolean = false) {
        _events.tryEmit(UiEvent.Message(text, isError))
    }

    override fun onCleared() {
        super.onCleared()
        foregroundJob?.cancel()
        entriesJob?.cancel()
        syncJobs.values.forEach { it.cancel() }
        reconcileJobs.values.forEach { it.cancel() }
    }
}

private fun readableError(error: Throwable): String = when (error) {
    is UnknownHostException -> "无法解析服务器地址，请检查网络与 URL"
    is SocketTimeoutException -> "连接服务器超时，请稍后重试"
    is SSLException -> "HTTPS 证书验证失败，可在账户高级设置中选择允许不受信任的证书"
    is com.clouddock.webdav.data.local.CacheAccessException -> error.message ?: "本地缓存不可用"
    is IOException -> error.message?.takeIf { it.isNotBlank() }?.let { "网络错误：$it" } ?: "网络错误"
    else -> error.message?.takeIf { it.isNotBlank() } ?: "未知错误"
}
