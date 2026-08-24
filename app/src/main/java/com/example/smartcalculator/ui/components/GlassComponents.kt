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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity

/** 用于检测当前主题是否为深色背景（internal 供 Drawers 复用）。 */
@Composable
internal fun isDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun Color.luminance(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue

/**
 * Apple iOS 液态玻璃容器 —— 严格对齐 HTML `.liquid-glass` 结构比例：
 *
 *   background:              rgba(255,255,255,0.22)        // 乳白蒙层（比例基准）
 *   backdrop-filter:         blur(20px) saturate(180%)     // 用 saturate 对角渐变近似
 *   border-top    : 1px solid rgba(255,255,255, 0.55)
 *   border-left/rt: 1px solid rgba(255,255,255, 0.35)
 *   border-bottom : 1px solid rgba(0,0,0,     0.06)
 *   box-shadow   : var(--shadow-lg)
 *
 * 注意：Compose 没有 CSS 原生 backdrop-filter。为了在 --apple-secondary（浅灰#F2F2F7）
 * 背景上仍能呈现"玻璃薄片"的层次感，我们在保留四边亮度比例（top最亮 ≈ 1.57×side、
 * side ≈ 5.8×bottom）的前提下，对绝对 alpha 做一个 ≈1.22 倍的轻微补偿。
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

    // ------- 按比例对齐 HTML，浅灰背景下做轻微可见度补偿（×1.22）-------
    // HTML tint:        light 0.22 → 补偿 0.27
    //                   dark  0.16 → 补偿 0.195
    // 深色模式：tint 使用蓝色调半透明（而非白色），避免"白色矩形"
    val actualTint = when {
        tint != Color.Unspecified -> tint
        dark -> Color(0xFF50606F).copy(alpha = 0.55f)  // 蓝灰色半透明，与 #303842 背景融合
        else -> Color(0xFFFFFFFF).copy(alpha = 0.27f)
    }
    // border-top:    rgba(255,255,255, 0.55) → 补偿 light 0.67 / dark 0.27
    val bTop    = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.22f) else Color(0xFFFFFFFF).copy(alpha = 0.67f)
    // border-left/right: rgba(255,255,255, 0.35) → 补偿 light 0.43 / dark 0.18
    val bSide   = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.15f) else Color(0xFFFFFFFF).copy(alpha = 0.43f)
    // border-bottom: rgba(0,0,0, 0.06) → 补偿 light 0.075 / dark 0.27
    val bBottom = if (dark) Color(0xFF000000).copy(alpha = 0.35f) else Color(0xFF000000).copy(alpha = 0.075f)

    // shadow-lg（HTML 精确值，不做补偿）：
    //   0 8px 24px -8px rgba(0,0,0,0.08),  0 4px 8px -4px rgba(0,0,0,0.05)
    val elevation: Dp = if (dark) 10.dp else 12.dp
    val ambient = if (dark) Color(0xFF000000).copy(alpha = 0.50f) else Color(0xFF000000).copy(alpha = 0.08f)
    val spot    = if (dark) Color(0xFF000000).copy(alpha = 0.40f) else Color(0xFF000000).copy(alpha = 0.05f)

    val shape: Shape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }

    // saturate(180%) 近似：左上偏亮白 → 右下淡
    // 深色模式：终点用完全透明（而非透明白），防止出现白色矩形边框
    val saturateBrush = Brush.linearGradient(
        0.00f to Color(0xFFFFFFFF).copy(alpha = if (dark) 0.05f else 0.125f),
        0.25f to Color(0xFFFFFFFF).copy(alpha = if (dark) 0.025f else 0.070f),
        0.60f to Color.Transparent,
        1.00f to Color.Transparent,
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = ambient,
                spotColor = spot,
            )
            .clip(shape)
            .background(actualTint)
            .background(saturateBrush)   // saturate(180%) 视觉等效
            .drawBehind {
                val w = size.width
                val h = size.height
                // top
                drawLine(bTop,    Offset(radiusPx, 0.5f),    Offset(w - radiusPx, 0.5f),    1f)
                // bottom
                drawLine(bBottom, Offset(radiusPx, h - 0.5f), Offset(w - radiusPx, h - 0.5f), 1f)
                // left
                drawLine(bSide,   Offset(0.5f, radiusPx),    Offset(0.5f, h - radiusPx),    1f)
                // right
                drawLine(bSide,   Offset(w - 0.5f, radiusPx),Offset(w - 0.5f, h - radiusPx),1f)
            },
    ) {
        content()
    }
}

/**
 * 圆形玻璃按钮（菜单 / 历史 / 设置） —— 结构对齐 HTML `.glass-btn`：
 *
 *   background:          rgba(255,255,255, 0.32)   // 基础
 *   :hover               background rgba(, 0.42) + brightness(1.04)
 *   border:              1px solid rgba(255,255,255, 0.45)
 *   box-shadow:          inset 0 1px 0 rgba(255,255,255, 0.55), var(--shadow-sm)
 *   :active transform:   scale(0.94)
 *   color:               var(--apple-foreground)
 *
 * 浅灰背景下与 GlassCard 同步做 ×1.22 的可见度补偿，并在 pressed 态用"更深的
 * 背景 + brightness↑"模拟 :hover 的高亮反馈。
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
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1.0f,
        tween(durationMillis = 150),
        label = "glassBtnScale",
    )
    val dark = isDarkTheme()

    // HTML 基础 × 1.22 补偿；pressed 态升至 HTML :hover 的 0.42（再×1.22）
    val bgAlpha = when {
        pressed -> if (dark) 0.32f else 0.51f       // HTML :hover 0.42 × 1.22 ≈ 0.51
        else    -> if (dark) 0.245f else 0.39f       // HTML idle   0.32 × 1.22 ≈ 0.39
    }
    val bg = Color(0xFFFFFFFF).copy(alpha = bgAlpha)
    val borderC        = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.34f) else Color(0xFFFFFFFF).copy(alpha = 0.55f) // 0.45×1.22
    val innerHighlight = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.22f) else Color(0xFFFFFFFF).copy(alpha = 0.67f) // 0.55×1.22

    // shadow-sm（HTML 精确值不补偿）：
    //   0 1px 2px 0 rgba(0,0,0,0.05), 0 1px 3px -1px rgba(0,0,0,0.05)
    val elevation = if (dark) 2.dp else 3.dp
    val amb = if (dark) Color(0xFF000000).copy(alpha = 0.36f) else Color(0xFF000000).copy(alpha = 0.05f)
    val spt = if (dark) Color(0xFF000000).copy(alpha = 0.36f) else Color(0xFF000000).copy(alpha = 0.05f)

    val shape = RoundedCornerShape(50)
    val density = LocalDensity.current
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
                    ambientColor = amb,
                    spotColor = spt,
                )
                .clip(shape)
                .background(bg)
                .border(width = 1.dp, color = borderC, shape = shape)
                .drawBehind {
                    // inset 0 1px 0 rgba(255,255,255,0.55) —— 圆形内只画中心 60% 段，
                    // 两端留出圆弧空间，防止直线延伸到圆边外
                    val insetX = sizePx * 0.20f
                    drawLine(
                        color = innerHighlight,
                        start = Offset(insetX, 0.5f),
                        end = Offset(sizePx - insetX, 0.5f),
                        strokeWidth = 1f,
                    )
                }
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/**
 * 抽屉内菜单项 —— 结构对齐 HTML `.drawer-item`：
 *
 *   padding:       px-3 py-2.5  (12.dp × 10.dp)
 *   border-radius: var(--apple-radius-sm) = 0.6rem = 9.6.dp
 *   :hover         background: rgba(255,255,255, 0.35)
 *   :active        background: rgba(255,255,255, 0.45); scale(0.985)
 *
 * 选中态（selected）使用 primary 背景 + onPrimary 前景，对应 HTML 中科学模式被选中时
 *   `background-color: var(--apple-primary); color: var(--apple-primary-foreground);`
 *
 * 与 GlassCard 同样做 ×1.22 的浅灰背景可见度补偿。
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // HTML hover 0.35 → ×1.22 ≈ 0.43   active 0.45 → ×1.22 ≈ 0.55
    val hoveredAlpha  = if (dark) 0.27f else 0.43f
    val pressedAlpha  = if (dark) 0.37f else 0.55f
    val idleAlpha     = if (dark) 0.0f else 0.0f

    val baseBgIdle  = if (selected) MaterialTheme.colorScheme.primary
    else Color.White.copy(alpha = idleAlpha)
    val baseBg = when {
        pressed -> if (selected) baseBgIdle.copy(alpha = 0.92f) else Color.White.copy(alpha = pressedAlpha)
        else    -> baseBgIdle
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onBackground

    val scale by animateFloatAsState(
        if (pressed) 0.985f else 1.0f,
        tween(durationMillis = 150),
        label = "itemBtnScale",
    )

    val shape = RoundedCornerShape(cornerRadius)
    CompositionLocalProvider(LocalContentColor provides fg) {
        Box(
            modifier = modifier
                .scale(scale)
                .clip(shape)
                .background(baseBg)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                content()
            }
        }
    }
}

// ============================================================================
//  GlassPillButton —— 胶囊形液态玻璃按钮（圆角矩形，非圆形）
//  从 GlassCircleButton 复制并改造：
//  - 形状：RoundedCornerShape(cornerRadius)（默认 14.dp）
//  - 尺寸：由外部 modifier 决定（weight + fillMaxSize 或自定义 size）
//  - 交互：**只用标准 .clickable()，不用 combinedClickable，不用 detectTapGestures**
//    → 短按即时响应，无 500ms 长按检测延迟
//  - 视觉：同 GlassCircleButton 的液态玻璃（背景 alpha、边框、inset 顶部高光、阴影、按压缩放）
// ============================================================================

@Composable
fun GlassPillButton(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1.0f,
        tween(durationMillis = 150),
        label = "glassPillBtnScale",
    )
    val dark = isDarkTheme()

    // 同 GlassCircleButton 的颜色比例 × 1.22 补偿
    val bgAlpha = when {
        pressed -> if (dark) 0.32f else 0.51f
        else    -> if (dark) 0.245f else 0.39f
    }
    val bg = Color(0xFFFFFFFF).copy(alpha = bgAlpha)
    val borderC        = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.34f) else Color(0xFFFFFFFF).copy(alpha = 0.55f)
    val innerHighlight = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.22f) else Color(0xFFFFFFFF).copy(alpha = 0.67f)

    val elevation = if (dark) 2.dp else 3.dp
    val amb = if (dark) Color(0xFF000000).copy(alpha = 0.36f) else Color(0xFF000000).copy(alpha = 0.05f)
    val spt = if (dark) Color(0xFF000000).copy(alpha = 0.36f) else Color(0xFF000000).copy(alpha = 0.05f)

    val shape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            modifier = modifier
                .scale(scale)
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = amb,
                    spotColor = spt,
                )
                .clip(shape)
                .background(bg)
                .border(width = 1.dp, color = borderC, shape = shape)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val radiusPx = with(density) { cornerRadius.toPx() }
                    // inset 0 1px 0 顶部高光（矩形内只画中间段，两端留圆弧空间）
                    val insetX = radiusPx * 0.5f
                    if (w > insetX * 2 + 2f) {
                        drawLine(
                            color = innerHighlight,
                            start = Offset(insetX, 0.5f),
                            end = Offset(w - insetX, 0.5f),
                            strokeWidth = 1f,
                        )
                    }
                }
                // ⚠️ 只用标准 clickable，不引入长按检测
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
