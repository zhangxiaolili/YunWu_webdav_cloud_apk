package com.clouddock.webdav.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clouddock.webdav.data.model.WebDavAccount
import com.clouddock.webdav.ui.AccountSyncUi
import com.clouddock.webdav.ui.theme.accountAccent
import com.clouddock.webdav.ui.util.deviceLabelFromUrl
import com.clouddock.webdav.ui.util.formatRelativeTime
import com.clouddock.webdav.ui.util.initialsFor

@Composable
fun AppSidebar(
    accounts: List<WebDavAccount>,
    selectedAccountId: String?,
    syncStates: Map<String, AccountSyncUi>,
    collapsed: Boolean,
    showCollapseButton: Boolean,
    onToggleCollapse: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (collapsed) 16.dp else 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(scheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Cloud,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(23.dp)
                )
            }
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Text(
                        "云坞",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        "CLOUDDOCK",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        letterSpacing = 1.4.sp
                    )
                }
            }
            if (showCollapseButton) {
                IconButton(onClick = onToggleCollapse, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (collapsed) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
                        contentDescription = if (collapsed) "展开侧边栏" else "收起侧边栏",
                        tint = scheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!collapsed) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "挂载空间",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    accounts.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = if (collapsed) 12.dp else 12.dp,
                vertical = 3.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(accounts, key = { it.id }) { account ->
                SidebarAccountItem(
                    account = account,
                    selected = account.id == selectedAccountId,
                    syncState = syncStates[account.id],
                    collapsed = collapsed,
                    onClick = { onSelectAccount(account.id) }
                )
            }
            item {
                if (!collapsed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onAddAccount)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(scheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            "添加 WebDAV",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 11.dp)
                        )
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = onAddAccount,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(scheme.surfaceVariant)
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "添加 WebDAV",
                                tint = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        HorizontalDivider(
            Modifier.padding(horizontal = if (collapsed) 16.dp else 20.dp),
            color = scheme.outlineVariant
        )
        if (!collapsed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp)
                )
                Text(
                    "设置与挂载管理",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(
                "本地缓存 · 双向同步",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 14.dp)
            )
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "设置",
                        tint = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarAccountItem(
    account: WebDavAccount,
    selected: Boolean,
    syncState: AccountSyncUi?,
    collapsed: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = accountAccent(account.accentIndex)
    val activeFill = accent.softLight
    val syncing = syncState?.running == true
    val failed = account.lastSyncMessage?.contains("失败") == true
    val statusColor = when {
        syncing -> scheme.primary
        failed -> scheme.error
        account.lastSyncAt > 0 -> Color(0xFF2FA77A)
        else -> scheme.outline
    }

    if (collapsed) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 3.dp)
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) activeFill else Color.Transparent)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.main.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initialsFor(account.name),
                        color = accent.main,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) activeFill else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(accent.main.copy(alpha = if (selected) 0.18f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initialsFor(account.name),
                color = accent.main,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 11.dp, end = 6.dp)
        ) {
            Text(
                account.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    syncing -> syncState?.progress?.phase ?: "正在同步…"
                    failed -> "同步失败"
                    account.lastSyncAt > 0 -> formatRelativeTime(account.lastSyncAt)
                    else -> deviceLabelFromUrl(account.baseUrl)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) scheme.error else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = scheme.primary
            )
        } else {
            Icon(
                imageVector = when {
                    failed -> Icons.Rounded.CloudOff
                    account.lastSyncAt > 0 -> Icons.Rounded.CloudDone
                    else -> Icons.Rounded.Cloud
                },
                contentDescription = null,
                tint = statusColor.copy(alpha = 0.8f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
