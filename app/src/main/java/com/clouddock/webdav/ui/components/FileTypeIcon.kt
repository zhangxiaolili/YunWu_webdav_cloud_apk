package com.clouddock.webdav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clouddock.webdav.data.model.FileKind

data class FileKindVisual(
    val icon: ImageVector,
    val tint: Color
)

fun fileKindVisual(kind: FileKind): FileKindVisual = when (kind) {
    FileKind.FOLDER -> FileKindVisual(Icons.Rounded.Folder, Color(0xFFE3A21A))
    FileKind.TEXT -> FileKindVisual(Icons.Rounded.Description, Color(0xFF4D7CF3))
    FileKind.MARKDOWN -> FileKindVisual(Icons.Rounded.Article, Color(0xFF5B6DE8))
    FileKind.CODE -> FileKindVisual(Icons.Rounded.Code, Color(0xFF8C62D9))
    FileKind.IMAGE -> FileKindVisual(Icons.Rounded.Image, Color(0xFF189B76))
    FileKind.AUDIO -> FileKindVisual(Icons.Rounded.Audiotrack, Color(0xFFD64D86))
    FileKind.VIDEO -> FileKindVisual(Icons.Rounded.Movie, Color(0xFFE15A54))
    FileKind.PDF -> FileKindVisual(Icons.Rounded.PictureAsPdf, Color(0xFFD04444))
    FileKind.OFFICE -> FileKindVisual(Icons.Rounded.TableChart, Color(0xFF1789B7))
    FileKind.ARCHIVE -> FileKindVisual(Icons.Rounded.Archive, Color(0xFFD8842B))
    FileKind.OTHER -> FileKindVisual(Icons.Rounded.InsertDriveFile, Color(0xFF6B7486))
}

@Composable
fun FileTypeIcon(
    kind: FileKind,
    modifier: Modifier = Modifier,
    containerSize: Dp = 44.dp,
    iconSize: Dp = 23.dp,
    shape: RoundedCornerShape = RoundedCornerShape(13.dp)
) {
    val visual = fileKindVisual(kind)
    val dark = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier = modifier
            .size(containerSize)
            .background(
                color = visual.tint.copy(alpha = if (dark) 0.20f else 0.12f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = visual.tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
