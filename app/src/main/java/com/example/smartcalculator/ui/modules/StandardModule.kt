package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.ui.components.GlassCircleButton
import com.example.smartcalculator.ui.components.isDarkTheme
import com.example.smartcalculator.ui.theme.PanelAppleDark
import com.example.smartcalculator.ui.theme.Text200

/**
 * 标准计算器模块 —— 状态定义。
 *
 * 负责人：用户A。请只在本文件内修改。
 *
 * @property expression 输入行（无空格拼接按键原始序列）
 * @property display    答案行（实时结果）
 * @property evaluated  是否已按 = 得出结果：
 *                      false（输入中）→ 上大亮 / 下小暗
 *                      true（ 有结果）→ 上小暗 / 下大亮
 */
data class StandardModuleState(
    val expression: String = "",
    val display: String = "0",
    val evaluated: Boolean = false,
) : ModuleState

/**
 * 标准计算器模块入口。
 *
 * - 负责人：用户A
 * - 布局结构：
 *   上部（较小）：计算区（一块玻璃卡片，内部两行字）
 *   下部（较大）：按键区（**一块完整玻璃卡片**，内部 4×5 网格，参考 iOS 标准计算器布局）
 *   按键全用液态玻璃圆形按钮，与全局风格一致
 * - 按键：%  0  .  = （等号强调色实心）
 *         1  2  3  +
 *         4  5  6  −
 *         7  8  9  ×
 *         AC ⌫  ±  ÷  （最上排）
 *
 * **注意**：为了保证独立性，本文件没有引用 [ModuleLayout] 或 [ModulePlaceholders]。
 * 其他模块也可以直接在自己的.kt 内自定义布局与风格，互不干扰。
 *
 * @param state 当前模块状态
 * @param onIntent 用户操作回调
 * @param isLandscape 是否横屏
 * @param modifier 外部位置约束
 */
@Composable
fun StandardModule(
    state: StandardModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isLandscape) {
        // 横屏：计算区在左、按键区在右
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.weight(0.9f)) {
                DisplaySection(state = state)
            }
            Box(modifier = Modifier.weight(1.1f)) {
                KeypadSection(onIntent = onIntent)
            }
        }
    } else {
        // 竖屏：计算区在上（较小）、按键区在下（较大）
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                DisplaySection(state = state)
            }
            Box(modifier = Modifier.fillMaxWidth().weight(2.4f)) {
                KeypadSection(onIntent = onIntent)
            }
        }
    }
}

// =========================================================================
// 显示区（计算区）
// =========================================================================

@Composable
private fun DisplaySection(state: StandardModuleState) {
    GlassDisplayCard {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
        ) {
            // 两行角色根据 evaluated 翻转：
            //   输入中（!evaluated）→ 上（表达式）大亮 / 下（答案）小暗
            //   有结果（ evaluated）→ 上（表达式）小暗 / 下（答案）大亮
            val topSize: TextUnit
            val topWeight: FontWeight
            val topColor: Color
            val bottomSize: TextUnit
            val bottomWeight: FontWeight
            val bottomColor: Color
            val brightColor = LocalContentColor.current
            val darkColor = if (isDarkTheme()) {
                Text200.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            }

            if (state.evaluated) {
                topSize = 20.sp
                topWeight = FontWeight.Normal
                topColor = darkColor
                bottomSize = 48.sp
                bottomWeight = FontWeight.Bold
                bottomColor = brightColor
            } else {
                topSize = 48.sp
                topWeight = FontWeight.Bold
                topColor = brightColor
                bottomSize = 20.sp
                bottomWeight = FontWeight.Normal
                bottomColor = darkColor
            }

            // 输入行（上）
            Text(
                text = state.expression.ifBlank { "0" },
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = topWeight,
                    fontSize = topSize,
                ),
                color = topColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                lineHeight = (topSize.value * 1.1).sp,
            )
            // 答案行（下）——初始（未输入且按=前）隐藏数字，只在有输入 / 有结果后显示
            val bottomText = when {
                !state.evaluated && state.expression.isBlank() -> " "
                else -> state.display
            }
            Text(
                text = bottomText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = bottomWeight,
                    fontSize = bottomSize,
                ),
                color = bottomColor,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.End,
                lineHeight = (bottomSize.value * 1.1).sp,
            )
        }
    }
}

/**
 * 标准模块自用的显示区卡片。
 *
 * 样式与共享占位卡片对齐：同渐变、同圆角；复制到本文件内是为了文件级独立。
 */
@Composable
private fun GlassDisplayCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = isDarkTheme()
    val top: Color = if (dark) PanelAppleDark else Color(0xFFFFFFFF)
    val bottom: Color = if (dark) Color(0xFF2C343E) else Color(0xFFF2F2F7)
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.8.dp))
            .background(gradient),
    ) { content() }
}

// =========================================================================
// 按键区：单一玻璃卡片 + 4 列 × 5 行布局（参考 iOS 标准计算器）
// =========================================================================

@Composable
private fun KeypadSection(onIntent: (ModuleIntent) -> Unit) {
    GlassKeypadCard {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // —— 第 1 行：AC  ⌫  ±  ÷ ——
            KeypadRow(
                modifier = Modifier.weight(1f),
            ) {
                GlassKey("AC", variant = KeyVariant.Clear)  { onIntent(ModuleIntent.Clear) }
                GlassKey("⌫",  variant = KeyVariant.Clear)  { onIntent(ModuleIntent.Backspace) }
                GlassKey("±",  variant = KeyVariant.Neutral){ onIntent(ModuleIntent.Custom("negate")) }
                GlassKey("÷",  variant = KeyVariant.Operator) { onIntent(ModuleIntent.Input("÷")) }
            }
            // —— 第 2 行：7  8  9  × ——
            KeypadRow(
                modifier = Modifier.weight(1f),
            ) {
                GlassKey("7") { onIntent(ModuleIntent.Input("7")) }
                GlassKey("8") { onIntent(ModuleIntent.Input("8")) }
                GlassKey("9") { onIntent(ModuleIntent.Input("9")) }
                GlassKey("×", variant = KeyVariant.Operator) { onIntent(ModuleIntent.Input("×")) }
            }
            // —— 第 3 行：4  5  6  − ——
            KeypadRow(
                modifier = Modifier.weight(1f),
            ) {
                GlassKey("4") { onIntent(ModuleIntent.Input("4")) }
                GlassKey("5") { onIntent(ModuleIntent.Input("5")) }
                GlassKey("6") { onIntent(ModuleIntent.Input("6")) }
                GlassKey("−", variant = KeyVariant.Operator) { onIntent(ModuleIntent.Input("−")) }
            }
            // —— 第 4 行：1  2  3  + ——
            KeypadRow(
                modifier = Modifier.weight(1f),
            ) {
                GlassKey("1") { onIntent(ModuleIntent.Input("1")) }
                GlassKey("2") { onIntent(ModuleIntent.Input("2")) }
                GlassKey("3") { onIntent(ModuleIntent.Input("3")) }
                GlassKey("+", variant = KeyVariant.Operator) { onIntent(ModuleIntent.Input("+")) }
            }
            // —— 第 5 行：%  0  .  = ——
            KeypadRow(
                modifier = Modifier.weight(1f),
            ) {
                GlassKey("%", variant = KeyVariant.Neutral) { onIntent(ModuleIntent.Custom("percent")) }
                GlassKey("0") { onIntent(ModuleIntent.Input("0")) }
                GlassKey(".") { onIntent(ModuleIntent.Input(".")) }
                GlassKey("=", variant = KeyVariant.Equal) { onIntent(ModuleIntent.Evaluate) }
            }
        }
    }
}

/**
 * 按键区单一玻璃卡片。
 */
@Composable
private fun GlassKeypadCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isDarkTheme()
    val top: Color = (if (dark) PanelAppleDark else Color(0xFFFFFFFF))
        .copy(alpha = if (dark) 0.92f else 0.85f)
    val bottom: Color = (if (dark) Color(0xFF2C343E) else Color(0xFFF2F2F7))
        .copy(alpha = if (dark) 0.92f else 0.60f)
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.8.dp))
            .background(gradient),
    ) { content() }
}

// =========================================================================
// 按键行 / 按键封装
// =========================================================================

/** 按键的视觉变体。 */
private enum class KeyVariant {
    /** 普通数字。 */
    Default,
    /** 中性功能键（±、%）。 */
    Neutral,
    /** 红色清除键（AC / ⌫）。 */
    Clear,
    /** 运算符（÷ × − +）主题色。 */
    Operator,
    /** 等号：主题色实心背景 + 白色文字。 */
    Equal,
}

@Composable
private fun KeypadRow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * 液态玻璃按键 —— 基于 [GlassCircleButton] 封装。
 *
 * 所有按键都走同一个组件以保证风格统一。[variant] 决定颜色与背景。
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.GlassKey(
    label: String,
    variant: KeyVariant = KeyVariant.Default,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        // 用 GlassCircleButton，但对于 Equal 变体我们给一个强调色包装：
        // GlassCircleButton 本身无法自定义背景颜色（它是液态玻璃半透明），
        // 所以 Equal 按键外层再套一层主题色实心底 + 白色文字。
        when (variant) {
            KeyVariant.Equal -> EqualKeyShell {
                GlassCircleButton(size = 72.dp, onClick = onClick) {
                    Text(
                        text = label,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> GlassCircleButton(size = 72.dp, onClick = onClick) {
                val color = when (variant) {
                    KeyVariant.Clear    -> if (isDarkTheme()) Color(0xFFFF6B6B) else Color(0xFFE53935)
                    KeyVariant.Operator -> MaterialTheme.colorScheme.primary
                    KeyVariant.Neutral  -> LocalContentColor.current
                    else /* Default */  -> LocalContentColor.current
                }
                val weight = when {
                    variant == KeyVariant.Clear -> FontWeight.SemiBold
                    label.length >= 2           -> FontWeight.SemiBold
                    else                        -> FontWeight.Medium
                }
                val fontSize = when {
                    label.length >= 2 && variant == KeyVariant.Clear -> 18.sp
                    variant == KeyVariant.Operator -> 22.sp
                    variant == KeyVariant.Neutral  -> 18.sp
                    else                            -> 22.sp
                }
                CompositionLocalProvider(LocalContentColor provides color) {
                    Text(
                        text = label,
                        fontSize = fontSize,
                        fontWeight = weight,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * "=" 按键专用外壳：给液态玻璃按钮底下垫一层主题色实心底，
 * 让 "=" 看起来是 iOS 计算器那种实心强调按钮。
 *
 * 由于 GlassCircleButton 本身背景是半透明的液态玻璃，
 * 这里的外壳把整个按钮容器填充为主题色，玻璃叠加其上就呈现为"实心强调色"。
 */
@Composable
private fun EqualKeyShell(content: @Composable BoxScope.() -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(50))
            .background(primary),
        contentAlignment = Alignment.Center,
    ) { content() }
}
