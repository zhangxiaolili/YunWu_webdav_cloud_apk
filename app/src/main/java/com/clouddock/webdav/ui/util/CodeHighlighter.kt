package com.clouddock.webdav.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import java.util.Locale

data class SyntaxColors(
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
    val tag: Color,
    val lineNumber: Color
)

private val commonKeywords = setOf(
    "if", "else", "when", "for", "while", "do", "break", "continue", "return", "class",
    "interface", "object", "fun", "val", "var", "new", "this", "super", "throw", "try",
    "catch", "finally", "true", "false", "null", "nil", "None", "True", "False", "import",
    "package", "from", "as", "in", "is", "typeof", "instanceof", "public", "private",
    "protected", "internal", "static", "final", "const", "let", "def", "async", "await",
    "yield", "lambda", "struct", "enum", "trait", "impl", "use", "mod", "pub", "fn",
    "match", "switch", "case", "default", "extends", "implements", "override", "open",
    "data", "sealed", "abstract", "lateinit", "suspend", "inline", "operator", "typealias",
    "void", "int", "long", "double", "float", "boolean", "char", "byte", "short", "string",
    "String", "Int", "Long", "Double", "Float", "Boolean", "Char", "Unit", "Any",
    "namespace", "using", "end", "begin", "then", "elif", "fi", "done", "esac", "function",
    "local", "export", "echo", "require", "module", "exports", "undefined", "of", "not",
    "and", "or", "pass", "raise", "with", "assert", "del", "global", "nonlocal"
)

private val extensionKeywords = mapOf(
    "sql" to setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
        "create", "table", "drop", "alter", "join", "left", "right", "inner", "outer", "on",
        "group", "by", "order", "having", "limit", "offset", "as", "and", "or", "not", "null",
        "primary", "key", "foreign", "references", "index", "union", "case", "when", "then", "else", "end"
    ),
    "xml" to setOf("html", "head", "body", "div", "span", "xml", "version", "encoding"),
    "css" to setOf("important", "media", "supports", "keyframes", "from", "to"),
    "json" to setOf("true", "false", "null"),
    "yaml" to setOf("true", "false", "null", "yes", "no")
)

private val cLikeLanguages = setOf("kotlin", "java", "javascript", "typescript", "c", "go", "rust", "csharp")
private val hashLanguages = setOf("python", "ruby", "shell", "config", "yaml")
private val dashLanguages = setOf("sql", "lua")

fun highlightCode(
    code: String,
    language: String,
    colors: SyntaxColors,
    showLineNumbers: Boolean = true
): AnnotatedString {
    val normalizedLanguage = language.lowercase(Locale.ROOT)
    val keywords = commonKeywords + (extensionKeywords[normalizedLanguage] ?: emptySet())
    val builder = AnnotatedString.Builder()
    val lines = code.split('\n')
    val width = if (showLineNumbers) {
        lines.size.toString().length.coerceAtLeast(2)
    } else 0
    var inBlockComment = false

    lines.forEachIndexed { index, line ->
        if (index > 0) builder.append('\n')
        if (showLineNumbers) {
            builder.withStyle(SpanStyle(color = colors.lineNumber)) {
                builder.append(index.plus(1).toString().padStart(width))
                builder.append("  ")
            }
        }
        inBlockComment = highlightLine(
            builder = builder,
            line = line,
            language = normalizedLanguage,
            colors = colors,
            keywords = keywords,
            startsInBlockComment = inBlockComment
        )
    }
    return builder.toAnnotatedString()
}

private fun highlightLine(
    builder: AnnotatedString.Builder,
    line: String,
    language: String,
    colors: SyntaxColors,
    keywords: Set<String>,
    startsInBlockComment: Boolean
): Boolean {
    var index = 0
    var inBlockComment = startsInBlockComment
    val supportsBlockComments = language !in hashLanguages || language == "css"
    val lineComment = when {
        language in hashLanguages -> "#"
        language in dashLanguages -> "--"
        language in cLikeLanguages || language in setOf("groovy", "swift", "dart", "php") -> "//"
        language == "css" -> null
        language == "xml" -> null
        else -> "//"
    }

    while (index < line.length) {
        if (inBlockComment) {
            val end = line.indexOf("*/", index)
            if (end < 0) {
                builder.withStyle(SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)) {
                    builder.append(line.substring(index))
                }
                return true
            }
            builder.withStyle(SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)) {
                builder.append(line.substring(index, end + 2))
            }
            index = end + 2
            inBlockComment = false
            continue
        }

        if (supportsBlockComments && line.startsWith("/*", index)) {
            val end = line.indexOf("*/", index + 2)
            if (end < 0) {
                builder.withStyle(SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)) {
                    builder.append(line.substring(index))
                }
                return true
            }
            builder.withStyle(SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)) {
                builder.append(line.substring(index, end + 2))
            }
            index = end + 2
            continue
        }

        if (language == "xml" && line.startsWith("<!--", index)) {
            val end = line.indexOf("-->", index + 4)
            val boundary = if (end < 0) line.length else end + 3
            builder.withStyle(SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)) {
                builder.append(line.substring(index, boundary))
            }
            index = boundary
            continue
        }

        if (lineComment != null && line.startsWith(lineComment, index)) {
            builder.withStyle(SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)) {
                builder.append(line.substring(index))
            }
            return false
        }

        val char = line[index]
        if (char == '"' || char == '\'' || char == '`') {
            val start = index
            index++
            while (index < line.length) {
                if (line[index] == '\\' && index + 1 < line.length) {
                    index += 2
                    continue
                }
                if (line[index] == char) {
                    index++
                    break
                }
                index++
            }
            builder.withStyle(SpanStyle(color = colors.string)) {
                builder.append(line.substring(start, index))
            }
            continue
        }

        if (char.isDigit() && (index == 0 || !line[index - 1].isLetter())) {
            val start = index
            while (index < line.length && (line[index].isLetterOrDigit() || line[index] in "._xXaAbBcCdDeEfF+-")) {
                index++
            }
            builder.withStyle(SpanStyle(color = colors.number)) {
                builder.append(line.substring(start, index))
            }
            continue
        }

        if (language == "xml" && char == '<') {
            var end = index + 1
            while (end < line.length && (line[end].isLetterOrDigit() || line[end] in "/?!_-:.")) end++
            if (end > index + 1) {
                builder.withStyle(SpanStyle(color = colors.tag, fontWeight = FontWeight.Medium)) {
                    builder.append(line.substring(index, end))
                }
                index = end
                continue
            }
        }

        if (char.isLetter() || char == '_' || char == '$') {
            val start = index
            while (index < line.length && (line[index].isLetterOrDigit() || line[index] == '_' || line[index] == '$')) {
                index++
            }
            val word = line.substring(start, index)
            val isKeyword = word in keywords
            val isLiteral = word in setOf("true", "false", "null", "None", "True", "False", "undefined")
            val isFunction = !isKeyword && line.getOrNull(index) == '('
            val style = when {
                isKeyword || isLiteral -> SpanStyle(color = colors.keyword, fontWeight = FontWeight.Medium)
                isFunction -> SpanStyle(color = colors.function)
                else -> null
            }
            if (style != null) {
                builder.withStyle(style) { builder.append(word) }
            } else {
                builder.append(word)
            }
            continue
        }

        builder.append(char)
        index++
    }
    return inBlockComment
}
