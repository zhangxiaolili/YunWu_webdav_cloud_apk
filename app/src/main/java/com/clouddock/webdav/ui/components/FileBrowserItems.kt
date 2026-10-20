package com.clouddock.webdav.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clouddock.webdav.data.model.DavEntry
import com.clouddock.webdav.data.model.FileKind
import com.clouddock.webdav.ui.util.formatBytes
import com.clouddock.webdav.ui.util.formatDateTime
import com.clouddock.webdav.ui.util.formatRelativeTime
import com.clouddock.webdav.ui.util.isTextLike

sealed interface FileAction {
    data object Open : FileAction
    data object Download : FileAction
    data object Rename : FileAction
    data object Delete : FileAction
    data object Details : FileAction
    data object OpenExternal : FileAction
    data object Share : FileAction
}

@Composable
fun FileListRow(
    entry: DavEntry,
    onOpen: () -> Unit,
    onAction: (FileAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileTypeIcon(entry.kind)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 13.dp, end = 4.dp)
        ) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitleFor(entry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "文件操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            EntryActionMenu(
                entry = entry,
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onAction = {
                    menuExpanded = false
                    onAction(it)
                }
            )
        }
    }
}

@Composable
fun FileGridCard(
    entry: DavEntry,
    onOpen: () -> Unit,
    onAction: (FileAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(158.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                FileTypeIcon(entry.kind, containerSize = 44.dp, iconSize = 23.dp)
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "文件操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    EntryActionMenu(
                        entry = entry,
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onAction = {
                            menuExpanded = false
                            onAction(it)
                        }
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
            Spacer(Modifier.weight(1f))
            Text(
                subtitleFor(entry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun subtitleFor(entry: DavEntry): String {
    if (entry.isDirectory) {
        return if (entry.lastModified > 0) {
            "文件夹 · ${formatRelativeTime(entry.lastModified)}"
        } else {
            "文件夹"
        }
    }
    val parts = mutableListOf(formatBytes(entry.size))
    if (entry.lastModified > 0) parts += formatDateTime(entry.lastModified).substringBefore(' ')
    return parts.joinToString("  ·  ")
}

@Composable
private fun EntryActionMenu(
    entry: DavEntry,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (FileAction) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val textKind = isTextLike(entry.kind)
        ActionItem(
            label = when {
                entry.isDirectory -> "打开文件夹"
                textKind -> "预览 / 编辑"
                entry.kind == FileKind.IMAGE -> "查看图片"
                entry.kind == FileKind.AUDIO || entry.kind == FileKind.VIDEO -> "播放"
                else -> "打开"
            },
            icon = if (textKind) Icons.Rounded.Edit else Icons.Rounded.OpenInNew,
            onClick = { onAction(FileAction.Open) }
        )
        ActionItem(
            label = if (entry.isDirectory) "下载文件夹" else "下载到缓存目录",
            icon = Icons.Rounded.Download,
            onClick = { onAction(FileAction.Download) }
        )
        if (!entry.isDirectory) {
            ActionItem(
                label = "用其他应用打开",
                icon = Icons.Rounded.OpenInNew,
                onClick = { onAction(FileAction.OpenExternal) }
            )
            ActionItem(
                label = "分享",
                icon = Icons.Rounded.Share,
                onClick = { onAction(FileAction.Share) }
            )
        }
        ActionItem(
            label = "重命名",
            icon = Icons.Rounded.Edit,
            onClick = { onAction(FileAction.Rename) }
        )
        ActionItem(
            label = "详细信息",
            icon = Icons.Rounded.Info,
            onClick = { onAction(FileAction.Details) }
        )
        ActionItem(
            label = "删除",
            icon = Icons.Rounded.DeleteOutline,
            tint = scheme.error,
            onClick = { onAction(FileAction.Delete) }
        )
    }
}

@Composable
private fun ActionItem(
    label: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        },
        onClick = onClick
    )
}
