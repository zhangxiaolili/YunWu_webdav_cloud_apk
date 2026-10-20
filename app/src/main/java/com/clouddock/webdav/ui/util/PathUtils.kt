package com.clouddock.webdav.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * WebDAV paths are relative to the mount root, use '/' separators and never start with a slash.
 */
fun normalizeRelativePath(path: String): String {
    val segments = path.split('/').filter { it.isNotEmpty() }
    require(segments.none { it == "." || it == ".." }) { "路径不能包含 . 或 .." }
    return segments.joinToString("/")
}

fun joinPath(parent: String, name: String): String {
    val normalizedParent = normalizeRelativePath(parent)
    val safeName = name.trim()
    require(isValidFileName(safeName)) { "名称包含非法字符" }
    return if (normalizedParent.isEmpty()) safeName else "$normalizedParent/$safeName"
}

fun parentPath(path: String): String {
    val normalized = normalizeRelativePath(path)
    return if ('/' !in normalized) "" else normalized.substringBeforeLast('/')
}

fun baseName(path: String): String = normalizeRelativePath(path).substringAfterLast('/')

fun isValidFileName(name: String): Boolean {
    val trimmed = name.trim()
    return trimmed.isNotEmpty() &&
        trimmed != "." &&
        trimmed != ".." &&
        '/' !in trimmed &&
        '\u0000' !in trimmed &&
        trimmed.length <= 255
}

fun extensionOf(name: String): String {
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.length - 1) return ""
    return name.substring(dot + 1).lowercase(Locale.ROOT)
}

fun siblingPath(path: String, newName: String): String = joinPath(parentPath(path), newName)

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "未知"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (value >= 100) {
        String.format(Locale.getDefault(), "%.0f %s", value, units[index])
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }
}

fun formatDateTime(epochMs: Long, includeDate: Boolean = true): String {
    if (epochMs <= 0L) return "未知"
    val formatter = if (includeDate) {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    } else {
        DateTimeFormatter.ofPattern("HH:mm:ss")
    }
    return formatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMs))
}

fun formatRelativeTime(epochMs: Long): String {
    if (epochMs <= 0L) return "尚未同步"
    val delta = System.currentTimeMillis() - epochMs
    if (delta < 0) return formatDateTime(epochMs)
    return when {
        delta < 60_000 -> "刚刚"
        delta < 3_600_000 -> "${delta / 60_000} 分钟前"
        delta < 86_400_000 -> "${delta / 3_600_000} 小时前"
        delta < 7 * 86_400_000 -> "${delta / 86_400_000} 天前"
        else -> formatDateTime(epochMs).substringBefore(' ')
    }
}

fun timesClose(first: Long, second: Long, toleranceMs: Long = 2_000L): Boolean {
    if (first <= 0L && second <= 0L) return true
    if (first <= 0L || second <= 0L) return false
    return abs(first - second) <= toleranceMs
}

fun deviceLabelFromUrl(url: String): String {
    return runCatching {
        val parsed = java.net.URI(url)
        val host = parsed.host ?: return@runCatching url
        val port = if (parsed.port > 0) ":${parsed.port}" else ""
        val path = parsed.path?.let { if (it == "/") "" else it }.orEmpty()
        host + port + path
    }.getOrDefault(url)
}

fun initialsFor(name: String): String {
    val firstLetter = name.trim().firstOrNull { it.isLetter() }
    return (firstLetter ?: '云').uppercase(Locale.getDefault())
}
