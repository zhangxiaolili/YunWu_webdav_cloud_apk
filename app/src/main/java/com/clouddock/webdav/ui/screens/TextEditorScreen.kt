package com.clouddock.webdav.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clouddock.webdav.data.model.FileKind
import com.clouddock.webdav.ui.AppUiState
import com.clouddock.webdav.ui.AppViewModel
import com.clouddock.webdav.ui.components.MarkdownView
import com.clouddock.webdav.ui.util.SyntaxColors
import com.clouddock.webdav.ui.util.formatBytes
import com.clouddock.webdav.ui.util.highlightCode
import com.clouddock.webdav.ui.util.syntaxLanguageFor

private enum class TextMode { READ, EDIT }
private const val PREVIEW_CHAR_LIMIT = 400_000

@Composable
fun TextEditorScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onFinish: () -> Unit
) {
    val doc = state.editorDocument
    val kind = doc?.let { com.clouddock.webdav.ui.util.kindForFile(it.name) } ?: FileKind.TEXT
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val language = doc?.let { syntaxLanguageFor(it.name) } ?: "plain"
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var mode by rememberSaveable(doc?.path, doc?.accountId) {
        mutableStateOf(TextMode.READ)
    }
    var textValue by remember(doc?.path, doc?.accountId) {
        mutableStateOf(TextFieldValue(doc?.draft.orEmpty()))
    }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val dirty = doc != null && textValue.text != doc.text
    val previewText = remember(textValue.text) {
        if (textValue.text.length > PREVIEW_CHAR_LIMIT) {
            textValue.text.take(PREVIEW_CHAR_LIMIT)
        } else {
            textValue.text
        }
    }
    val previewTruncated = textValue.text.length > PREVIEW_CHAR_LIMIT

    fun requestClose() {
        when {
            doc?.isSaving == true -> viewModel.postMessage("正在保存，请稍候")
            dirty -> showDiscardDialog = true
            else -> onFinish()
        }
    }

    BackHandler { requestClose() }

    LaunchedEffect(mode) {
        if (mode == TextMode.EDIT && doc != null && !doc.truncated) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 12.dp, top = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { requestClose() }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 5.dp)
                        ) {
                            Text(
                                doc?.name ?: "文档",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                buildString {
                                    append(doc?.charsetName ?: "UTF-8")
                                    doc?.fileSize?.let { append(" · ${formatBytes(it)}") }
                                    if (dirty) append(" · 未保存")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (dirty) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        TextButton(
                            onClick = {
                                if (doc == null || !dirty || doc.isSaving || doc.truncated) return@TextButton
                                viewModel.saveEditor(textValue.text) { result ->
                                    result.fold(
                                        onSuccess = { message -> viewModel.postMessage(message) },
                                        onFailure = { error ->
                                            viewModel.postMessage(error.message ?: "保存失败", true)
                                        }
                                    )
                                }
                            },
                            enabled = doc != null && dirty && !doc.isSaving && !doc.truncated
                        ) {
                            if (doc?.isSaving == true) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(17.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(if (doc?.isSaving == true) "保存中" else "保存")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            if (doc == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "没有打开的文档",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        TextButton(onClick = onFinish) { Text("返回") }
                    }
                }
                return@Column
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val readLabel = when {
                    kind == FileKind.MARKDOWN -> "预览"
                    kind == FileKind.CODE -> "代码"
                    else -> "阅读"
                }
                TextMode.entries.forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = {
                            mode = item
                            if (item == TextMode.READ) {
                                focusManager.clearFocus()
                            }
                        },
                        label = {
                            Text(
                                if (item == TextMode.READ) readLabel else "编辑",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${textValue.text.count { it == '\n' } + 1} 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (doc.truncated || previewTruncated) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (doc.truncated) {
                            "文件超过 3 MB，当前为只读预览，编辑功能已禁用。"
                        } else {
                            "文档较大，仅渲染前 ${PREVIEW_CHAR_LIMIT / 1000}K 个字符；编辑功能不受影响。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (mode == TextMode.EDIT && !doc.truncated) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                        )
                    ) {
                        BasicTextField(
                            value = textValue,
                            onValueChange = { updated ->
                                textValue = updated
                                viewModel.updateEditorDraft(updated.text)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None
                            )
                        )
                    }
                } else {
                    PreviewContent(
                        kind = kind,
                        text = previewText,
                        language = language,
                        dark = dark,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        doc.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${doc.charsetName} · ${textValue.text.length} 字符",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("当前文档有尚未保存的改动，返回后将丢失这些修改。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onFinish()
                    }
                ) {
                    Text("放弃修改", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        mode = TextMode.EDIT
                    }
                ) { Text("继续编辑") }
            }
        )
    }
}

@Composable
private fun PreviewContent(
    kind: FileKind,
    text: String,
    language: String,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    when (kind) {
        FileKind.MARKDOWN -> {
            val verticalScroll = rememberScrollState()
            Box(
                modifier = modifier
                    .verticalScroll(verticalScroll)
                    .padding(horizontal = 16.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 30.dp)
                ) {
                    MarkdownView(markdown = text)
                }
            }
        }

        FileKind.CODE -> {
            val colors = syntaxColors(dark)
            val highlighted = remember(text, language, dark) {
                highlightCode(text, language, colors, showLineNumbers = true)
            }
            val verticalScroll = rememberScrollState()
            val horizontalScroll = rememberScrollState()
            Surface(
                modifier = modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                )
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScroll)
                        .padding(vertical = 14.dp)
                ) {
                    Box(
                        Modifier
                            .horizontalScroll(horizontalScroll)
                            .padding(horizontal = 14.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = highlighted,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 21.sp,
                                    color = colors.plain
                                )
                            )
                        }
                    }
                }
            }
        }

        else -> {
            val verticalScroll = rememberScrollState()
            Surface(
                modifier = modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                )
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 23.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScroll)
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

private fun syntaxColors(dark: Boolean): SyntaxColors {
    return if (dark) {
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
            plain = Color(0xFF26303E),
            keyword = Color(0xFF7548C7),
            string = Color(0xFF1B8A61),
            comment = Color(0xFF8B93A1),
            number = Color(0xFFB36B1D),
            function = Color(0xFF2E6EB5),
            tag = Color(0xFFC04C6F),
            lineNumber = Color(0xFFA0A7B4)
        )
    }
}
