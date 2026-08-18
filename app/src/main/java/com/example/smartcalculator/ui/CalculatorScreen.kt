package com.example.smartcalculator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.smartcalculator.ui.components.DrawerBackdrop
import com.example.smartcalculator.ui.components.GlassCard
import com.example.smartcalculator.ui.components.HeaderBar
import com.example.smartcalculator.ui.components.HistoryDrawer
import com.example.smartcalculator.ui.components.ModeDrawer
import com.example.smartcalculator.ui.components.SettingsDrawer
import com.example.smartcalculator.ui.theme.Background200
import com.example.smartcalculator.ui.theme.Background800

/**
 * 主屏幕（阶段一）：只保留框架。
 * - HeaderBar（三个按钮）
 * - 显示区占位卡片
 * - 按键区占位卡片
 * - 三个抽屉：菜单 / 历史 / 设置
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

    val screenBg: Color = if (isSystemInDarkTheme()) Background800 else Background200
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
                )
            }

            if (wide) {
                LandscapeContent(
                    state = state,
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
            } else {
                PortraitContent(
                    state = state,
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
}

private data class SharedCallbacks(
    val onPickMode: (CalcMode) -> Unit,
    val onPickHistory: (HistoryItem) -> Unit,
    val onClearHistory: () -> Unit,
    val onCloseSettings: () -> Unit,
)

@Composable
private fun PortraitContent(
    state: CalculatorUiState,
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
                title = "科学计算器",
                onMenuClick = onMenuClick,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 显示区占位卡片（设计稿上方的圆角卡片）
            DisplayPlaceholderCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 按键区占位卡片（设计稿下方的圆角卡片区域）
            KeypadPlaceholderCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
private fun LandscapeContent(
    state: CalculatorUiState,
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
                title = "科学计算器",
                onMenuClick = onMenuClick,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    DisplayPlaceholderCard(modifier = Modifier.fillMaxSize())
                }
                Box(modifier = Modifier.weight(1f)) {
                    KeypadPlaceholderCard(modifier = Modifier.fillMaxSize())
                }
            }
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

/**
 * 显示区占位卡片：上方空白圆角卡片（设计稿第 2 张图）
 */
@Composable
private fun DisplayPlaceholderCard(modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.height(220.dp),
        cornerRadius = 28.8.dp,
        blurRadius = 10.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd,
        ) {
            // 占位：只显示模式标签在左上角
            Text(
                text = "标准",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 20.dp),
            )
        }
    }
}

/**
 * 按键区占位卡片：下方空白圆角卡片区域（设计稿第 2 张图）
 */
@Composable
private fun KeypadPlaceholderCard(modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.height(420.dp),
        cornerRadius = 28.8.dp,
        blurRadius = 10.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // 空白占位（后续步骤填充按键）
        }
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
    ModeDrawer(
        visible = isMenuOpen,
        menuOrder = state.menuOrder,
        currentMode = state.mode,
        onPickMode = cb.onPickMode,
        onAbout = onAbout,
        modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 80.dp),
    )
    HistoryDrawer(
        visible = isHistoryOpen,
        history = state.history,
        onPick = cb.onPickHistory,
        onClear = cb.onClearHistory,
        modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 80.dp),
    )
    SettingsDrawer(
        visible = isSettingsOpen,
        onClose = cb.onCloseSettings,
        modifier = Modifier.align(Alignment.Center),
    )
}
