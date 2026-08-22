package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.ui.components.GlassCircleButton
import com.example.smartcalculator.ui.components.isDarkTheme
import com.example.smartcalculator.ui.theme.PanelAppleDark
import com.example.smartcalculator.ui.theme.Text200

/**
 * 程序员计算器模块 —— 子模式。
 */
enum class ProgrammerSubMode { Radix, Bitwise }

/**
 * 位运算支持的运算符。
 */
enum class BitOp(val symbol: String) {
    AND("AND"), OR("OR"), XOR("XOR"),
    SHL("<<"), SHR(">>"),
}

/**
 * 程序员计算器模块 —— 状态定义。
 *
 * 负责人：用户A。请只在本文件内修改。
 *
 * @property subMode 当前子模式：进制转换 / 位运算
 *
 * 进制转换模式专用：
 * @property radixExpression 当前进制下的输入表达式（按键序列拼接）
 * @property radixDisplay    当前数字显示（expression 的数字片段 / 最终结果）
 * @property radix           当前进制：2 / 8 / 10 / 16
 * @property radixEvaluated  是否已按 =（视觉翻转用）
 *
 * 位运算模式专用（两个操作数 + 一个运算符）：
 * @property bitLhs  左操作数（当前进制字符串）
 * @property bitOp   位运算符（null 表示未选择）
 * @property bitRhs  右操作数（空字符串表示未开始输入）
 * @property bitFocus 当前输入焦点：Lhs / Rhs（单操作数运算时只改 Lhs）
 */
data class ProgrammerModuleState(
    val subMode: ProgrammerSubMode = ProgrammerSubMode.Radix,

    // Radix mode
    val radixExpression: String = "",
    val radixDisplay: String = "0",
    val radix: Int = 10,
    val radixEvaluated: Boolean = false,
    val radixError: String? = null,  // 进制非法字符 / 溢出错误提示（null = 无）

    // Bitwise mode
    val bitLhs: String = "0",
    val bitOp: BitOp? = null,
    val bitRhs: String = "",
    val bitFocus: BitFocus = BitFocus.Lhs,
    val bitRadix: Int = 10,
    val bitEvaluated: Boolean = false,
    val bitResult: String = "",
) : ModuleState

enum class BitFocus { Lhs, Rhs }

/**
 * 程序员计算器模块入口。
 *
 * - 负责人：用户A
 * - 布局：显示区（上）+ 按键区（下，液态玻璃卡片）
 *   按键区顶部是「进制转换」/「位运算」两个 tab 按键
 */
@Composable
fun ProgrammerModule(
    state: ProgrammerModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isLandscape) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.weight(1.15f)) { DisplaySection(state, onIntent) }
            Box(modifier = Modifier.weight(0.85f)) { KeypadSection(state, onIntent) }
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 竖屏：显示区适中偏小（比1.7小），又保证方框高度不裁切数字
            Box(modifier = Modifier.fillMaxWidth().weight(1.6f)) { DisplaySection(state, onIntent) }
            Box(modifier = Modifier.fillMaxWidth().weight(2.4f)) { KeypadSection(state, onIntent) }
        }
    }
}

// =========================================================================
// 显示区
// =========================================================================

@Composable
private fun DisplaySection(state: ProgrammerModuleState, onIntent: (ModuleIntent) -> Unit) {
    GlassDisplayCard {
        when (state.subMode) {
            ProgrammerSubMode.Radix -> RadixDisplay(state) { radix ->
                onIntent(ModuleIntent.Custom("radix:activate:$radix"))
            }
            ProgrammerSubMode.Bitwise -> BitwiseDisplay(state, onIntent)
        }
    }
}

// --- 进制转换显示：2×2 四个玻璃输入框（DEC↔HEX 一行 / OCT↔BIN 一行）---
@Composable
private fun RadixDisplay(
    state: ProgrammerModuleState,
    onActivateRadix: (Int) -> Unit,
) {
    val dim = if (isDarkTheme()) Text200.copy(alpha = 0.45f)
              else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val errorColor = if (isDarkTheme()) Color(0xFFFF8A8A) else Color(0xFFD32F2F)

    // 把当前表达式解析成 Long（空/非法 → 0），派生四种进制字符串
    val activeExpr = state.radixExpression
    val value: Long = activeExpr.toLongOrNull(radix = state.radix)
        ?: state.radixDisplay.toLongOrNull(radix = state.radix) ?: 0L

    fun formatByRadix(radix: Int, activeRadix: Int, exprIfActive: String, v: Long): String {
        return if (radix == activeRadix && exprIfActive.isNotBlank()) {
            exprIfActive   // 当前激活框：直接显示用户正在编辑的表达式
        } else {
            v.toString(radix = radix).uppercase()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 提示文案
        Text(
            "在任意一个输入框中输入数字，其它进制会自动同步。",
            fontSize = 11.5.sp,
            color = dim,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = Modifier.padding(vertical = 1.dp),
        )

        // —— 第一行：DEC + HEX ——
        Row(Modifier.fillMaxWidth().weight(1.34f),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadixInputCell(
                label = "十进制 DEC",
                text = formatByRadix(10, state.radix, activeExpr, value),
                isActive = state.radix == 10,
                modifier = Modifier.weight(1f),
                onClick = { onActivateRadix(10) },
            )
            RadixInputCell(
                label = "十六进制 HEX",
                text = formatByRadix(16, state.radix, activeExpr, value),
                isActive = state.radix == 16,
                modifier = Modifier.weight(1f),
                onClick = { onActivateRadix(16) },
            )
        }

        // —— 第二行：OCT + BIN ——
        Row(Modifier.fillMaxWidth().weight(1.34f),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadixInputCell(
                label = "八进制 OCT",
                text = formatByRadix(8, state.radix, activeExpr, value),
                isActive = state.radix == 8,
                modifier = Modifier.weight(1f),
                onClick = { onActivateRadix(8) },
            )
            RadixInputCell(
                label = "二进制 BIN",
                text = formatByRadix(2, state.radix, activeExpr, value),
                isActive = state.radix == 2,
                modifier = Modifier.weight(1f),
                onClick = { onActivateRadix(2) },
            )
        }

        // 错误提示（底部固定一行，空时占位但不显示内容；错误内容给 2 行完整显示）
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (state.radixError != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(5.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(errorColor))
                    Text(
                        state.radixError,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = errorColor,
                        maxLines = 2,
                        overflow = TextOverflow.Visible,
                        softWrap = true,
                        lineHeight = 13.sp,
                    )
                }
            } else {
                Text("·", fontSize = 11.5.sp, color = Color.Transparent)
            }
        }
    }
}

/** 一个玻璃输入框：上方 label + 下方 value，支持点击切换激活进制。 */
@Composable
private fun RowScope.RadixInputCell(
    label: String,
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    val dark = isDarkTheme()

    val top: Color = (if (dark) Color(0xFF2C343E) else Color(0xFFFFFFFF))
        .copy(alpha = 0.94f)
    val bottom: Color = (if (dark) Color(0xFF272F38) else Color(0xFFECECF0))
        .copy(alpha = 0.94f)
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)

    val labelColor = if (isActive) primary else LocalContentColor.current.copy(alpha = 0.80f)
    val valueColor = LocalContentColor.current
    val borderColor = if (isActive) primary.copy(alpha = 0.78f)
                      else Color.White.copy(alpha = if (dark) 0.16f else 0.08f)

    val interactionSource = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .border(
                if (isActive) 1.6.dp else 1.dp,
                borderColor,
                RoundedCornerShape(20.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // 根据实际方框高度决定最大 Value 字号，避免底部裁切
        val h = maxHeight
        // 可用字号上限：估算 (h - 上下 padding - label行) * 字符高度约等于字号的 1.2 倍
        // 这里直接按 maxHeight 分段：
        val capFontSize = when {
            h < 50.dp -> 14.sp
            h < 58.dp -> 16.sp
            h < 66.dp -> 18.sp
            h < 74.dp -> 20.sp
            else -> 22.sp
        }

        // Value 字号：按「字符长度」统一缩（DEC/HEX/OCT/BIN 完全相同规则），
        // 再与「方框高度上限 capFontSize」取较小者，保证底部不裁切。
        val lenBased = when {
            text.length > 22 -> 12.sp   // BIN 32 位时也会触发
            text.length > 16 -> 13.sp
            text.length > 12 -> 15.sp
            text.length > 8  -> 17.sp
            else             -> capFontSize
        }
        val valueFont = if (lenBased.value <= capFontSize.value) lenBased else capFontSize

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = labelColor)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text(
                    text.ifBlank { "0" },
                    fontSize = valueFont,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

// --- 位运算显示（两数 + 运算符行 + 结果行）---
@Composable
private fun BitwiseDisplay(
    state: ProgrammerModuleState,
    onIntent: (ModuleIntent) -> Unit,
) {
    val dim = if (isDarkTheme()) Text200.copy(alpha = 0.45f)
              else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val bright = LocalContentColor.current
    val dark = isDarkTheme()

    // 解析 LHS / RHS 为 Long（按当前 bitRadix），派生 DEC/HEX/BIN 副显示
    val radix = state.bitRadix
    val lhsLong = state.bitLhs.toLongOrNull(radix) ?: 0L
    val rhsLong = if (state.bitRhs.isBlank()) null else state.bitRhs.toLongOrNull(radix)

    val resultLong = state.bitResult.toLongOrNull(radix)

    // 辅助：格式化 "BIN:xxxx xxxx xxxx"（从最低位开始每 4 位分组）
    fun formatBinGrouped(v: Long): String {
        val unsigned = if (v < 0) {
            // 负数：显示 64 位补码，最低 32 位（8 组）足够
            val bits = (v and 0xFFFFFFFFL).toString(2)
            bits.padStart(32, '0')
        } else {
            val bits = v.toString(2)
            val padLen = when {
                bits.length <= 4 -> 4
                bits.length <= 8 -> 8
                bits.length <= 12 -> 12
                bits.length <= 16 -> 16
                bits.length <= 20 -> 20
                bits.length <= 24 -> 24
                bits.length <= 28 -> 28
                else -> 32
            }
            bits.padStart(padLen, '0')
        }
        val grouped = unsigned.chunked(4).joinToString(" ")
        return "BIN:$grouped"
    }

    // 辅助：格式化 "OCT:x  HEX:y" 一行
    fun formatOctHex(v: Long): String {
        val oct = v.toString(8)
        val hex = v.toString(16).uppercase()
        return "OCT:$oct   HEX:$hex"
    }
    // 辅助：格式化单行 "OCT:..."
    fun formatOct(v: Long): String = "OCT:" + v.toString(8)
    // 辅助：格式化单行 "HEX:..."
    fun formatHex(v: Long): String = "HEX:" + v.toString(16).uppercase()
    // 辅助：把 OCT / HEX / BIN 拼成同一行（用于 RESULT 副显）
    fun formatOhb(v: Long): String {
        val oct = v.toString(8)
        val hex = v.toString(16).uppercase()
        val bin = formatBinGrouped(v).removePrefix("BIN:")
        return "OCT:$oct  HEX:$hex  BIN:$bin"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ======= 顶部提示语（一行，小字号） =======
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "在方框中输入要计算的数（默认输入十进制数）。",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = LocalContentColor.current.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
        }

        // =========================================================
        // 第一行：LHS + RHS 两个可点击玻璃输入框（按截图格式）
        // =========================================================
        Row(Modifier.fillMaxWidth().weight(1.3f),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BitwiseOperandCell(
                label = "LHS",
                valueText = state.bitLhs.ifBlank { "0" },
                v = lhsLong,
                radix = radix,
                isActive = state.bitFocus == BitFocus.Lhs,
                modifier = Modifier.weight(1f),
                formatOctHex = ::formatOctHex,
                formatBinGrouped = ::formatBinGrouped,
                onClick = { onIntent(ModuleIntent.Custom("bit:focus:Lhs")) },
            )
            BitwiseOperandCell(
                label = "RHS",
                valueText = state.bitRhs.ifBlank { "0" },
                v = rhsLong ?: 0L,
                radix = radix,
                isActive = state.bitFocus == BitFocus.Rhs,
                modifier = Modifier.weight(1f),
                formatOctHex = ::formatOctHex,
                formatBinGrouped = ::formatBinGrouped,
                onClick = { onIntent(ModuleIntent.Custom("bit:focus:Rhs")) },
            )
        }

        // =========================================================
        // 第二行：运算符按键（按用户给定符号：& | ^ ~ << >>）
        // =========================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OpSymbolKey("&", hint = "AND",
                isSelected = state.bitOp == BitOp.AND,
                primary, bright, dark) {
                onIntent(ModuleIntent.Custom("bit:op:AND"))
            }
            OpSymbolKey("|", hint = "OR",
                isSelected = state.bitOp == BitOp.OR,
                primary, bright, dark) {
                onIntent(ModuleIntent.Custom("bit:op:OR"))
            }
            OpSymbolKey("^", hint = "XOR",
                isSelected = state.bitOp == BitOp.XOR,
                primary, bright, dark) {
                onIntent(ModuleIntent.Custom("bit:op:XOR"))
            }
            OpSymbolKey("~", hint = "NOT",
                isSelected = false, // NOT 是单目，单独执行
                primary = secondary, bright, dark) {
                onIntent(ModuleIntent.Custom("bit:unary:NOT"))
            }
            OpSymbolKey("<<", hint = "SHL",
                isSelected = state.bitOp == BitOp.SHL,
                primary, bright, dark) {
                onIntent(ModuleIntent.Custom("bit:op:SHL"))
            }
            OpSymbolKey(">>", hint = "SHR",
                isSelected = state.bitOp == BitOp.SHR,
                primary, bright, dark) {
                onIntent(ModuleIntent.Custom("bit:op:SHR"))
            }
        }

        // =========================================================
        // 第三行：结果行（主值十进制大字号；下方小字号三行：OCT / HEX / BIN）
        // =========================================================
        Row(
            Modifier.fillMaxWidth().weight(0.9f),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasResult = state.bitResult.isNotBlank()
            val resV = resultLong ?: 0L
            // 结果主框（占 2 份宽度）：主值十进制 + 副一行 OCT/HEX/BIN
            BitwiseResultCell(
                modifier = Modifier.weight(2f),
                decResultText = if (hasResult) (resV.toString(10)) else "—",
                v = resV,
                visible = hasResult,
                bright = bright,
                dim = dim,
                primary = primary,
                dark = dark,
                formatOhb = ::formatOhb,
            )
        }
    }
}

/** 位运算 LHS / RHS 单框：第一行 "LHS：值"；副显 OCT/HEX 与 BIN 两行 */
@Composable
private fun RowScope.BitwiseOperandCell(
    label: String,        // "LHS" or "RHS"（与值放在同一行）
    valueText: String,    // 当前 radix 下的输入字符串
    v: Long,              // 解析后的 Long 值（派生副显示）
    radix: Int,
    isActive: Boolean,
    modifier: Modifier,
    formatOctHex: (Long) -> String,
    formatBinGrouped: (Long) -> String,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val dark = isDarkTheme()

    val top: Color = (if (dark) Color(0xFF2C343E) else Color(0xFFFFFFFF))
        .copy(alpha = 0.94f)
    val bottom: Color = (if (dark) Color(0xFF272F38) else Color(0xFFECECF0))
        .copy(alpha = 0.94f)
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)
    val borderColor = if (isActive) primary.copy(alpha = 0.78f)
                      else Color.White.copy(alpha = if (dark) 0.16f else 0.08f)
    val interactionSource = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .border(if (isActive) 1.6.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        val h = maxHeight
        // 根据实际高度动态缩字号
        val valueFont = when {
            h < 60.dp -> 17.sp
            h < 70.dp -> 19.sp
            h < 80.dp -> 21.sp
            else -> 23.sp
        }
        val labelFont = when {
            h < 60.dp -> 12.sp
            h < 75.dp -> 13.sp
            else -> 14.sp
        }
        val subFont = when {
            h < 65.dp -> 10.5.sp
            else -> 11.5.sp
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // —— 第一行：LHS：<value> 同一行 ——
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$label：",
                    fontSize = labelFont,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) primary else LocalContentColor.current.copy(alpha = 0.75f),
                )
                Text(
                    valueText,
                    fontSize = valueFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = LocalContentColor.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // —— 底部副显两行：OCT/HEX 与 BIN ——
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    formatOctHex(v),
                    fontSize = subFont,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalContentColor.current.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                )
                Text(
                    formatBinGrouped(v),
                    fontSize = (subFont.value - 0.2f).coerceAtLeast(10f).sp,
                    fontWeight = FontWeight.Medium,
                    color = LocalContentColor.current.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

/** 运算符按键（符号形式）：AND→&, OR→|, XOR→^, NOT→~, SHL→<<, SHR→>> */
@Composable
private fun RowScope.OpSymbolKey(
    symbol: String,
    hint: String,
    isSelected: Boolean,
    primary: Color,
    bright: Color,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.55f),
        contentAlignment = Alignment.Center,
    ) {
        val bg = if (isSelected) primary.copy(alpha = 0.22f)
                 else Color.White.copy(alpha = if (dark) 0.08f else 0.05f)
        val border = if (isSelected) primary.copy(alpha = 0.75f)
                     else Color.White.copy(alpha = if (dark) 0.14f else 0.08f)
        val fg = if (isSelected) primary else bright
        val fontSize = if (maxHeight < 36.dp) 16.sp else 18.sp
        val hintSize = if (maxHeight < 36.dp) 9.sp else 9.5.sp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.Center) {
                Text(symbol, fontSize = fontSize,
                     fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                     color = fg)
                Text(hint, fontSize = hintSize,
                     fontWeight = FontWeight.Medium,
                     color = fg.copy(alpha = 0.55f),
                     letterSpacing = 0.5.sp)
            }
        }
    }
}

/** 结果显示框：占 2 列宽度。大字十进靠右；下一行小字号同一行 OCT/HEX/BIN —— Box 绝对定位，不裁切字形 */
@Composable
private fun RowScope.BitwiseResultCell(
    modifier: Modifier,
    decResultText: String,  // 十进制结果作为大字号
    v: Long,
    visible: Boolean,
    bright: Color,
    dim: Color,
    primary: Color,
    dark: Boolean,
    formatOhb: (Long) -> String,   // 合并成同一行
) {
    val top = (if (dark) Color(0xFF323B47) else Color(0xFFF7F7FB))
        .copy(alpha = 0.95f)
    val bottom = (if (dark) Color(0xFF2B333D) else Color(0xFFE6E6EF))
        .copy(alpha = 0.95f)
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .border(1.4.dp, primary.copy(alpha = 0.65f), RoundedCornerShape(18.dp)),
    ) {
        val h = maxHeight
        val mainFont = when {
            !visible -> 18.sp
            h < 55.dp -> 16.sp
            h < 65.dp -> 18.sp
            h < 75.dp -> 20.sp
            else -> 22.sp
        }
        val subFont = when {
            h < 62.dp -> 9.2.sp
            else -> 9.8.sp
        }
        val titleFont = 10.5.sp

        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            // —— 左上：RESULT ——
            Text(
                modifier = Modifier.align(Alignment.TopStart),
                text = "RESULT",
                fontSize = titleFont,
                fontWeight = FontWeight.SemiBold,
                color = primary,
            )

            // —— 中部偏下靠右：十进制大字（底部保留 2.6 倍副显字高 + 3dp 避免与副显重叠/裁切）——
            val subLinePad = when {
                h < 62.dp -> 27.dp
                else -> 30.dp
            }
            Text(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(bottom = subLinePad),
                text = decResultText,
                fontSize = mainFont,
                fontWeight = FontWeight.ExtraBold,
                color = if (visible) bright else dim,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                softWrap = false,
            )

            // —— 左下：副显示一行 OCT / HEX / BIN ——
            if (visible) {
                Text(
                    modifier = Modifier.align(Alignment.BottomStart),
                    text = formatOhb(v),
                    fontSize = subFont,
                    fontWeight = FontWeight.SemiBold,
                    color = bright.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    letterSpacing = 0.2.sp,
                )
            } else {
                Text(
                    modifier = Modifier.align(Alignment.BottomStart),
                    text = "OCT:    HEX:    BIN:",
                    fontSize = subFont,
                    color = Color.Transparent,
                )
            }
        }
    }
}

// --- 玻璃卡片 ---
@Composable
private fun GlassDisplayCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = isDarkTheme()
    val top = if (dark) PanelAppleDark else Color(0xFFFFFFFF)
    val bottom = if (dark) Color(0xFF2C343E) else Color(0xFFF2F2F7)
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.8.dp))
            .background(gradient),
    ) { content() }
}

// =========================================================================
// 按键区
// =========================================================================

@Composable
private fun KeypadSection(
    state: ProgrammerModuleState,
    onIntent: (ModuleIntent) -> Unit,
) {
    GlassKeypadCard {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ===== 顶部 Tab：进制转换 / 位运算 =====
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val weightTab = Modifier.weight(1f)
                SubModeTab(
                    text = "进制转换",
                    isActive = state.subMode == ProgrammerSubMode.Radix,
                    modifier = weightTab,
                ) { onIntent(ModuleIntent.Custom("subMode:Radix")) }
                SubModeTab(
                    text = "位运算",
                    isActive = state.subMode == ProgrammerSubMode.Bitwise,
                    modifier = weightTab,
                ) { onIntent(ModuleIntent.Custom("subMode:Bitwise")) }
            }

            when (state.subMode) {
                ProgrammerSubMode.Radix -> RadixKeypad(state, onIntent, modifier = Modifier.weight(1f))
                ProgrammerSubMode.Bitwise -> BitwiseKeypad(state, onIntent, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.SubModeTab(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val bg = if (isActive) primary.copy(alpha = 0.22f)
             else Color.White.copy(alpha = if (isDarkTheme()) 0.05f else 0.08f)
    val border = if (isActive) primary.copy(alpha = 0.7f)
                 else Color.White.copy(alpha = 0.12f)
    val fg = if (isActive) primary else LocalContentColor.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            color = fg,
        )
    }
}

// --- 进制转换按键 ---
@Composable
private fun RadixKeypad(
    state: ProgrammerModuleState,
    onIntent: (ModuleIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 固定 HEX 键盘格式：6 行 × 4 列。不合法字符灰化禁用。
    val allowed = validDigitsForRadix(state.radix)

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1：A B C ×
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            HexLetterKey("A", allowed, onIntent)
            HexLetterKey("B", allowed, onIntent)
            HexLetterKey("C", allowed, onIntent)
            GlassKeyVariant("×", ProgKeyVariant.Operator) { onIntent(ModuleIntent.Input("×")) }
        }

        // Row 2：D E F ÷
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            HexLetterKey("D", allowed, onIntent)
            HexLetterKey("E", allowed, onIntent)
            HexLetterKey("F", allowed, onIntent)
            GlassKeyVariant("÷", ProgKeyVariant.Operator) { onIntent(ModuleIntent.Input("÷")) }
        }

        // Row 3：7 8 9 −
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            DigitKeyOrDisabled("7", allowed, onIntent)
            DigitKeyOrDisabled("8", allowed, onIntent)
            DigitKeyOrDisabled("9", allowed, onIntent)
            GlassKeyVariant("−", ProgKeyVariant.Operator) { onIntent(ModuleIntent.Input("−")) }
        }

        // Row 4：4 5 6 +
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            DigitKeyOrDisabled("4", allowed, onIntent)
            DigitKeyOrDisabled("5", allowed, onIntent)
            DigitKeyOrDisabled("6", allowed, onIntent)
            GlassKeyVariant("+", ProgKeyVariant.Operator) { onIntent(ModuleIntent.Input("+")) }
        }

        // Row 5：1 2 3 =
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            DigitKeyOrDisabled("1", allowed, onIntent)
            DigitKeyOrDisabled("2", allowed, onIntent)
            DigitKeyOrDisabled("3", allowed, onIntent)
            GlassKeyVariant("=", ProgKeyVariant.Equal) { onIntent(ModuleIntent.Evaluate) }
        }

        // Row 6：0 AC ⌫ ±
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            GlassKeyVariant("0", ProgKeyVariant.Default) {
                if ("0" in allowed) onIntent(ModuleIntent.Input("0"))
            }
            GlassKeyVariant("AC", ProgKeyVariant.Clear) { onIntent(ModuleIntent.Clear) }
            GlassKeyVariant("⌫", ProgKeyVariant.Clear) { onIntent(ModuleIntent.Backspace) }
            GlassKeyVariant("±", ProgKeyVariant.Neutral) {
                onIntent(ModuleIntent.Custom("radix:negate"))
            }
        }
    }
}

// --- 位运算按键（结构与进制转换相同：6 行 × 4 列固定 HEX 布局）---
@Composable
private fun BitwiseKeypad(
    state: ProgrammerModuleState,
    onIntent: (ModuleIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 固定 6 行 × 4 列，与进制转换键盘排布一致：
    // Row1: A  B  C  ≪  (左移 SHL)
    // Row2: D  E  F  ≫  (右移 SHR)
    // Row3: 7  8  9  ∧  (AND)
    // Row4: 4  5  6  ∨  (OR)
    // Row5: 1  2  3  =  (执行求值)
    // Row6: 0 AC ⌫  ±  (正负切换 / 清零 / 退格)
    val digitsForRadix: List<String> = when (state.bitRadix) {
        2 -> listOf("0", "1")
        8 -> listOf("0", "1", "2", "3", "4", "5", "6", "7")
        10 -> listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
        16 -> listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                     "A", "B", "C", "D", "E", "F")
        else -> listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1：A B C  <<
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            BitwiseDigitOrDisabled("A", digitsForRadix, onIntent)
            BitwiseDigitOrDisabled("B", digitsForRadix, onIntent)
            BitwiseDigitOrDisabled("C", digitsForRadix, onIntent)
            GlassKeyVariant("<<", ProgKeyVariant.Bitwise) {
                onIntent(ModuleIntent.Custom("bit:op:SHL"))
            }
        }

        // Row 2：D E F  >>
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            BitwiseDigitOrDisabled("D", digitsForRadix, onIntent)
            BitwiseDigitOrDisabled("E", digitsForRadix, onIntent)
            BitwiseDigitOrDisabled("F", digitsForRadix, onIntent)
            GlassKeyVariant(">>", ProgKeyVariant.Bitwise) {
                onIntent(ModuleIntent.Custom("bit:op:SHR"))
            }
        }

        // Row 3：7 8 9  &
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            BitwiseDigitOrDisabled("7", digitsForRadix, onIntent)
            BitwiseDigitOrDisabled("8", digitsForRadix, onIntent)
            BitwiseDigitOrDisabled("9", digitsForRadix, onIntent)
            GlassKeyVariant("&", ProgKeyVariant.Operator) {
                onIntent(ModuleIntent.Custom("bit:op:AND"))
            }
        }

        // Row 4：4 5 6  |
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            BitwiseDigitOrDisabled("4", digitsForRadix, onIntent)
            BitwiseDigitOrDisabled("5", digitsForRadix, onIntent)
            BitwiseDigitOrDisabled("6", digitsForRadix, onIntent)
            GlassKeyVariant("|", ProgKeyVariant.Operator) {
                onIntent(ModuleIntent.Custom("bit:op:OR"))
            }
        }

        // Row 5：1 2 3  =
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            BitwiseDigitOrDisabled("1", digitsForRadix, onIntent)
            BitwiseDigitOrDisabled("2", digitsForRadix, onIntent)
            BitwiseDigitOrDisabled("3", digitsForRadix, onIntent)
            GlassKeyVariant("=", ProgKeyVariant.Equal) { onIntent(ModuleIntent.Evaluate) }
        }

        // Row 6：0 AC ⌫  ±
        KeypadRow(Modifier.fillMaxWidth().weight(1f)) {
            GlassKeyVariant("0", ProgKeyVariant.Default) {
                if ("0" in digitsForRadix) onIntent(ModuleIntent.Input("0"))
            }
            GlassKeyVariant("AC", ProgKeyVariant.Clear) { onIntent(ModuleIntent.Clear) }
            GlassKeyVariant("⌫", ProgKeyVariant.Clear) { onIntent(ModuleIntent.Backspace) }
            GlassKeyVariant("±", ProgKeyVariant.Neutral) {
                onIntent(ModuleIntent.Custom("bit:negate"))
            }
        }
    }
}

/** 位运算键盘下的数字/字母键：不合法字符 reducer 里会忽略，这里都彩色显示。 */
@Composable
private fun RowScope.BitwiseDigitOrDisabled(
    label: String,
    allowed: List<String>,
    onIntent: (ModuleIntent) -> Unit,
) {
    GlassKeyVariant(
        label = label,
        variant = ProgKeyVariant.Default,
        onClick = {
            if (label in allowed) onIntent(ModuleIntent.Input(label))
        },
    )
}

// --- 玻璃按键卡片 ---
@Composable
private fun GlassKeypadCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isDarkTheme()
    val top = (if (dark) PanelAppleDark else Color(0xFFFFFFFF))
        .copy(alpha = if (dark) 0.92f else 0.85f)
    val bottom = (if (dark) Color(0xFF2C343E) else Color(0xFFF2F2F7))
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
// 行 / 按键封装
// =========================================================================

private enum class ProgKeyVariant {
    Default, Neutral, Clear, Operator, Equal, Toggle, Bitwise, Disabled,
}

@Composable
private fun KeypadRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** 通用液态玻璃圆形按键（按钮 size 根据行内剩余空间自适应，小格子会自动缩小）。 */
@Composable
private fun RowScope.GlassKeyVariant(
    label: String,
    variant: ProgKeyVariant = ProgKeyVariant.Default,
    maxSize: Dp? = null,
    onClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        val cap = maxSize ?: 52.dp
        val available = minOf(maxWidth, maxHeight)
        val size = minOf(cap, available)
        val disabled = variant == ProgKeyVariant.Disabled
        val safeOnClick: () -> Unit = { if (!disabled) onClick() }

        when (variant) {
            ProgKeyVariant.Equal -> EqualKeyShell(size) {
                GlassCircleButton(size = size, onClick = safeOnClick) {
                    Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                         color = Color.White, textAlign = TextAlign.Center)
                }
            }
            ProgKeyVariant.Disabled -> {
                GlassCircleButton(size = size, onClick = {}) {}
            }
            else -> {
                val color = when (variant) {
                    ProgKeyVariant.Clear    -> if (isDarkTheme()) Color(0xFFFF6B6B) else Color(0xFFE53935)
                    ProgKeyVariant.Operator -> MaterialTheme.colorScheme.primary
                    ProgKeyVariant.Toggle   -> MaterialTheme.colorScheme.tertiary
                    ProgKeyVariant.Bitwise  -> MaterialTheme.colorScheme.secondary
                    ProgKeyVariant.Neutral  -> LocalContentColor.current
                    else /* Default */  -> LocalContentColor.current
                }.copy(alpha = if (disabled) 0.25f else 1f)
                val weight = when {
                    variant == ProgKeyVariant.Clear -> FontWeight.SemiBold
                    label.length >= 2 -> FontWeight.SemiBold
                    else -> FontWeight.Medium
                }
                // 小尺寸时整体缩字号，避免字符溢出圈外
                val shrink = if (size <= 42.dp) 0.88f else 1f
                val baseFont = when {
                    label.length >= 3 -> 12.sp
                    label.length == 2 -> 14.sp
                    variant == ProgKeyVariant.Operator -> 18.sp
                    else -> 18.sp
                }
                val fontSize = (baseFont.value * shrink).sp
                GlassCircleButton(size = size, onClick = safeOnClick) {
                    CompositionLocalProvider(LocalContentColor provides color) {
                        Text(label, fontSize = fontSize, fontWeight = weight,
                             textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** 进制切换按键（BIN/OCT/DEC/HEX）：横向矩形高亮条。 */
@Composable
private fun RowScope.RadixKey(
    label: String, isSelected: Boolean, onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val border = if (isSelected) primary else Color.White.copy(alpha = 0.15f)
    val bg = if (isSelected) primary.copy(alpha = 0.20f)
             else Color.White.copy(alpha = 0.04f)
    val fg = if (isSelected) primary else LocalContentColor.current
    val interactionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(2.8f),    // 更扁 → 省高度
        contentAlignment = Alignment.Center,
    ) {
        val h = maxHeight
        val fontSize = when {
            h <= 26.dp -> 10.sp
            h <= 32.dp -> 11.sp
            else -> 12.sp
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}

/** HEX 字母键（A-F）：键盘固定彩色，不随进制灰化；不合法字符交由 reducer 报错。 */
@Composable
private fun RowScope.HexLetterKey(
    label: String,
    @Suppress("UNUSED_PARAMETER")
    allowed: List<String>,
    onIntent: (ModuleIntent) -> Unit,
) {
    GlassKeyVariant(
        label = label,
        variant = ProgKeyVariant.Default,
        onClick = { onIntent(ModuleIntent.Input(label)) },
    )
}

/** 数字键：键盘固定彩色，不随进制灰化；不合法字符交由 reducer 报错。 */
@Composable
private fun RowScope.DigitKeyOrDisabled(
    label: String,
    @Suppress("UNUSED_PARAMETER")
    allowed: List<String>,
    onIntent: (ModuleIntent) -> Unit,
    maxSize: Dp? = null,
) {
    GlassKeyVariant(
        label = label,
        variant = ProgKeyVariant.Default,
        maxSize = maxSize,
        onClick = { onIntent(ModuleIntent.Input(label)) },
    )
}

/** HEX 字母键（位运算页，允许集合不包含时禁用）。 */
@Composable
private fun RowScope.HexLetterKeyOrDisabled(
    label: String,
    allowed: List<String>,
    onIntent: (ModuleIntent) -> Unit,
    maxSize: Dp? = null,
) {
    val enabled = label in allowed
    GlassKeyVariant(
        label = label,
        variant = if (enabled) ProgKeyVariant.Default else ProgKeyVariant.Disabled,
        maxSize = maxSize,
        onClick = if (enabled) {
            { onIntent(ModuleIntent.Input(label)) }
        } else { {} },
    )
}

/** 焦点切换 Tab（LHS / RHS）。 */
@Composable
private fun RowScope.FocusTab(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val border = if (isActive) primary else Color.White.copy(alpha = 0.15f)
    val bg = if (isActive) primary.copy(alpha = 0.20f)
             else Color.White.copy(alpha = 0.04f)
    val fg = if (isActive) primary else LocalContentColor.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

/** 位运算符按键。 */
@Composable
private fun RowScope.BitOpKey(
    op: BitOp,
    currentOp: BitOp?,
    onIntent: (ModuleIntent) -> Unit,
    maxSize: Dp? = null,
) {
    val isSelected = (currentOp == op)
    GlassKeyVariant(
        label = op.symbol,
        variant = if (isSelected) ProgKeyVariant.Operator else ProgKeyVariant.Bitwise,
        maxSize = maxSize,
    ) { onIntent(ModuleIntent.Custom("bit:op:${op.name}")) }
}

/** = 按键外壳（实心主题色底）。 */
@Composable
private fun EqualKeyShell(
    maxSize: Dp = 52.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val s = minOf(maxSize, minOf(maxWidth, maxHeight))
        Box(
            modifier = Modifier
                .size(s)
                .clip(RoundedCornerShape(50))
                .background(primary),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

// =========================================================================
// Reducer（纯函数，由 ViewModel 调用）
// =========================================================================

/** 程序员模块 reducer（纯函数）。 */
fun reduceProgrammer(
    state: ProgrammerModuleState,
    intent: ModuleIntent,
): ProgrammerModuleState = when (intent) {
    is ModuleIntent.Clear -> ProgrammerModuleState(subMode = state.subMode)

    is ModuleIntent.Backspace -> when (state.subMode) {
        ProgrammerSubMode.Radix -> {
            if (state.radixExpression.isEmpty()) state
            else state.copy(
                radixExpression = state.radixExpression.dropLast(1),
                radixDisplay = state.radixExpression.dropLast(1).ifBlank { "0" },
                radixEvaluated = false,
                radixError = null,
            )
        }
        ProgrammerSubMode.Bitwise -> {
            when (state.bitFocus) {
                BitFocus.Lhs -> state.copy(
                    bitLhs = state.bitLhs.dropLast(1).ifBlank { "0" },
                    bitEvaluated = false,
                    bitResult = "",
                )
                BitFocus.Rhs -> state.copy(
                    bitRhs = state.bitRhs.dropLast(1),
                    bitEvaluated = false,
                    bitResult = "",
                )
            }
        }
    }

    is ModuleIntent.Input -> when (state.subMode) {
        ProgrammerSubMode.Radix -> {
            val tok = intent.value
            val validChars = validDigitsForRadix(state.radix)
            val isOp = tok in listOf("+", "−", "×", "÷")
            val isValidDigit = tok.length == 1 && tok.uppercase() in validChars
            val isDot = tok == "."
            val radixName = when (state.radix) {
                2 -> "二进制 BIN"
                8 -> "八进制 OCT"
                10 -> "十进制 DEC"
                16 -> "十六进制 HEX"
                else -> "${state.radix}进制"
            }
            when {
                isOp -> {
                    val expr = state.radixExpression
                    val newExpr = if (expr.isNotEmpty() &&
                        expr.last().toString() in listOf("+", "−", "×", "÷")) {
                        expr.dropLast(1) + tok
                    } else expr + tok
                    state.copy(
                        radixExpression = newExpr,
                        radixEvaluated = false,
                        radixError = null,
                    )
                }
                isValidDigit -> {
                    val expr = state.radixExpression
                    val newExpr = if (expr == "0") tok.uppercase() else expr + tok.uppercase()
                    state.copy(
                        radixExpression = newExpr,
                        radixDisplay = newExpr,
                        radixEvaluated = false,
                        radixError = null,
                    )
                }
                isDot -> state.copy(
                    radixExpression = state.radixExpression + tok,
                    radixEvaluated = false,
                    radixError = null,
                )
                else -> {
                    // 非法字符：不改动表达式，弹出错误提示
                    val label = when (tok) {
                        "A","B","C","D","E","F" -> "'$tok'（A–F 仅十六进制可用）"
                        in listOf("0","1","2","3","4","5","6","7","8","9") -> "'$tok'（超出当前进制位范围）"
                        else -> "'$tok'"
                    }
                    state.copy(radixError = "输入错误：$label 不是 $radixName 的合法字符。")
                }
            }
        }
        ProgrammerSubMode.Bitwise -> {
            // 数字/字母 → 往当前焦点字段里塞
            val tok = intent.value.uppercase()
            val valid = validDigitsForRadix(state.bitRadix)
            if (tok !in valid) {
                state
            } else {
                when (state.bitFocus) {
                    BitFocus.Lhs -> {
                        val lhs = state.bitLhs
                        val newLhs = if (lhs == "0") tok else lhs + tok
                        state.copy(bitLhs = newLhs, bitEvaluated = false, bitResult = "")
                    }
                    BitFocus.Rhs -> {
                        val rhs = state.bitRhs
                        val newRhs = if (rhs.isBlank()) tok else rhs + tok
                        state.copy(bitRhs = newRhs, bitEvaluated = false, bitResult = "")
                    }
                }
            }
        }
    }

    is ModuleIntent.Evaluate -> when (state.subMode) {
        ProgrammerSubMode.Radix -> state.copy(radixEvaluated = true, radixError = null)
        ProgrammerSubMode.Bitwise -> {
            // 两数运算：必须有 LHS、OP、RHS；或单目 NOT：直接算 LHS
            val lhs = state.bitLhs.toLongOrNull(state.bitRadix)
            val rhs = state.bitRhs.takeIf { it.isNotBlank() }?.toLongOrNull(state.bitRadix)
            val op = state.bitOp
            var result: Long? = null
            if (op != null && lhs != null && rhs != null) {
                result = when (op) {
                    BitOp.AND -> lhs and rhs
                    BitOp.OR  -> lhs or rhs
                    BitOp.XOR -> lhs xor rhs
                    BitOp.SHL -> lhs shl rhs.toInt()
                    BitOp.SHR -> lhs shr rhs.toInt()
                }
            }
            if (result != null) {
                state.copy(
                    bitEvaluated = true,
                    bitResult = result.toString(state.bitRadix).uppercase(),
                )
            } else state.copy(bitEvaluated = false, bitResult = "")
        }
    }

    is ModuleIntent.Custom -> {
        val key = intent.key
        when {
            key == "subMode:Radix" -> state.copy(subMode = ProgrammerSubMode.Radix)
            key == "subMode:Bitwise" -> state.copy(subMode = ProgrammerSubMode.Bitwise)

            // 点击上方 4 个进制输入框 → 切进制，并把当前 Long 值用新进制写回 expression
            key.startsWith("radix:activate:") -> {
                val newRadix = key.removePrefix("radix:activate:").toIntOrNull()
                if (newRadix != null && newRadix in listOf(2, 8, 10, 16)) {
                    if (newRadix == state.radix) {
                        // 点了当前激活的框：不动，清空错误就行
                        state.copy(radixError = null)
                    } else {
                        // 解析当前已有数字（优先 expression，空则 fallback display）
                        val currentLong: Long = run {
                            val byExpr = state.radixExpression.toLongOrNull(radix = state.radix)
                            if (byExpr != null) byExpr
                            else state.radixDisplay.toLongOrNull(radix = state.radix) ?: 0L
                        }
                        val inNewRadix = currentLong.toString(radix = newRadix).uppercase()
                        state.copy(
                            radix = newRadix,
                            radixExpression = if (currentLong == 0L) "" else inNewRadix,
                            radixDisplay = inNewRadix,
                            radixEvaluated = false,
                            radixError = null,
                        )
                    }
                } else state
            }

            // 直接切进制（保持旧 value 不动，不常用；保留兼容）
            key.startsWith("radix:") && !key.startsWith("radix:activate:") && key != "radix:negate" -> {
                val r = key.removePrefix("radix:").toIntOrNull()
                if (r != null && r in listOf(2, 8, 10, 16)) state.copy(radix = r, radixError = null)
                else state
            }
            key == "radix:negate" -> {
                val expr = state.radixExpression
                if (expr.isEmpty() || expr == "0") state
                else if (expr.startsWith("-")) state.copy(
                    radixExpression = expr.drop(1),
                    radixDisplay = expr.drop(1),
                    radixError = null,
                )
                else state.copy(
                    radixExpression = "-$expr",
                    radixDisplay = "-$expr",
                    radixError = null,
                )
            }

            key.startsWith("bit:radix:") -> {
                val r = key.removePrefix("bit:radix:").toIntOrNull()
                if (r != null && r in listOf(2, 8, 10, 16)) state.copy(bitRadix = r) else state
            }
            key == "bit:focus:Lhs" -> state.copy(bitFocus = BitFocus.Lhs)
            key == "bit:focus:Rhs" -> state.copy(bitFocus = BitFocus.Rhs)
            key.startsWith("bit:op:") -> {
                val name = key.removePrefix("bit:op:")
                val op = runCatching { BitOp.valueOf(name) }.getOrNull()
                if (op != null) state.copy(
                    bitOp = op,
                    // 选了双目运算符自动跳到 RHS 输入（更符合直觉）
                    bitFocus = when (op) {
                        BitOp.AND, BitOp.OR, BitOp.XOR,
                        BitOp.SHL, BitOp.SHR -> BitFocus.Rhs
                    },
                    bitEvaluated = false,
                    bitResult = "",
                ) else state
            }
            key == "bit:unary:NOT" -> {
                val v = state.bitLhs.toLongOrNull(state.bitRadix)
                state.copy(
                    bitEvaluated = true,
                    bitResult = if (v == null) "" else (v.inv()).toString(state.bitRadix).uppercase(),
                )
            }
            key == "bit:negate" -> {
                when (state.bitFocus) {
                    BitFocus.Lhs -> {
                        val s = state.bitLhs
                        if (s == "0") state
                        else if (s.startsWith("-")) state.copy(bitLhs = s.drop(1))
                        else state.copy(bitLhs = "-$s")
                    }
                    BitFocus.Rhs -> {
                        val s = state.bitRhs
                        if (s.isBlank()) state
                        else if (s.startsWith("-")) state.copy(bitRhs = s.drop(1))
                        else state.copy(bitRhs = "-$s")
                    }
                }
            }
            else -> state
        }
    }
}

private fun validDigitsForRadix(radix: Int): List<String> = when (radix) {
    2 -> listOf("0", "1")
    8 -> listOf("0", "1", "2", "3", "4", "5", "6", "7")
    10 -> listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    16 -> listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                 "A", "B", "C", "D", "E", "F")
    else -> listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
}
