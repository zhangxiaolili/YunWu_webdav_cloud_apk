package com.clouddock.webdav.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clouddock.webdav.data.model.DavEntry
import com.clouddock.webdav.data.model.SortKey
import com.clouddock.webdav.data.model.SyncIntervalOptions
import com.clouddock.webdav.data.model.WebDavAccount
import com.clouddock.webdav.ui.AccountSyncUi
import com.clouddock.webdav.ui.AppUiState
import com.clouddock.webdav.ui.AppViewModel
import com.clouddock.webdav.ui.components.CreateAction
import com.clouddock.webdav.ui.components.CreateActionSheet
import com.clouddock.webdav.ui.components.DeleteEntryDialog
import com.clouddock.webdav.ui.components.EntryDetailsSheet
import com.clouddock.webdav.ui.components.FileAction
import com.clouddock.webdav.ui.components.FileGridCard
import com.clouddock.webdav.ui.components.FileListRow
import com.clouddock.webdav.ui.components.NameInputDialog
import com.clouddock.webdav.ui.theme.accountAccent
import com.clouddock.webdav.ui.util.deviceLabelFromUrl
import com.clouddock.webdav.ui.util.formatRelativeTime
import com.clouddock.webdav.ui.util.formatDateTime
import com.clouddock.webdav.ui.util.initialsFor
import java.text.Collator
import java.util.Locale

private enum class NewEntryKind { FOLDER, FILE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    isCompact: Boolean,
    onOpenDrawer: (() -> Unit)?
) {
    val account = state.selectedAccount
    var searchActive by rememberSaveable(account?.id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(account?.id) { mutableStateOf("") }
    var showCreateSheet by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var newEntryKind by remember { mutableStateOf<NewEntryKind?>(null) }
    var renameTarget by remember { mutableStateOf<DavEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<DavEntry?>(null) }
    var detailsTarget by remember { mutableStateOf<DavEntry?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()

    LaunchedEffect(account?.id, state.currentPath) {
        searchActive = false
        searchQuery = ""
        keyboard?.hide()
        gridState.scrollToItem(0)
    }
    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    BackHandler(enabled = searchActive || state.currentPath.isNotEmpty()) {
        if (searchActive) {
            searchActive = false
            searchQuery = ""
            keyboard?.hide()
        } else {
            viewModel.navigateUp()
        }
    }

    val displayedEntries = remember(
        state.entries,
        searchQuery,
        state.settings.sortKey,
        state.settings.sortAscending
    ) {
        filterAndSort(
            entries = state.entries,
            query = searchQuery,
            sortKey = state.settings.sortKey,
            ascending = state.settings.sortAscending
        )
    }

    val uploadFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadFiles(uris)
    }
    val uploadFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.uploadFolder(uri)
    }

    fun handleAction(entry: DavEntry, action: FileAction) {
        when (action) {
            FileAction.Open -> viewModel.openEntry(entry)
            FileAction.Download -> viewModel.downloadEntry(entry)
            FileAction.Rename -> renameTarget = entry
            FileAction.Delete -> deleteTarget = entry
            FileAction.Details -> detailsTarget = entry
            FileAction.OpenExternal -> viewModel.openWithExternalApp(entry)
            FileAction.Share -> viewModel.shareEntry(entry)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BrowserTopBar(
                account = account,
                path = state.currentPath,
                isCompact = isCompact,
                onOpenDrawer = onOpenDrawer,
                searchActive = searchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    searchActive = !searchActive
                    if (!searchActive) {
                        searchQuery = ""
                        keyboard?.hide()
                    }
                },
                onSearchClose = {
                    searchActive = false
                    searchQuery = ""
                    keyboard?.hide()
                },
                focusRequester = focusRequester,
                loading = state.entriesLoading,
                syncRunning = account?.let { state.syncStates[it.id]?.running == true } ?: false,
                moreExpanded = moreExpanded,
                onMoreExpandedChange = { moreExpanded = it },
                onNavigate = viewModel::openFolder,
                onRefresh = viewModel::refreshEntries,
                onSync = { account?.let { viewModel.syncAccount(it.id) } },
                onAddAccount = { viewModel.openAccountEditor(null) },
                onAccountSettings = { account?.let { viewModel.openAccountEditor(it.id) } },
                onOpenSettings = viewModel::openSettings
            )
        },
        floatingActionButton = {
            if (account != null) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateSheet = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("添加") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (account == null) {
                NoAccountsContent(
                    compact = isCompact,
                    onOpenDrawer = onOpenDrawer,
                    onAddAccount = { viewModel.openAccountEditor(null) },
                    onOpenSettings = viewModel::openSettings
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = if (state.settings.gridMode) {
                        GridCells.Adaptive(minSize = 158.dp)
                    } else {
                        GridCells.Fixed(1)
                    },
                    modifier = Modifier
                        .widthIn(max = 1240.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 104.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.currentPath.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AccountOverview(
                                account = account,
                                syncState = state.syncStates[account.id],
                                onSync = { viewModel.syncAccount(account.id) },
                                onOpenSettings = { viewModel.openAccountEditor(account.id) }
                            )
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FileListControls(
                            count = displayedEntries.size,
                            searching = searchQuery.isNotBlank(),
                            sortExpanded = sortExpanded,
                            onSortExpandedChange = { sortExpanded = it },
                            sortKey = state.settings.sortKey,
                            ascending = state.settings.sortAscending,
                            gridMode = state.settings.gridMode,
                            onSortKey = viewModel::setSortKey,
                            onToggleDirection = viewModel::toggleSortDirection,
                            onToggleGrid = { viewModel.setGridMode(!state.settings.gridMode) }
                        )
                    }

                    state.entriesError?.takeIf { state.entries.isNotEmpty() }?.let { error ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            InlineErrorBanner(
                                message = error,
                                onRetry = viewModel::refreshEntries
                            )
                        }
                    }

                    when {
                        state.entriesLoading && state.entries.isEmpty() -> {
                            repeat(6) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SkeletonRow()
                                }
                            }
                        }

                        state.entriesError != null && state.entries.isEmpty() -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                BrowserErrorState(
                                    message = state.entriesError,
                                    onRetry = viewModel::refreshEntries,
                                    onEditAccount = { viewModel.openAccountEditor(account.id) }
                                )
                            }
                        }

                        displayedEntries.isEmpty() -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyFolderState(
                                    searching = searchQuery.isNotBlank(),
                                    onClearSearch = {
                                        searchQuery = ""
                                        searchActive = false
                                    },
                                    onAdd = { showCreateSheet = true }
                                )
                            }
                        }

                        else -> {
                            items(displayedEntries, key = { it.path }) { entry ->
                                if (state.settings.gridMode) {
                                    FileGridCard(
                                        entry = entry,
                                        onOpen = { viewModel.openEntry(entry) },
                                        onAction = { handleAction(entry, it) }
                                    )
                                } else {
                                    FileListRow(
                                        entry = entry,
                                        onOpen = { viewModel.openEntry(entry) },
                                        onAction = { handleAction(entry, it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateActionSheet(
            onDismiss = { showCreateSheet = false },
            onAction = { action ->
                showCreateSheet = false
                when (action) {
                    CreateAction.NEW_FOLDER -> newEntryKind = NewEntryKind.FOLDER
                    CreateAction.NEW_FILE -> newEntryKind = NewEntryKind.FILE
                    CreateAction.UPLOAD_FILE -> uploadFilesLauncher.launch(arrayOf("*/*"))
                    CreateAction.UPLOAD_FOLDER -> uploadFolderLauncher.launch(null)
                }
            }
        )
    }

    newEntryKind?.let { kind ->
        NameInputDialog(
            title = if (kind == NewEntryKind.FOLDER) "新建文件夹" else "新建文件",
            label = if (kind == NewEntryKind.FOLDER) "文件夹名称" else "文件名称（含扩展名）",
            initialValue = "",
            confirmText = "创建",
            onDismiss = { newEntryKind = null },
            onConfirm = { name ->
                val target = newEntryKind
                newEntryKind = null
                if (target == NewEntryKind.FOLDER) viewModel.createFolder(name)
                else viewModel.createFile(name)
            }
        )
    }

    renameTarget?.let { entry ->
        NameInputDialog(
            title = "重命名",
            label = "新名称",
            initialValue = entry.name,
            confirmText = "重命名",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                val target = renameTarget
                renameTarget = null
                if (target != null) viewModel.renameEntry(target, name)
            }
        )
    }

    deleteTarget?.let { entry ->
        DeleteEntryDialog(
            entry = entry,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                viewModel.deleteEntry(entry)
            }
        )
    }

    detailsTarget?.let { entry ->
        EntryDetailsSheet(
            entry = entry,
            onDismiss = { detailsTarget = null }
        )
    }
}

@Composable
private fun BrowserTopBar(
    account: WebDavAccount?,
    path: String,
    isCompact: Boolean,
    onOpenDrawer: (() -> Unit)?,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchClose: () -> Unit,
    focusRequester: FocusRequester,
    loading: Boolean,
    syncRunning: Boolean,
    moreExpanded: Boolean,
    onMoreExpandedChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit,
    onSync: () -> Unit,
    onAddAccount: () -> Unit,
    onAccountSettings: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (isCompact) 6.dp else 20.dp, end = 10.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCompact && onOpenDrawer != null) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Rounded.Menu, contentDescription = "打开挂载列表")
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = if (isCompact) 2.dp else 0.dp)
                ) {
                    Text(
                        account?.name ?: "云坞",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        account?.let { deviceLabelFromUrl(it.baseUrl) }
                            ?: "WebDAV 文件系统 · 本地缓存与双向同步",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (account != null) {
                    IconButton(onClick = onToggleSearch) {
                        Icon(
                            if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (searchActive) "关闭搜索" else "搜索",
                            tint = scheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onSync, enabled = !syncRunning) {
                        if (syncRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Sync,
                                contentDescription = "立即同步",
                                tint = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { onMoreExpandedChange(true) }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "更多选项",
                            tint = scheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { onMoreExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("刷新") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                            onClick = {
                                onMoreExpandedChange(false)
                                onRefresh()
                            },
                            enabled = account != null
                        )
                        DropdownMenuItem(
                            text = { Text("立即同步") },
                            leadingIcon = { Icon(Icons.Rounded.Sync, contentDescription = null) },
                            onClick = {
                                onMoreExpandedChange(false)
                                onSync()
                            },
                            enabled = account != null
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("添加挂载") },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            onClick = {
                                onMoreExpandedChange(false)
                                onAddAccount()
                            }
                        )
                        if (account != null) {
                            DropdownMenuItem(
                                text = { Text("账户设置") },
                                leadingIcon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null) },
                                onClick = {
                                    onMoreExpandedChange(false)
                                    onAccountSettings()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("应用设置") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = {
                                onMoreExpandedChange(false)
                                onOpenSettings()
                            }
                        )
                    }
                }
            }

            if (account != null) {
                Breadcrumb(path = path, accountName = account.name, onNavigate = onNavigate)
            }

            AnimatedVisibility(visible = searchActive && account != null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("在当前文件夹中搜索") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchClose() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .focusRequester(focusRequester)
                )
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = scheme.primary,
                    trackColor = Color.Transparent
                )
            } else {
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun Breadcrumb(
    path: String,
    accountName: String,
    onNavigate: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val segments = path.split('/').filter { it.isNotEmpty() }
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            accountName,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(15.dp)
        )
        BreadcrumbSegment(
            label = "全部文件",
            last = segments.isEmpty(),
            onClick = { onNavigate("") }
        )
        var accumulated = ""
        segments.forEachIndexed { index, segment ->
            accumulated = if (accumulated.isEmpty()) segment else "$accumulated/$segment"
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(15.dp)
            )
            BreadcrumbSegment(
                label = segment,
                last = index == segments.lastIndex,
                onClick = { onNavigate(accumulated) }
            )
        }
    }
}

@Composable
private fun BreadcrumbSegment(label: String, last: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
        color = if (last) MaterialTheme.colorScheme.onBackground
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = !last, onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp)
    )
}

@Composable
private fun AccountOverview(
    account: WebDavAccount,
    syncState: AccountSyncUi?,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = accountAccent(account.accentIndex)
    val syncing = syncState?.running == true
    val failed = !syncing && account.lastSyncMessage?.contains("失败") == true
    val cacheLabel = account.cacheTreeUri?.let {
        account.cacheLocationName ?: "已选文件夹"
    } ?: "应用私有存储"
    val lastSyncText = when {
        syncing -> syncState?.progress?.phase ?: "正在同步…"
        account.lastSyncAt > 0 -> formatRelativeTime(account.lastSyncAt)
        else -> "尚未同步"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = accent.softLight,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            accent.main.copy(alpha = 0.18f)
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.main),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initialsFor(account.name),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 13.dp)
                ) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        deviceLabelFromUrl(account.baseUrl),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(
                    text = when {
                        syncing -> "同步中"
                        failed -> "同步异常"
                        else -> "已挂载"
                    },
                    color = when {
                        syncing -> scheme.primary
                        failed -> scheme.error
                        else -> scheme.secondary
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                InfoBlock(
                    icon = Icons.Rounded.Folder,
                    label = "本地缓存",
                    value = cacheLabel,
                    modifier = Modifier.weight(1f)
                )
                InfoBlock(
                    icon = Icons.Rounded.Sync,
                    label = if (syncing) "同步进度" else "最近同步",
                    value = lastSyncText,
                    modifier = Modifier.weight(1f)
                )
            }
            if (syncing) {
                Spacer(Modifier.height(12.dp))
                val progress = syncState?.progress
                val byteFraction = progress?.bytesProgress
                if (byteFraction != null) {
                    LinearProgressIndicator(
                        progress = byteFraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp),
                        trackColor = accent.main.copy(alpha = 0.15f),
                        color = accent.main
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp),
                        trackColor = accent.main.copy(alpha = 0.15f),
                        color = accent.main
                    )
                }
                progress?.currentPath?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            } else {
                account.lastSyncMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failed) scheme.error else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = onSync,
                    enabled = !syncing,
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                        disabledContainerColor = scheme.surfaceVariant,
                        disabledContentColor = scheme.onSurfaceVariant
                    )
                ) {
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = scheme.primary
                        )
                        Spacer(Modifier.width(7.dp))
                    } else {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(if (syncing) "同步中" else "立即同步")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onOpenSettings) {
                    Text("账户设置")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun InfoBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun FileListControls(
    count: Int,
    searching: Boolean,
    sortExpanded: Boolean,
    onSortExpandedChange: (Boolean) -> Unit,
    sortKey: SortKey,
    ascending: Boolean,
    gridMode: Boolean,
    onSortKey: (SortKey) -> Unit,
    onToggleDirection: () -> Unit,
    onToggleGrid: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (searching) "搜索结果" else "文件",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "$count 个项目",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
        Box {
            TextButton(
                onClick = { onSortExpandedChange(true) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.Rounded.SwapVert,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(sortKey.label, style = MaterialTheme.typography.labelMedium)
                Icon(
                    if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = if (ascending) "升序" else "降序",
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(15.dp)
                )
            }
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { onSortExpandedChange(false) }
            ) {
                SortKey.entries.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(key.label) },
                        leadingIcon = {
                            if (key == sortKey) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Spacer(Modifier.size(18.dp))
                            }
                        },
                        onClick = {
                            onSortKey(key)
                            onSortExpandedChange(false)
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (ascending) "切换为降序" else "切换为升序") },
                    leadingIcon = {
                        Icon(
                            if (ascending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        onToggleDirection()
                        onSortExpandedChange(false)
                    }
                )
            }
        }
        IconButton(onClick = onToggleGrid) {
            Icon(
                if (gridMode) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                contentDescription = if (gridMode) "切换为列表" else "切换为网格",
                tint = scheme.onSurfaceVariant
            )
        }
    }
}

private val SortKey.label: String
    get() = when (this) {
        SortKey.NAME -> "名称"
        SortKey.DATE -> "时间"
        SortKey.SIZE -> "大小"
        SortKey.TYPE -> "类型"
    }

@Composable
private fun InlineErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 9.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun BrowserErrorState(
    message: String,
    onRetry: () -> Unit,
    onEditAccount: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(35.dp)
            )
        }
        Text(
            "无法读取当前文件夹",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 28.dp, vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onEditAccount,
                shape = RoundedCornerShape(13.dp)
            ) {
                Text("账户设置")
            }
            Button(onClick = onRetry, shape = RoundedCornerShape(13.dp)) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun EmptyFolderState(
    searching: Boolean,
    onClearSearch: () -> Unit,
    onAdd: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (searching) Icons.Rounded.SearchOff else Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            if (searching) "没有匹配的文件" else "这里还没有文件",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            if (searching) "尝试更换关键词，或切换到其他文件夹" else "新建文件夹或上传文件，开始使用这个空间",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp)
        )
        Spacer(Modifier.height(14.dp))
        if (searching) {
            OutlinedButton(onClick = onClearSearch, shape = RoundedCornerShape(13.dp)) {
                Text("清除搜索")
            }
        } else {
            FilledTonalButton(onClick = onAdd, shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加内容")
            }
        }
    }
}

@Composable
private fun SkeletonRow() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
            Box(
                Modifier
                    .fillMaxWidth(0.32f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha * 0.7f))
            )
        }
    }
}

@Composable
private fun NoAccountsContent(
    compact: Boolean,
    onOpenDrawer: (() -> Unit)?,
    onAddAccount: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = scheme.primaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.size(124.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Cloud,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }
            Text(
                "把 WebDAV 放进你的口袋",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 26.dp)
            )
            Text(
                "连接坚果云、Nextcloud、ownCloud 或自建 WebDAV。像本地文件夹一样浏览、编辑、缓存与同步。",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureChip("多账户挂载")
                FeatureChip("SAF 本地缓存")
                FeatureChip("双向同步")
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAddAccount,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("添加 WebDAV 挂载")
            }
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(top = 3.dp)
            ) {
                Text("查看设置")
            }
            if (compact && onOpenDrawer != null) {
                Text(
                    "提示：从左上角菜单可以随时切换挂载",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

private fun filterAndSort(
    entries: List<DavEntry>,
    query: String,
    sortKey: SortKey,
    ascending: Boolean
): List<DavEntry> {
    val filtered = if (query.isBlank()) {
        entries
    } else {
        entries.filter { it.name.contains(query, ignoreCase = true) }
    }
    val collator = Collator.getInstance(Locale.CHINA)
    val comparator = Comparator<DavEntry> { first, second ->
        if (first.isDirectory != second.isDirectory) {
            return@Comparator if (first.isDirectory) -1 else 1
        }
        val compared = when (sortKey) {
            SortKey.NAME -> {
                val value = collator.compare(first.name, second.name)
                if (value != 0) value else first.name.compareTo(second.name, ignoreCase = true)
            }
            SortKey.DATE -> first.lastModified.compareTo(second.lastModified)
            SortKey.SIZE -> {
                val value = first.size.compareTo(second.size)
                if (value != 0) value else collator.compare(first.name, second.name)
            }
            SortKey.TYPE -> {
                val value = first.kind.ordinal.compareTo(second.kind.ordinal)
                if (value != 0) value else collator.compare(first.name, second.name)
            }
        }
        if (ascending) compared else -compared
    }
    return filtered.sortedWith(comparator)
}
