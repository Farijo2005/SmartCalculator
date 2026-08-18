package com.example.smartcalculator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

/** 用于检测当前主题是否为深色背景。 */
@Composable
private fun isDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun Color.luminance(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue

/**
 * Apple iOS 风格液态玻璃容器（强对比度版本）。
 *
 * 对应 HTML 设计稿 .liquid-glass 的 Compose 等价实现。
 * 由于 Compose 没有 CSS backdrop-filter 真正对下层内容做模糊，
 * 这里采用"高对比度半透明蒙层 + 顶部白边 + 阴影"的组合来还原
 * 液态玻璃的视觉质感，在纯色背景上也能明显分辨。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 19.2.dp,
    blurRadius: Dp = 20.dp,
    tint: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme()
    // ---------- Tint（核心乳白蒙层）----------
    // 在 light 下：不低于 0.45 alpha，保证在 #F2F2F7 背景上肉眼能看见胶囊形状
    // 在 dark  下：0.18 alpha，够用即可
    val actualTint = when {
        tint != Color.Unspecified -> tint
        dark -> Color(0xFFFFFFFF).copy(alpha = 0.18f)
        else -> Color(0xFFFFFFFF).copy(alpha = 0.55f)
    }
    val bTop    = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.25f) else Color(0xFFFFFFFF).copy(alpha = 0.85f)
    val bSide   = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.16f) else Color(0xFFFFFFFF).copy(alpha = 0.55f)
    val bBottom = if (dark) Color(0xFF000000).copy(alpha = 0.18f) else Color(0xFF000000).copy(alpha = 0.10f)
    val elevation: Dp = if (dark) 8.dp else 12.dp  // shadow-lg，投影清楚可见

    val shape: Shape = RoundedCornerShape(cornerRadius)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF000000).copy(alpha = if (dark) 0.45f else 0.18f),
                spotColor    = Color(0xFF000000).copy(alpha = if (dark) 0.55f else 0.14f),
            )
            .clip(shape)
            .background(actualTint)
            .drawBehind {
                val w = size.width
                val h = size.height
                // top
                drawLine(bTop,    Offset(radiusPx, 0.5f),          Offset(w - radiusPx, 0.5f),          strokeWidth = 1f)
                // bottom
                drawLine(bBottom, Offset(radiusPx, h - 0.5f),       Offset(w - radiusPx, h - 0.5f),       strokeWidth = 1f)
                // left
                drawLine(bSide,   Offset(0.5f,       radiusPx),     Offset(0.5f,       h - radiusPx),     strokeWidth = 1f)
                // right
                drawLine(bSide,   Offset(w - 0.5f,   radiusPx),     Offset(w - 0.5f,   h - radiusPx),     strokeWidth = 1f)
            },
    ) {
        content()
    }
}

/**
 * 圆形玻璃按钮（头部菜单 / 历史 / 设置）。
 *
 * 对应 HTML .glass-btn：
 *   background       = rgba(255,255,255, 0.40~0.55)  （肉眼可辨的圆形背景）
 *   border           = 1px rgba(255,255,255, 0.60)
 *   box-shadow       = inset 0 1px 0 rgba(255,255,255, 0.70), shadow-sm outside
 *   color (foreground) = onBackground (图标深色/浅色清晰)
 */
@Composable
fun GlassCircleButton(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1.0f, tween(140), label = "btnScale")
    val dark = isDarkTheme()

    val bg             = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.24f) else Color(0xFFFFFFFF).copy(alpha = 0.55f)
    val borderC        = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.30f) else Color(0xFFFFFFFF).copy(alpha = 0.65f)
    val innerHighlight = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.18f) else Color(0xFFFFFFFF).copy(alpha = 0.75f)
    val elevation      = if (dark) 3.dp else 4.dp
    val shape = RoundedCornerShape(50)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sizePx = with(density) { size.toPx() }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            modifier = modifier
                .size(size)
                .scale(scale)
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = Color(0xFF000000).copy(alpha = if (dark) 0.40f else 0.22f),
                    spotColor    = Color(0xFF000000).copy(alpha = if (dark) 0.50f else 0.16f),
                )
                .clip(shape)
                .background(bg)
                .border(width = 1.dp, color = borderC, shape = shape)
                .drawBehind {
                    val inset = sizePx * 0.15f
                    drawLine(
                        color = innerHighlight,
                        start = Offset(inset, 0.5f),
                        end = Offset(sizePx - inset, 0.5f),
                        strokeWidth = 1f,
                    )
                }
                .clickable(interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/**
 * 抽屉内菜单项按钮：选中态使用 primary 填充。
 */
@Composable
fun GlassItemButton(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    cornerRadius: Dp = 9.6.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme()
    val baseBg = if (selected) MaterialTheme.colorScheme.primary
    else Color.White.copy(alpha = if (dark) 0.10f else 0.35f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onBackground
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1.0f, tween(140), label = "itemScale")
    val bg = if (pressed) baseBg.copy(alpha = 0.85f) else baseBg
    val shape = RoundedCornerShape(cornerRadius)
    CompositionLocalProvider(LocalContentColor provides fg) {
        Box(
            modifier = modifier
                .scale(scale)
                .clip(shape)
                .background(bg)
                .clickable(interaction, indication = null) { onClick() },
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                content()
            }
        }
    }
}
