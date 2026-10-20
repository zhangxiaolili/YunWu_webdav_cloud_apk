package com.clouddock.webdav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clouddock.webdav.ui.util.SyntaxColors
import com.clouddock.webdav.ui.util.highlightCode

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class ListBlock(val items: List<MdListItem>) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

private data class MdListItem(val indent: Int, val marker: String, val text: String)

private val listRegex = Regex("""^(\s*)([-*+]|\d+[.)])\s+(.*)$""")
private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
private val tableDividerRegex = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$""")
private val hrRegex = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private const val LINK_TAG = "MD_LINK"

private fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MdBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            index++
            continue
        }

        val fence = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        if (fence != null) {
            val language = trimmed.removePrefix(fence).trim()
            index++
            val code = StringBuilder()
            while (index < lines.size && !lines[index].trim().startsWith(fence)) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[index])
                index++
            }
            if (index < lines.size) index++
            blocks += MdBlock.CodeBlock(language, code.toString())
            continue
        }

        if (trimmed.startsWith(">")) {
            val quote = mutableListOf<String>()
            while (index < lines.size && lines[index].trim().startsWith(">")) {
                quote += lines[index].trim().removePrefix(">").trimStart()
                index++
            }
            blocks += MdBlock.Quote(quote.joinToString("\n"))
            continue
        }

        val headingMatch = headingRegex.matchEntire(trimmed)
        if (headingMatch != null) {
            blocks += MdBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2].trim())
            index++
            continue
        }

        if (hrRegex.matches(line)) {
            blocks += MdBlock.Divider
            index++
            continue
        }

        if (listRegex.matches(line)) {
            val items = mutableListOf<MdListItem>()
            while (index < lines.size) {
                val match = listRegex.matchEntire(lines[index]) ?: break
                val indent = match.groupValues[1].replace("\t", "  ").length / 2
                val rawMarker = match.groupValues[2]
                val marker = if (rawMarker.first().isDigit()) rawMarker else "•"
                items += MdListItem(indent, marker, match.groupValues[3])
                index++
            }
            blocks += MdBlock.ListBlock(items)
            continue
        }

        if (trimmed.contains('|') && index + 1 < lines.size &&
            tableDividerRegex.matches(lines[index + 1])
        ) {
            val headers = splitTableRow(line)
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && lines[index].trim().isNotEmpty() && lines[index].contains('|')) {
                rows += splitTableRow(lines[index])
                index++
            }
            blocks += MdBlock.Table(headers, rows)
            continue
        }

        val paragraph = StringBuilder(line.trimEnd())
        index++
        while (index < lines.size) {
            val next = lines[index]
            val nextTrimmed = next.trim()
            if (nextTrimmed.isEmpty() ||
                nextTrimmed.startsWith("```") ||
                nextTrimmed.startsWith("~~~") ||
                nextTrimmed.startsWith(">") ||
                headingRegex.matches(nextTrimmed) ||
                listRegex.matches(next) ||
                hrRegex.matches(next)
            ) break
            paragraph.append('\n').append(next.trimEnd())
            index++
        }
        blocks += MdBlock.Paragraph(paragraph.toString())
    }
    return blocks
}

private fun splitTableRow(line: String): List<String> {
    var text = line.trim()
    if (text.startsWith("|")) text = text.substring(1)
    if (text.endsWith("|")) text = text.dropLast(1)
    return text.split('|').map { it.trim() }
}

@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val inlineCodeBg = scheme.surfaceVariant.copy(alpha = if (dark) 0.9f else 0.72f)
    val inlineCodeColor = scheme.onSurface
    val syntaxColors = if (dark) {
        SyntaxColors(
            plain = Color(0xFFE3E7F0),
            keyword = Color(0xFFC7A8FF),
            string = Color(0xFF83D8B5),
            comment = Color(0xFF8992A3),
            number = Color(0xFFF0B274),
            function = Color(0xFF93C1FF),
            tag = Color(0xFFFF9FBC),
            lineNumber = Color(0xFF5E6778)
        )
    } else {
        SyntaxColors(
            plain = scheme.onSurface,
            keyword = Color(0xFF7548C7),
            string = Color(0xFF1B8A61),
            comment = Color(0xFF8B93A1),
            number = Color(0xFFB36B1D),
            function = Color(0xFF2E6EB5),
            tag = Color(0xFFC04C6F),
            lineNumber = Color(0xFFA0A7B4)
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 38.sp)
                        2 -> MaterialTheme.typography.headlineMedium.copy(fontSize = 25.sp, lineHeight = 33.sp)
                        3 -> MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp, lineHeight = 29.sp)
                        4 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    }
                    MarkdownInline(
                        text = block.text,
                        style = style.copy(color = scheme.onBackground),
                        inlineCodeBg = inlineCodeBg,
inlineCodeColor = inlineCodeColor,                        linkColor = scheme.primary,
                        modifier = Modifier.padding(top = if (block.level <= 2) 10.dp else 4.dp)
                    )
                }

                is MdBlock.Paragraph -> MarkdownInline(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge.copy(color = scheme.onBackground),
                    inlineCodeBg = inlineCodeBg,
inlineCodeColor = inlineCodeColor,                    linkColor = scheme.primary
                )

                is MdBlock.Quote -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(42.dp)
                            .background(scheme.primary.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                    )
                    MarkdownInline(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = scheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        ),
                        inlineCodeBg = inlineCodeBg,
inlineCodeColor = inlineCodeColor,                        linkColor = scheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    block.items.forEach { item ->
                        Row(
                            Modifier.padding(start = (item.indent * 18).dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = item.marker,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.primary,
                                modifier = Modifier.widthIn(min = 18.dp)
                            )
                            MarkdownInline(
                                text = item.text,
                                style = MaterialTheme.typography.bodyLarge.copy(color = scheme.onBackground),
                                inlineCodeBg = inlineCodeBg,
inlineCodeColor = inlineCodeColor,                                linkColor = scheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                is MdBlock.CodeBlock -> CodeBlockView(block, syntaxColors)

                is MdBlock.Table -> {
                    val columnCount = maxOf(block.headers.size, block.rows.maxOfOrNull { it.size } ?: 0)
                    if (columnCount > 0) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
                                .background(scheme.surface, RoundedCornerShape(12.dp))
                                .padding(vertical = 4.dp)
                        ) {
                            repeat(columnCount) { column ->
                                Column(Modifier.width(180.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    MarkdownInline(
                                        text = block.headers.getOrElse(column) { "" },
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = scheme.onSurface,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        inlineCodeBg = inlineCodeBg,
inlineCodeColor = inlineCodeColor,                                        linkColor = scheme.primary
                                    )
                                    block.rows.forEach { row ->
                                        Spacer(Modifier.height(7.dp))
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(scheme.outlineVariant.copy(alpha = 0.55f))
                                        )
                                        Spacer(Modifier.height(7.dp))
                                        MarkdownInline(
                                            text = row.getOrElse(column) { "" },
                                            style = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurfaceVariant),
                                            inlineCodeBg = inlineCodeBg,
inlineCodeColor = inlineCodeColor,                                            linkColor = scheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                MdBlock.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .height(1.dp)
                        .background(scheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: MdBlock.CodeBlock, syntaxColors: SyntaxColors) {
    val clipboard = LocalClipboardManager.current
    val scheme = MaterialTheme.colorScheme
    val highlighted = remember(block.code, block.language) {
        highlightCode(
            code = block.code,
            language = block.language.ifBlank { "plain" },
            colors = syntaxColors,
            showLineNumbers = false
        )
    }
    Surface(
        color = scheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(block.code)) },
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "复制代码",
                        modifier = Modifier.height(16.dp),
                        tint = scheme.onSurfaceVariant
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            ) {
                Text(
                    text = highlighted,
                    style = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = syntaxColors.plain
                    )
                )
            }
        }
    }
}

@Composable
private fun MarkdownInline(
    text: String,
    style: TextStyle,
    inlineCodeBg: Color,
    inlineCodeColor: Color,
    linkColor: Color,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, style, inlineCodeBg, inlineCodeColor, linkColor) {
        buildMarkdownInline(text, inlineCodeBg, inlineCodeColor, linkColor)
    }
    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(LINK_TAG, offset, offset)
                .firstOrNull()
                ?.item
                ?.let { url -> runCatching { uriHandler.openUri(url) } }
        }
    )
}

private fun buildMarkdownInline(
    text: String,
    inlineCodeBg: Color,
    inlineCodeColor: Color,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {
    appendInlineMarkdown(this, text, inlineCodeBg, inlineCodeColor, linkColor)
}

private fun appendInlineMarkdown(
    builder: AnnotatedString.Builder,
    text: String,
    inlineCodeBg: Color,
    inlineCodeColor: Color,
    linkColor: Color,
    baseStyle: SpanStyle = SpanStyle()
) {
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char == '\\' && index + 1 < text.length -> {
                builder.withStyle(baseStyle) { builder.append(text[index + 1]) }
                index += 2
            }

            char == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    builder.withStyle(
                        baseStyle + SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBg,
                            color = inlineCodeColor
                        )
                    ) {
                        builder.append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    builder.withStyle(baseStyle) { builder.append(char) }
                    index++
                }
            }

            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val marker = text.substring(index, index + 2)
                val end = text.indexOf(marker, index + 2)
                if (end > index + 2) {
                    builder.withStyle(baseStyle + SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInlineMarkdown(builder, text.substring(index + 2, end), inlineCodeBg, inlineCodeColor, linkColor)
                    }
                    index = end + 2
                } else {
                    builder.withStyle(baseStyle) { builder.append(marker) }
                    index += 2
                }
            }

            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", index + 2)
                if (end > index + 2) {
                    builder.withStyle(baseStyle + SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInlineMarkdown(builder, text.substring(index + 2, end), inlineCodeBg, inlineCodeColor, linkColor)
                    }
                    index = end + 2
                } else {
                    builder.withStyle(baseStyle) { builder.append("~~") }
                    index += 2
                }
            }

            text.startsWith("![", index) -> {
                val closeBracket = text.indexOf("](", index + 2)
                val closeParen = if (closeBracket >= 0) text.indexOf(')', closeBracket + 2) else -1
                if (closeBracket > index && closeParen > closeBracket) {
                    val alt = text.substring(index + 2, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen).trim()
                    builder.withStyle(baseStyle) { builder.append("🖼 ") }
                    val start = builder.length
                    builder.withStyle(
                        baseStyle + SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    ) { builder.append(alt.ifBlank { "图片" }) }
                    if (isSafeLink(url)) builder.addStringAnnotation(LINK_TAG, url, start, builder.length)
                    index = closeParen + 1
                } else {
                    builder.withStyle(baseStyle) { builder.append("![") }
                    index += 2
                }
            }

            char == '[' -> {
                val closeBracket = text.indexOf("](", index + 1)
                val closeParen = if (closeBracket >= 0) text.indexOf(')', closeBracket + 2) else -1
                if (closeBracket > index && closeParen > closeBracket) {
                    val label = text.substring(index + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen).trim()
                    val start = builder.length
                    builder.withStyle(
                        baseStyle + SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    ) { builder.append(label) }
                    if (isSafeLink(url)) builder.addStringAnnotation(LINK_TAG, url, start, builder.length)
                    index = closeParen + 1
                } else {
                    builder.withStyle(baseStyle) { builder.append(char) }
                    index++
                }
            }

            char == '*' || char == '_' -> {
                val marker = char.toString()
                val end = text.indexOf(marker, index + 1)
                if (end > index + 1) {
                    builder.withStyle(baseStyle + SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineMarkdown(builder, text.substring(index + 1, end), inlineCodeBg, inlineCodeColor, linkColor)
                    }
                    index = end + 1
                } else {
                    builder.withStyle(baseStyle) { builder.append(char) }
                    index++
                }
            }

            else -> {
                var end = index + 1
                while (end < text.length && text[end] !in charArrayOf('\\', '`', '*', '_', '~', '[', '!')) end++
                builder.withStyle(baseStyle) { builder.append(text.substring(index, end)) }
                index = end
            }
        }
    }
}

private fun isSafeLink(url: String): Boolean {
    val lower = url.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") ||
        lower.startsWith("mailto:") || lower.startsWith("tel:")
}

