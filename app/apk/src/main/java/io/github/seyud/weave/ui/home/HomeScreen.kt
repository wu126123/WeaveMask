package io.github.seyud.weave.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.seyud.weave.ui.theme.LocalEnableBlur
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import io.github.seyud.weave.core.R as CoreR
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.utils.TextHolder
import io.github.seyud.weave.ui.module.RebootListPopup
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

/**
 * 主页面屏幕
 * 显示 Magisk 状态、管理器信息和开发者链接
 *
 * @param viewModel 主页 ViewModel
 * @param contentBottomPadding 主页面内容底部留白
 * @param modifier Modifier
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentBottomPadding: Dp,
    onNavigateToInstall: () -> Unit,
    onNavigateToUninstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasStartedLoading by rememberSaveable { mutableStateOf(false) }
    var isManagerCardExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val hazeState = remember { HazeState() }
    val hazeStyle = if (enableBlur) {
        HazeStyle(
            backgroundColor = MiuixTheme.colorScheme.surface,
            tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
        )
    } else {
        HazeStyle.Unspecified
    }

    LaunchedEffect(viewModel.appState) {
        if (viewModel.appState == HomeViewModel.State.LOADING ||
            viewModel.appState == HomeViewModel.State.INVALID) {
            isManagerCardExpanded = false
        }
    }

    // 仅首次进入时触发加载，避免每次返回主页都触发重任务导致转场掉帧
    LaunchedEffect(hasStartedLoading) {
        if (!hasStartedLoading) {
            hasStartedLoading = true
            viewModel.startLoading()
        }
    }

    HomeDialogHost(
        viewModel = viewModel,
        onNavigateToInstall = onNavigateToInstall,
        onNavigateToUninstall = onNavigateToUninstall
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                modifier = if (enableBlur) {
                    Modifier.hazeEffect(hazeState) {
                        style = hazeStyle
                        blurRadius = 30.dp
                        noiseFactor = 0f
                    }
                } else Modifier,
                color = if (enableBlur) Color.Transparent else MiuixTheme.colorScheme.surface,
                title = context.getString(CoreR.string.section_home),
                scrollBehavior = scrollBehavior,
                actions = {
                    // 添加电源重启弹出菜单
                    RebootListPopup(
                        modifier = Modifier.padding(end = 16.dp),
                        alignment = PopupPositionProvider.Align.TopEnd,
                    )
                }
            )
        },
        popupHost = { }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .then(if (enableBlur) Modifier.hazeSource(state = hazeState) else Modifier)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null
        ) {
            // 通知卡片 - 使用 AnimatedVisibility 添加消失动画
            item {
                AnimatedVisibility(
                    visible = viewModel.isNoticeVisible,
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = 300)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 300)
                    )
                ) {
                    NoticeCard(
                        onHide = { viewModel.hideNotice() }
                    )
                }
            }

            // Core 板块
            item {
                MagiskCard(
                    magiskState = viewModel.magiskState,
                    installedVersion = viewModel.magiskInstalledVersion.getText(context.resources).toString(),
                    onPressed = onNavigateToInstall
                )
            }

            // 卸载按钮（红色警告色）
            if (Info.env.isActive) {
                item {
                    UninstallButton(
                        onPressed = { viewModel.onDeletePressed() }
                    )
                }
            }

            // Zygisk & Ramdisk 并排卡片
            item {
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ZygiskCard(
                        isEnabled = Info.isZygiskEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    RamdiskCard(
                        isAvailable = Info.ramdisk,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // App 板块
            item {
                Column {
                    ManagerCard(
                        appState = viewModel.appState,
                        remoteVersion = viewModel.managerRemoteVersion.getText(context.resources).toString(),
                        installedVersion = viewModel.managerInstalledVersion,
                        packageName = viewModel.managerPackageName,
                        progress = viewModel.stateManagerProgress,
                        expanded = isManagerCardExpanded,
                        onCardClick = {
                            isManagerCardExpanded = !isManagerCardExpanded
                        }
                    )

                    AnimatedVisibility(
                        visible = isManagerCardExpanded &&
                            viewModel.appState != HomeViewModel.State.LOADING &&
                            viewModel.appState != HomeViewModel.State.INVALID,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 260)
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 220)
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 220)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 180)
                        )
                    ) {
                        ManagerInstallAction(
                            appState = viewModel.appState,
                            onClick = { viewModel.onManagerPressed() }
                        )
                    }
                }
            }

            // 支持开发板块
            item {
                SupportCard(
                    onLinkPressed = { link ->
                        viewModel.onLinkPressed(link)
                    }
                )
            }

            // 贡献者板块
            item {
                FollowCard(
                    onLinkPressed = { link ->
                        viewModel.onLinkPressed(link)
                    }
                )
            }

            // 底部留白 - 统一使用主页面传入的内容留白，确保最后一个卡片与底栏保持一致间距
            item {
                Spacer(modifier = Modifier.height(contentBottomPadding))
            }
        }
    }
}
