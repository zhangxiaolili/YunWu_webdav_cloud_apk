package com.clouddock.webdav.ui

import android.content.ActivityNotFoundException
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.DialogProperties
import com.clouddock.webdav.ui.components.AccountEditorDialog
import com.clouddock.webdav.ui.components.AppSidebar
import com.clouddock.webdav.ui.screens.BrowserScreen
import com.clouddock.webdav.ui.screens.ImageViewerScreen
import com.clouddock.webdav.ui.screens.MediaPlayerScreen
import com.clouddock.webdav.ui.screens.SettingsScreen
import com.clouddock.webdav.ui.screens.TextEditorScreen
import com.clouddock.webdav.ui.theme.CloudDockTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun CloudDockApp(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var snackbarJob by remember { mutableStateOf<Job?>(null) }

    fun notify(text: String, isError: Boolean) {
        snackbarJob?.cancel()
        snackbarJob = scope.launch {
            snackbarHostState.showSnackbar(
                message = text,
                withDismissAction = true,
                duration = if (isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> notify(event.text, event.isError)
                is UiEvent.LaunchIntent -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (_: ActivityNotFoundException) {
                        notify("未找到可打开此类型文件的应用", true)
                    } catch (e: Exception) {
                        notify("无法启动外部应用：${e.message ?: "未知错误"}", true)
                    }
                }
            }
        }
    }

    CloudDockTheme(
        themeMode = state.settings.themeMode,
        dynamicColor = state.settings.dynamicColor
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
                when (val screen = state.screen) {
                    AppScreen.Browser -> AdaptiveBrowserShell(
                        state = state,
                        viewModel = viewModel
                    )

                    AppScreen.Settings -> SettingsScreen(state = state, viewModel = viewModel)

                    is AppScreen.Text -> TextEditorScreen(
                        state = state,
                        viewModel = viewModel,
                        onFinish = viewModel::closeViewer
                    )

                    is AppScreen.Media -> MediaPlayerScreen(
                        screen = screen,
                        state = state,
                        onClose = viewModel::closeViewer
                    )

                    is AppScreen.Image -> ImageViewerScreen(
                        screen = screen,
                        onClose = viewModel::closeViewer
                    )
                }

                val snackbarBottomPadding = if (state.screen == AppScreen.Browser) 92.dp else 20.dp
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = snackbarBottomPadding)
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }

                state.operation?.let { operation ->
                    OperationDialog(
                        operation = operation,
                        onCancel = viewModel::cancelActiveOperation
                    )
                }

                state.accountEditor?.let { request ->
                    AccountEditorDialog(
                        state = state,
                        accountId = request.accountId,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveBrowserShell(
    state: AppUiState,
    viewModel: AppViewModel
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val permanent = maxWidth >= 780.dp
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var collapsed by rememberSaveable { mutableStateOf(false) }
        val sidebarWidth by animateDpAsState(
            targetValue = if (collapsed) 84.dp else 288.dp,
            animationSpec = tween(220),
            label = "sidebarWidth"
        )

        if (permanent) {
            Row(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    AppSidebar(
                        accounts = state.accounts,
                        selectedAccountId = state.selectedAccountId,
                        syncStates = state.syncStates,
                        collapsed = collapsed,
                        showCollapseButton = true,
                        onToggleCollapse = { collapsed = !collapsed },
                        onSelectAccount = viewModel::selectAccount,
                        onAddAccount = { viewModel.openAccountEditor(null) },
                        onOpenSettings = viewModel::openSettings
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                BrowserScreen(
                    state = state,
                    viewModel = viewModel,
                    isCompact = false,
                    onOpenDrawer = null
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(304.dp),
                        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        AppSidebar(
                            accounts = state.accounts,
                            selectedAccountId = state.selectedAccountId,
                            syncStates = state.syncStates,
                            collapsed = false,
                            showCollapseButton = false,
                            onToggleCollapse = {},
                            onSelectAccount = { id ->
                                viewModel.selectAccount(id)
                                scope.launch { drawerState.close() }
                            },
                            onAddAccount = {
                                scope.launch {
                                    drawerState.close()
                                    viewModel.openAccountEditor(null)
                                }
                            },
                            onOpenSettings = {
                                viewModel.openSettings()
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            ) {
                BrowserScreen(
                    state = state,
                    viewModel = viewModel,
                    isCompact = true,
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
        }
    }
}

@Composable
private fun OperationDialog(
    operation: TransferUi,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.widthIn(min = 300.dp, max = 420.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(22.dp).height(22.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            operation.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        operation.detail?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (operation.progress != null) {
                    LinearProgressIndicator(
                        progress = operation.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text("取消")
                    }
                }
            }
        }
    }
}
