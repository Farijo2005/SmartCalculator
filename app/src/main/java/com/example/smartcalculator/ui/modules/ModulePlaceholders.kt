package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.smartcalculator.ui.components.isDarkTheme
import com.example.smartcalculator.ui.theme.Background100
import com.example.smartcalculator.ui.theme.PanelAppleDark

/**
 * 模块共享的占位 UI 组件。
 *
 * 这些组件是"框架级"的——所有模块在占位阶段都用它们，
 * 但每个模块后续会替换为自己的实现（按键、显示文字等）。
 *
 * **共享文件**：所有人都能引用，但**不要修改它的实现**——
 * 要扩展请在自己模块文件内自定义组件。
 */

/**
 * 显示区卡片：竖向渐变背景 + 圆角。
 *
 * 默认高度 220dp，外部可用 modifier 覆盖（横屏用 fillMaxSize）。
 *
 * @param content 接收 [BoxScope]，可在卡片内放置显示文字等
 */
@Composable
fun DisplayCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = isDarkTheme()
    val top: Color = if (dark) PanelAppleDark else Color(0xFFFFFFFF)
    val bottom: Color = if (dark) Color(0xFF2C343E) else Background100
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)
    Box(
        modifier = modifier
            .height(220.dp)
            .clip(RoundedCornerShape(28.8.dp))
            .background(gradient),
    ) {
        content()
    }
}

/**
 * 按键区卡片：与显示区同款渐变，但透明度略低（视觉层次）。
 *
 * 默认高度 420dp，外部可用 modifier 覆盖。
 *
 * @param content 接收 [BoxScope]，可在卡片内放置按键网格等
 */
@Composable
fun KeypadCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = isDarkTheme()
    val top: Color = (if (dark) PanelAppleDark else Color(0xFFFFFFFF))
        .copy(alpha = if (dark) 0.92f else 0.85f)
    val bottom: Color = (if (dark) Color(0xFF2C343E) else Background100)
        .copy(alpha = if (dark) 0.92f else 0.60f)
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)
    Box(
        modifier = modifier
            .height(420.dp)
            .clip(RoundedCornerShape(28.8.dp))
            .background(gradient),
    ) {
        content()
    }
}

