package com.clouddock.webdav.data.model

import com.clouddock.webdav.ui.util.extensionOf
import com.clouddock.webdav.ui.util.kindForFile

/**
 * A saved WebDAV mount. The password is encrypted with an Android Keystore key before being
 * persisted; it is only decrypted in memory when a network client is created.
 */
data class WebDavAccount(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val encryptedPassword: String,
    val cacheTreeUri: String? = null,
    val cacheLocationName: String? = null,
    val allowUntrustedTls: Boolean = false,
    val syncOnOpen: Boolean = true,
    val syncIntervalMinutes: Int = 30,
    val lastSyncAt: Long = 0L,
    val lastSyncMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val accentIndex: Int = 0
)

data class DavEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val etag: String? = null,
    val mimeType: String? = null
) {
    val extension: String
        get() = extensionOf(name)

    val kind: FileKind
        get() = if (isDirectory) FileKind.FOLDER else kindForFile(name, mimeType)
}

enum class FileKind {
    FOLDER,
    TEXT,
    MARKDOWN,
    CODE,
    IMAGE,
    AUDIO,
    VIDEO,
    PDF,
    OFFICE,
    ARCHIVE,
    OTHER
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class SortKey {
    NAME,
    DATE,
    SIZE,
    TYPE
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val gridMode: Boolean = false,
    val sortKey: SortKey = SortKey.NAME,
    val sortAscending: Boolean = true,
    val lastAccountId: String? = null
)

/** Persisted metadata describing the last successfully synchronized version of a file. */
data class SyncBaseline(
    val remoteEtag: String? = null,
    val remoteModified: Long = 0L,
    val remoteSize: Long = -1L,
    val localModified: Long = 0L,
    val localSize: Long = -1L
)

data class SyncSummary(
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val conflicts: Int = 0,
    val errors: Int = 0,
    val scanned: Int = 0,
    val errorDetails: List<String> = emptyList()
) {
    val hasChanges: Boolean
        get() = downloaded > 0 || uploaded > 0

    fun describe(): String {
        val parts = buildList {
            if (uploaded > 0) add("上传 $uploaded")
            if (downloaded > 0) add("下载 $downloaded")
            if (conflicts > 0) add("冲突 $conflicts")
            if (errors > 0) add("失败 $errors")
        }
        return if (parts.isEmpty()) "所有文件已是最新" else parts.joinToString(" · ")
    }
}

data class SyncProgress(
    val phase: String,
    val current: Int = 0,
    val total: Int = 0,
    val currentPath: String? = null,
    val bytesProgress: Float? = null
)

data class AccountFormData(
    val id: String? = null,
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val cacheTreeUri: String? = null,
    val cacheLocationName: String? = null,
    val allowUntrustedTls: Boolean = false,
    val syncOnOpen: Boolean = true,
    val syncIntervalMinutes: Int = 30
)

val SyncIntervalOptions: List<Pair<Int, String>> = listOf(
    0 to "仅手动同步",
    15 to "每 15 分钟",
    30 to "每 30 分钟",
    60 to "每小时",
    180 to "每 3 小时",
    720 to "每 12 小时",
    1440 to "每天"
)
