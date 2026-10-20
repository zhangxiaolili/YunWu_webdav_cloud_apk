package com.clouddock.webdav.ui.util

import android.webkit.MimeTypeMap
import com.clouddock.webdav.data.model.FileKind
import java.util.Locale

private val markdownExtensions = setOf("md", "markdown", "mdown", "mkd", "mdx")

private val codeExtensions = setOf(
    "kt", "kts", "java", "xml", "json", "json5", "yaml", "yml", "toml", "ini", "conf",
    "html", "htm", "xhtml", "css", "scss", "sass", "less", "js", "mjs", "cjs", "ts", "tsx",
    "jsx", "vue", "svelte", "py", "pyw", "go", "rs", "c", "h", "cc", "cpp", "cxx", "hpp",
    "cs", "php", "rb", "sh", "bash", "zsh", "fish", "sql", "gradle", "groovy", "lua", "swift",
    "dart", "m", "mm", "pl", "pm", "r", "scala", "bat", "cmd", "ps1", "psm1", "asm", "s",
    "proto", "graphql", "gql", "tex", "sty", "cls", "dockerfile", "makefile", "cmake",
    "properties", "env", "gitignore", "editorconfig"
)

private val textExtensions = setOf(
    "txt", "text", "log", "csv", "tsv", "rtf", "nfo", "srt", "vtt", "ass", "ssa",
    "diff", "patch", "license", "readme", "author", "changelog", "rst", "adoc"
)

private val imageExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "ico", "tif", "tiff"
)

private val audioExtensions = setOf(
    "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "amr", "mid", "midi"
)

private val videoExtensions = setOf(
    "mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "3g2", "flv", "ts", "m2ts", "ogv", "wmv"
)

private val officeExtensions = setOf(
    "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "pages", "numbers",
    "key", "epub"
)

private val archiveExtensions = setOf(
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "tgz", "tbz2", "iso", "jar", "apk"
)

private val specialTextNames = setOf(
    "dockerfile", "makefile", "cmakelists.txt", "license", "licence", "readme", "changelog",
    "authors", "notice", "gemfile", "podfile", "rakefile", "procfile", ".gitignore",
    ".editorconfig", ".env"
)

fun kindForFile(name: String, mimeType: String? = null): FileKind {
    val ext = extensionOf(name)
    val lowerName = name.lowercase(Locale.ROOT)
    return when {
        ext in markdownExtensions -> FileKind.MARKDOWN
        ext in imageExtensions -> FileKind.IMAGE
        ext in audioExtensions -> FileKind.AUDIO
        ext in videoExtensions -> FileKind.VIDEO
        ext == "pdf" -> FileKind.PDF
        ext in officeExtensions -> FileKind.OFFICE
        ext in archiveExtensions -> FileKind.ARCHIVE
        ext in codeExtensions || lowerName in specialTextNames -> FileKind.CODE
        ext in textExtensions -> FileKind.TEXT
        mimeType?.startsWith("image/", ignoreCase = true) == true -> FileKind.IMAGE
        mimeType?.startsWith("audio/", ignoreCase = true) == true -> FileKind.AUDIO
        mimeType?.startsWith("video/", ignoreCase = true) == true -> FileKind.VIDEO
        mimeType?.startsWith("text/", ignoreCase = true) == true -> FileKind.TEXT
        mimeType.equals("application/pdf", ignoreCase = true) -> FileKind.PDF
        mimeType.equals("application/json", ignoreCase = true) -> FileKind.CODE
        else -> FileKind.OTHER
    }
}

fun mimeTypeFor(name: String, serverMime: String? = null): String {
    val cleanServerMime = serverMime?.substringBefore(';')?.trim()
    if (!cleanServerMime.isNullOrBlank() &&
        cleanServerMime != "application/octet-stream" &&
        cleanServerMime != "binary/octet-stream"
    ) {
        return cleanServerMime
    }
    val ext = extensionOf(name)
    val custom = when (ext) {
        "md", "markdown", "mdown", "mkd", "mdx" -> "text/markdown"
        "kt", "kts" -> "text/x-kotlin"
        "ts", "tsx" -> "text/typescript"
        "yaml", "yml" -> "text/yaml"
        "toml" -> "text/toml"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js" -> "text/javascript"
        "csv" -> "text/csv"
        "svg" -> "image/svg+xml"
        "heic" -> "image/heic"
        "avif" -> "image/avif"
        "opus" -> "audio/opus"
        "mkv" -> "video/x-matroska"
        else -> null
    }
    if (custom != null) return custom
    val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    if (!fromMap.isNullOrBlank()) return fromMap
    val kind = kindForFile(name)
    return when (kind) {
        FileKind.TEXT, FileKind.MARKDOWN, FileKind.CODE -> "text/plain"
        FileKind.IMAGE -> "image/*"
        FileKind.AUDIO -> "audio/*"
        FileKind.VIDEO -> "video/*"
        FileKind.PDF -> "application/pdf"
        else -> "application/octet-stream"
    }
}

fun syntaxLanguageFor(name: String): String {
    val ext = extensionOf(name)
    return when (ext) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "js", "mjs", "cjs", "jsx" -> "javascript"
        "ts", "tsx" -> "typescript"
        "py", "pyw" -> "python"
        "sh", "bash", "zsh", "fish" -> "shell"
        "json", "json5" -> "json"
        "xml", "html", "htm", "xhtml", "svg", "vue", "svelte" -> "xml"
        "css", "scss", "sass", "less" -> "css"
        "sql" -> "sql"
        "c", "h", "cc", "cpp", "cxx", "hpp", "cs", "m", "mm" -> "c"
        "go" -> "go"
        "rs" -> "rust"
        "rb" -> "ruby"
        "php" -> "php"
        "yaml", "yml", "toml", "ini", "conf", "properties", "env" -> "config"
        "gradle", "groovy" -> "groovy"
        "lua" -> "lua"
        "swift" -> "swift"
        "dart" -> "dart"
        "md", "markdown" -> "markdown"
        else -> "plain"
    }
}

fun isTextLike(kind: FileKind): Boolean =
    kind == FileKind.TEXT || kind == FileKind.MARKDOWN || kind == FileKind.CODE

fun kindLabel(kind: FileKind, name: String): String = when (kind) {
    FileKind.FOLDER -> "文件夹"
    FileKind.TEXT -> "文本文件"
    FileKind.MARKDOWN -> "Markdown 文档"
    FileKind.CODE -> "${extensionOf(name).uppercase(Locale.ROOT).ifBlank { "源码" }} 文件"
    FileKind.IMAGE -> "图片"
    FileKind.AUDIO -> "音频"
    FileKind.VIDEO -> "视频"
    FileKind.PDF -> "PDF 文档"
    FileKind.OFFICE -> "办公文档"
    FileKind.ARCHIVE -> "压缩包"
    FileKind.OTHER -> "文件"
}
