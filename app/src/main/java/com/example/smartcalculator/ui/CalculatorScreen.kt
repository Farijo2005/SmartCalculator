package com.example.smartcalculator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartcalculator.calc.CalcMode
import com.example.smartcalculator.calc.CalculatorUiState
import com.example.smartcalculator.calc.CalculatorViewModel
import com.example.smartcalculator.calc.HistoryItem
import com.example.smartcalculator.calc.displayName
import com.example.smartcalculator.ui.components.DrawerBackdrop
import com.example.smartcalculator.ui.components.HeaderBar
import com.example.smartcalculator.ui.components.HistoryDrawer
import com.example.smartcalculator.ui.components.ModeDrawer
import com.example.smartcalculator.ui.components.SettingsDrawer
import com.example.smartcalculator.ui.components.isDarkTheme
import com.example.smartcalculator.ui.modules.ModuleIntent
import com.example.smartcalculator.ui.modules.ModuleRouter
import com.example.smartcalculator.ui.theme.Background200
import com.example.smartcalculator.ui.theme.BackgroundAppleDark
import com.example.smartcalculator.ui.theme.ThemeMode

/**
 * 主屏幕（阶段二）：模块化框架。
 *
 * - HeaderBar（三个按钮）
 * - 模块内容区（由 [ModuleRouter] 根据 [CalcMode] 路由到独立模块文件）
 * - 三个抽屉：菜单 / 历史 / 设置
 *
 * 模块化要点：
 * - 每个模式有自己的 .kt 文件（如 [com.example.smartcalculator.ui.modules.StandardModule]）
 * - 共享文件（本文件、[ModuleRouter]）只在新增模块时改一行
 * - 横竖屏布局统一由 [com.example.smartcalculator.ui.modules.ModuleLayout] 处理
 */
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onMenuClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAbout: () -> Unit,
    isMenuOpen: Boolean,
    isHistoryOpen: Boolean,
    isSettingsOpen: Boolean,
    onCloseDrawers: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val screenBg: Color = if (isDarkTheme()) BackgroundAppleDark else Background200
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val isLandscape = maxWidth > maxHeight
            val isTablet = maxWidth > 720.dp
            val wide = isLandscape || isTablet

            val cb = remember(onCloseDrawers) {
                SharedCallbacks(
                    onPickMode = { mode: CalcMode ->
                        viewModel.setMode(mode)
                        onCloseDrawers()
                    },
                    onPickHistory = { _: HistoryItem -> onCloseDrawers() },
                    onClearHistory = viewModel::clearHistory,
                    onCloseSettings = onCloseDrawers,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSetThemeColor = viewModel::setThemeColor,
                    onModuleIntent = { /* TODO: ViewModel 路由到对应模块的 reducer */ },
                )
            }

            CalculatorContent(
                state = state,
                isLandscape = wide,
                cb = cb,
                onMenuClick = onMenuClick,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                onAbout = onAbout,
                isMenuOpen = isMenuOpen,
                isHistoryOpen = isHistoryOpen,
                isSettingsOpen = isSettingsOpen,
                onCloseDrawers = onCloseDrawers,
            )
        }
    }
}

private data class SharedCallbacks(
    val onPickMode: (CalcMode) -> Unit,
    val onPickHistory: (HistoryItem) -> Unit,
    val onClearHistory: () -> Unit,
    val onCloseSettings: () -> Unit,
    val onSetThemeMode: (ThemeMode) -> Unit,
    val onSetThemeColor: (Color) -> Unit,
    val onModuleIntent: (ModuleIntent) -> Unit,
)

@Composable
private fun CalculatorContent(
    state: CalculatorUiState,
    isLandscape: Boolean,
    cb: SharedCallbacks,
    onMenuClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAbout: () -> Unit,
    isMenuOpen: Boolean,
    isHistoryOpen: Boolean,
    isSettingsOpen: Boolean,
    onCloseDrawers: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(
                title = state.mode.displayName(),
                onMenuClick = onMenuClick,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 模块内容区 —— 路由到当前模式的独立模块文件
            ModuleRouter(
                mode = state.mode,
                moduleStates = state.moduleStates,
                onIntent = cb.onModuleIntent,
                isLandscape = isLandscape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        DrawersLayer(
            state = state,
            cb = cb,
            onAbout = onAbout,
            isMenuOpen = isMenuOpen,
            isHistoryOpen = isHistoryOpen,
            isSettingsOpen = isSettingsOpen,
            onCloseDrawers = onCloseDrawers,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.DrawersLayer(
    state: CalculatorUiState,
    cb: SharedCallbacks,
    onAbout: () -> Unit,
    isMenuOpen: Boolean,
    isHistoryOpen: Boolean,
    isSettingsOpen: Boolean,
    onCloseDrawers: () -> Unit,
) {
    DrawerBackdrop(
        visible = isMenuOpen || isHistoryOpen || isSettingsOpen,
        onDismiss = onCloseDrawers,
        modifier = Modifier.fillMaxSize().align(Alignment.TopStart),
    )
    // 菜单抽屉：与顶部按钮（HeaderBar top=24, button=36dp）留出充足间距
    ModeDrawer(
        visible = isMenuOpen,
        menuOrder = state.menuOrder,
        currentMode = state.mode,
        onPickMode = cb.onPickMode,
        onAbout = onAbout,
        modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 108.dp),
    )
    // 历史记录抽屉：同左
    HistoryDrawer(
        visible = isHistoryOpen,
        history = state.history,
        onPick = cb.onPickHistory,
        onClear = cb.onClearHistory,
        modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 108.dp),
    )
    // 设置抽屉：同左
    SettingsDrawer(
        visible = isSettingsOpen,
        themeMode = state.themeMode,
        onSetThemeMode = cb.onSetThemeMode,
        themeColor = state.themeColor,
        onSetThemeColor = cb.onSetThemeColor,
        onClose = cb.onCloseSettings,
        modifier = Modifier
            .fillMaxSize()
            .align(Alignment.TopStart)
            .padding(start = 20.dp, end = 20.dp, top = 108.dp, bottom = 20.dp),
    )
}
