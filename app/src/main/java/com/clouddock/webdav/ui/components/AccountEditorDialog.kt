package com.clouddock.webdav.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import com.clouddock.webdav.data.local.SafPermissions
import com.clouddock.webdav.data.model.AccountFormData
import com.clouddock.webdav.data.model.SyncIntervalOptions
import com.clouddock.webdav.ui.AppUiState
import com.clouddock.webdav.ui.AppViewModel
import com.clouddock.webdav.ui.theme.accountAccent
import kotlinx.coroutines.launch

@Composable
fun AccountEditorDialog(
    state: AppUiState,
    accountId: String?,
    viewModel: AppViewModel
) {
    val existing = state.accounts.firstOrNull { it.id == accountId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = accountAccent(existing?.accentIndex ?: state.accounts.size % 6)

    var name by remember(accountId) { mutableStateOf(existing?.name.orEmpty()) }
    var baseUrl by remember(accountId) { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var username by remember(accountId) { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember(accountId) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var cacheTreeUri by remember(accountId) { mutableStateOf(existing?.cacheTreeUri) }
    var cacheLocationName by remember(accountId) { mutableStateOf(existing?.cacheLocationName) }
    var allowUntrustedTls by remember(accountId) { mutableStateOf(existing?.allowUntrustedTls ?: false) }
    var syncOnOpen by remember(accountId) { mutableStateOf(existing?.syncOnOpen ?: true) }
    var syncInterval by remember(accountId) { mutableStateOf(existing?.syncIntervalMinutes ?: 30) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var intervalExpanded by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun currentForm() = AccountFormData(
        id = existing?.id,
        name = name,
        baseUrl = baseUrl,
        username = username,
        password = password,
        cacheTreeUri = cacheTreeUri,
        cacheLocationName = cacheLocationName,
        allowUntrustedTls = allowUntrustedTls,
        syncOnOpen = syncOnOpen,
        syncIntervalMinutes = syncInterval
    )

    fun closeIfAllowed() {
        if (!saving && !testing) viewModel.closeAccountEditor()
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val granted = SafPermissions.takePersistablePermission(context, uri)
            if (granted) {
                cacheTreeUri = uri.toString()
                cacheLocationName = SafPermissions.displayName(context, uri.toString()) ?: "已选文件夹"
                formError = null
            } else {
                formError = "无法获取该文件夹的读写权限，请选择可写入的文件夹"
            }
        }
    }

    Dialog(
        onDismissRequest = { closeIfAllowed() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !saving && !testing,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .fillMaxHeight(0.94f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(accent.main.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Cloud,
                                contentDescription = null,
                                tint = accent.main,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 13.dp)
                        ) {
                            Text(
                                if (existing == null) "添加 WebDAV" else "编辑挂载",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (existing == null) "连接到你的 WebDAV 空间"
                                else "调整连接、缓存与同步策略",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { closeIfAllowed() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "连接信息",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("名称（可选）") },
                            placeholder = { Text("例如：我的坚果云") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it; testResult = null },
                            label = { Text("WebDAV 地址") },
                            placeholder = { Text("https://dav.example.com/webdav/") },
                            supportingText = { Text("填写完整的 WebDAV 根地址，通常以 / 结尾") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it; testResult = null },
                                label = { Text("用户名") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; testResult = null },
                                label = { Text("密码") },
                                placeholder = {
                                    Text(
                                        if (existing != null && password.isEmpty()) "留空不修改" else "密码"
                                    )
                                },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None
                                else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Rounded.VisibilityOff
                                            else Icons.Rounded.Visibility,
                                            contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        testResult?.let { (success, message) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (success) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.errorContainer
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (success) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (success) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        SectionDivider()
                        Text(
                            "本地缓存文件夹",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(horizontal = 11.dp)
                                    ) {
                                        Text(
                                            cacheLocationName
                                                ?: if (cacheTreeUri == null) "应用私有存储" else "已选文件夹",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            if (cacheTreeUri == null) "未选择时保存在应用专属目录"
                                            else "云端根目录会映射到这个文件夹",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (cacheTreeUri != null) {
                                        TextButton(onClick = {
                                            cacheTreeUri = null
                                            cacheLocationName = null
                                            formError = null
                                        }) {
                                            Text("恢复应用存储")
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            val current = cacheTreeUri?.let(Uri::parse)
                                            folderPicker.launch(current)
                                        }
                                    ) {
                                        Text(if (cacheTreeUri == null) "选择文件夹" else "更换文件夹")
                                    }
                                }
                            }
                        }
                        Text(
                            "打开文件、下载文件和自动同步都会使用此目录。建议选择一个独立文件夹，其中已有的同名文件将参与双向同步。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SectionDivider()
                        Text(
                            "同步",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        SettingSwitchRow(
                            title = "打开挂载时同步",
                            subtitle = "进入此 WebDAV 时自动比对本地与云端文件",
                            checked = syncOnOpen,
                            onCheckedChange = { syncOnOpen = it }
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { intervalExpanded = true }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "定时后台同步",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "系统可能因省电策略延后执行",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box {
                                OutlinedButton(
                                    onClick = { intervalExpanded = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        SyncIntervalOptions.firstOrNull { it.first == syncInterval }?.second
                                            ?: "仅手动同步",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(start = 3.dp)
                                            .size(18.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = intervalExpanded,
                                    onDismissRequest = { intervalExpanded = false }
                                ) {
                                    SyncIntervalOptions.forEach { (minutes, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                syncInterval = minutes
                                                intervalExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                            Text(if (advancedExpanded) "收起高级选项" else "高级选项")
                        }
                        AnimatedVisibility(visible = advancedExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SettingSwitchRow(
                                    title = "允许不受信任的 TLS 证书",
                                    subtitle = "仅对使用自签名证书的私有服务器开启，存在安全风险",
                                    checked = allowUntrustedTls,
                                    onCheckedChange = { allowUntrustedTls = it }
                                )
                                Text(
                                    "HTTP 明文连接默认允许，以便连接局域网 WebDAV；公网环境建议使用 HTTPS。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        formError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (existing != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                TextButton(onClick = { showDeleteConfirm = true }) {
                                    Text(
                                        "移除此挂载",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (testing) return@OutlinedButton
                                testing = true
                                testResult = null
                                formError = null
                                scope.launch {
                                    val result = viewModel.testAccount(currentForm())
                                    testing = false
                                    testResult = result.fold(
                                        onSuccess = { true to it },
                                        onFailure = { false to (it.message ?: "连接失败") }
                                    )
                                }
                            },
                            enabled = !testing && !saving && baseUrl.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (testing) "测试中" else "测试连接")
                        }
                        Button(
                            onClick = {
                                if (saving) return@Button
                                if (baseUrl.isBlank()) {
                                    formError = "请填写 WebDAV 服务器地址"
                                    return@Button
                                }
                                saving = true
                                formError = null
                                scope.launch {
                                    val result = viewModel.saveAccount(currentForm())
                                    saving = false
                                    result.onFailure {
                                        formError = it.message ?: "保存失败"
                                    }
                                }
                            },
                            enabled = !saving && !testing && baseUrl.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (saving) "保存中" else "保存")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text("移除挂载？") },
            text = {
                Text("将删除「${existing.name}」的连接配置和同步记录，不会删除云端文件或本地文件夹中的已有文件。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteAccount(existing.id)
                    }
                ) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
