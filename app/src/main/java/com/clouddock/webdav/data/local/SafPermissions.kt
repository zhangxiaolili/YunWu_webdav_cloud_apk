package com.clouddock.webdav.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object SafPermissions {

    fun takePersistablePermission(context: Context, uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (_: SecurityException) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                false
            } catch (_: Exception) {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun hasReadWrite(context: Context, treeUri: String?): Boolean {
        if (treeUri.isNullOrBlank()) return false
        val target = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == target && it.isReadPermission && it.isWritePermission
        }
    }

    fun release(context: Context, treeUri: String?) {
        if (treeUri.isNullOrBlank()) return
        val target = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                target,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun displayName(context: Context, treeUri: String?): String? {
        if (treeUri.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(treeUri)
            DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment?.substringAfterLast('/')
        }.getOrNull()
    }
}
