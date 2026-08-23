package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.ui.components.GlassCircleButton
import com.example.smartcalculator.ui.components.isDarkTheme
import com.example.smartcalculator.ui.theme.Background100
import com.example.smartcalculator.ui.theme.PanelAppleDark
import com.example.smartcalculator.ui.theme.Text200
import kotlin.math.abs
import kotlin.math.round

// ============================================================
//  ODE 子模块 —— 状态定义
// ============================================================

data class OdeSubModuleState(
    val order: Int = 1,                    // 1 = 一阶, 2 = 二阶
    val method: OdeMethod = OdeMethod.RK4,
    /** 表达式格：一阶只用 [0]（f），二阶用 [0]=a, [1]=b, [2]=f */
    val exprCells: List<String> = List(3) { "" },
    /** 数字格：[0]=x0, [1]=y0, [2]=x_end, [3]=h, [4]=y'0(二阶) */
    val numCells: List<String> = List(5) { "" },
    /** 当前激活格（全局统一编号，见 [cellMap]） */
    val activeCell: Int = 0,
    val result: List<String> = emptyList(),
    val errorMsg: String? = null,
) : ModuleState

// ---- 格子编号映射 helper ----

/** 总格子数：一阶 5，二阶 8 */
private fun odeTotalCells(order: Int) = if (order == 1) 5 else 8

/** 该格是否是表达式格 */
private fun isExprCell(active: Int, order: Int): Boolean =
    if (order == 1) active == 0 else active in 0..2

/** 表达式格在 exprCells 中的下标 */
private fun exprIdx(active: Int, order: Int): Int =
    if (order == 1) 0 else active

/** 数字格在 numCells 中的下标 */
private fun numIdx(active: Int, order: Int): Int =
    if (order == 1) active - 1 else active - 3

// ============================================================
//  ODE 子模块 —— Reducer
// ============================================================

internal fun reduceOde(
    state: OdeSubModuleState,
    intent: ModuleIntent,
): OdeSubModuleState = when (intent) {
    is ModuleIntent.Input -> state.inputToken(intent.value)
    is ModuleIntent.Evaluate -> state.evaluate()
    is ModuleIntent.Clear -> OdeSubModuleState(order = state.order, method = state.method)
    is ModuleIntent.Backspace -> state.backspace()
    is ModuleIntent.Custom -> when (intent.key) {
        "ode:order" -> {
            val newOrder = (intent.payload as? Int) ?: 1
            OdeSubModuleState(order = newOrder, method = state.method)
        }
        "ode:method" -> {
            val m = OdeMethod.valueOf((intent.payload as? String) ?: "RK4")
            state.copy(method = m, result = emptyList(), errorMsg = null)
        }
        "ode:active" -> state.copy(
            activeCell = ((intent.payload as? Int) ?: 0).coerceIn(0, odeTotalCells(state.order) - 1),
        )
        else -> state
    }
}

private fun OdeSubModuleState.inputToken(token: String): OdeSubModuleState {
    if (isExprCell(activeCell, order)) {
        val idx = exprIdx(activeCell, order)
        val newExpr = exprCells.toMutableList().also { it[idx] += token }
        return copy(exprCells = newExpr, errorMsg = null, result = emptyList())
    } else {
        // 数字格只接受数字、小数点、首位负号
        val idx = numIdx(activeCell, order)
        val cur = numCells[idx]
        val ok = token.all { it.isDigit() || it == '.' } ||
                 (token == "-" && cur.isEmpty())
        if (!ok) return this
        val newNum = numCells.toMutableList().also { it[idx] = cur + token }
        return copy(numCells = newNum, errorMsg = null, result = emptyList())
    }
}

private fun OdeSubModuleState.backspace(): OdeSubModuleState {
    if (isExprCell(activeCell, order)) {
        val idx = exprIdx(activeCell, order)
        val s = exprCells[idx]
        if (s.isEmpty()) return this
        val newExpr = exprCells.toMutableList().also { it[idx] = s.dropLast(1) }
        return copy(exprCells = newExpr, errorMsg = null, result = emptyList())
    } else {
        val idx = numIdx(activeCell, order)
        val s = numCells[idx]
        if (s.isEmpty()) return this
        val newNum = numCells.toMutableList().also { it[idx] = s.dropLast(1) }
        return copy(numCells = newNum, errorMsg = null, result = emptyList())
    }
}

private fun OdeSubModuleState.evaluate(): OdeSubModuleState {
    return try {
        val h = numCells[3].toDouble()
        if (h <= 0) throw IllegalArgumentException("步长 h 必须 > 0")
        val x0 = numCells[0].toDouble()
        val y0 = numCells[1].toDouble()
        val xEnd = numCells[2].toDouble()
        if (xEnd == x0) throw IllegalArgumentException("x_end 不能等于 x₀")

        if (order == 1) {
            val fExpr = exprCells[0]
            if (fExpr.isBlank()) throw IllegalArgumentException("请输入右端函数 f(x,y)")
            val f = { x: Double, y: Double -> MathExprEvaluator.eval(fExpr, x, y) }
            val y = OdeSolver.solve1st(f, x0, y0, xEnd, h, method)
            copy(result = listOf("y(${fmt(xEnd)}) = ${fmt(y)}"), errorMsg = null)
        } else {
            val aExpr = exprCells[0]; val bExpr = exprCells[1]; val fExpr = exprCells[2]
            if (aExpr.isBlank() || bExpr.isBlank() || fExpr.isBlank())
                throw IllegalArgumentException("请填写 a(x), b(x), f(x)")
            val a = { x: Double -> MathExprEvaluator.eval(aExpr, x, 0.0) }
            val b = { x: Double -> MathExprEvaluator.eval(bExpr, x, 0.0) }
            val f = { x: Double -> MathExprEvaluator.eval(fExpr, x, 0.0) }
            val dy0 = numCells[4].toDouble()
            val (y, dy) = OdeSolver.solve2nd(a, b, f, x0, y0, dy0, xEnd, h, method)
            copy(result = listOf(
                "y(${fmt(xEnd)}) = ${fmt(y)}",
                "y'(${fmt(xEnd)}) = ${fmt(dy)}",
            ), errorMsg = null)
        }
    } catch (e: Exception) {
        copy(errorMsg = e.message ?: "求解失败", result = emptyList())
    }
}

private fun fmt(d: Double): String {
    val r = round(d * 1e9) / 1e9
    val l = r.toLong()
    return if (abs(r - l) < 1e-9) l.toString()
           else "%.6f".format(r).trimEnd('0').trimEnd('.')
}

// ============================================================
//  ODE 子模块 —— UI 入口
// ============================================================

@Composable
internal fun OdeSubModule(
    state: OdeSubModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    OdeLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = { OdeDisplay(state, onIntent) },
        keypad = { OdeKeypad(state, onIntent) },
    )
}

// ---- 布局：仿 ModuleLayout 但自带玻璃卡片 ----

@Composable
private fun OdeLayout(
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    display: @Composable BoxScope.() -> Unit,
    keypad: @Composable BoxScope.() -> Unit,
) {
    if (isLandscape) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OdeGlassCard(Modifier.weight(1f), display)
            OdeGlassCard(Modifier.weight(1f), keypad)
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            OdeGlassCard(Modifier.fillMaxWidth().weight(0.40f), display)
            OdeGlassCard(Modifier.fillMaxWidth().weight(0.60f).padding(top = 16.dp), keypad)
        }
    }
}

@Composable
private fun OdeGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isDarkTheme()
    val top = (if (dark) PanelAppleDark else Color(0xFFFFFFFF))
        .copy(alpha = if (dark) 0.92f else 0.85f)
    val bottom = (if (dark) Color(0xFF2C343E) else Background100)
        .copy(alpha = if (dark) 0.92f else 0.60f)
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.8.dp))
            .background(gradient),
    ) { content() }
}

// ============================================================
//  显示屏：ODE 参数表
// ============================================================

@Composable
private fun BoxScope.OdeDisplay(
    state: OdeSubModuleState,
    onIntent: (ModuleIntent) -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.result, state.errorMsg) {
        if (state.result.isNotEmpty() || state.errorMsg != null) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ===== 方程行 =====
        if (state.order == 1) {
            // y' = [ f(x,y) ]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("y' =", style = eqLabelStyle(), color = eqLabelColor())
                ParamCell(
                    text = state.exprCells[0],
                    placeholder = "f(x,y)",
                    active = state.activeCell == 0,
                    wide = true,
                    onClick = { onIntent(ModuleIntent.Custom("ode:active", 0)) },
                )
            }
        } else {
            // y'' + [ a ] y' + [ b ] y = [ f(x) ]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("y'' +", style = eqLabelStyle(), color = eqLabelColor())
                ParamCell(
                    text = state.exprCells[0],
                    placeholder = "a(x)",
                    active = state.activeCell == 0,
                    onClick = { onIntent(ModuleIntent.Custom("ode:active", 0)) },
                )
                Text("y' +", style = eqLabelStyle(), color = eqLabelColor())
                ParamCell(
                    text = state.exprCells[1],
                    placeholder = "b(x)",
                    active = state.activeCell == 1,
                    onClick = { onIntent(ModuleIntent.Custom("ode:active", 1)) },
                )
                Text("y =", style = eqLabelStyle(), color = eqLabelColor())
                ParamCell(
                    text = state.exprCells[2],
                    placeholder = "f(x)",
                    active = state.activeCell == 2,
                    onClick = { onIntent(ModuleIntent.Custom("ode:active", 2)) },
                )
            }
        }

        // ===== 初始条件行 =====
        // x₀, y₀
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledCell("x₀ =", state.numCells[0], "0",
                active = state.activeCell == cellIdx(state.order, 0),
                onClick = { onIntent(ModuleIntent.Custom("ode:active", cellIdx(state.order, 0))) },
            )
            LabeledCell("y₀ =", state.numCells[1], "1",
                active = state.activeCell == cellIdx(state.order, 1),
                onClick = { onIntent(ModuleIntent.Custom("ode:active", cellIdx(state.order, 1))) },
            )
        }
        // x_end, y'₀（二阶）或 h（一阶）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledCell("x_end =", state.numCells[2], "1",
                active = state.activeCell == cellIdx(state.order, 2),
                onClick = { onIntent(ModuleIntent.Custom("ode:active", cellIdx(state.order, 2))) },
            )
            if (state.order == 2) {
                LabeledCell("y'₀ =", state.numCells[4], "0",
                    active = state.activeCell == cellIdx(state.order, 4),
                    onClick = { onIntent(ModuleIntent.Custom("ode:active", cellIdx(state.order, 4))) },
                )
            } else {
                LabeledCell("h =", state.numCells[3], "0.1",
                    active = state.activeCell == 4,
                    onClick = { onIntent(ModuleIntent.Custom("ode:active", 4)) },
                )
            }
        }
        // 二阶多一行 h
        if (state.order == 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabeledCell("h =", state.numCells[3], "0.1",
                    active = state.activeCell == cellIdx(state.order, 3),
                    onClick = { onIntent(ModuleIntent.Custom("ode:active", cellIdx(state.order, 3))) },
                )
            }
        }

        // ===== 方法选择 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OdeMethodTab("RK4", state.method == OdeMethod.RK4, Modifier.weight(1f)) {
                onIntent(ModuleIntent.Custom("ode:method", "RK4"))
            }
            OdeMethodTab("欧拉", state.method == OdeMethod.Euler, Modifier.weight(1f)) {
                onIntent(ModuleIntent.Custom("ode:method", "Euler"))
            }
        }

        // ===== 结果区 =====
        when {
            state.errorMsg != null -> {
                Text("ans =", style = ansLabelStyle(), color = ansLabelColor())
                Text("  ⚠ ${state.errorMsg}", style = ansValueStyle(), color = Color(0xFFFF3B30))
            }
            state.result.isNotEmpty() -> {
                Text("ans =", style = ansLabelStyle(), color = ansLabelColor())
                state.result.forEach {
                    Text(it, style = ansValueStyle(), color = MaterialTheme.colorScheme.primary)
                }
            }
            else -> {
                Text(
                    "% 填好参数后按 ↵ 计算",
                    style = MaterialTheme.typography.bodySmall,
                    color = ansLabelColor(),
                )
            }
        }
    }
}

/** numCells 下标 → 全局格子编号 */
private fun cellIdx(order: Int, numIndex: Int): Int =
    if (order == 1) numIndex + 1 else numIndex + 3

// ---- 显示屏样式 helper ----

@Composable private fun eqLabelStyle() = MaterialTheme.typography.bodyMedium
@Composable private fun eqLabelColor() = LocalContentColor.current.copy(alpha = 0.7f)
@Composable private fun ansLabelStyle() = MaterialTheme.typography.bodyMedium
@Composable private fun ansLabelColor() = LocalContentColor.current.copy(alpha = 0.5f)
@Composable private fun ansValueStyle() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)

// ---- 参数格组件 ----

@Composable
private fun RowScope.ParamCell(
    text: String,
    placeholder: String,
    active: Boolean,
    wide: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val dark = isDarkTheme()
    val bg = if (dark) Color(0xFF000000).copy(alpha = 0.25f) else Color(0xFF000000).copy(alpha = 0.05f)
    val borderColor = if (active) accent else Color.White.copy(alpha = if (dark) 0.15f else 0.30f)
    val mod = if (wide) Modifier.weight(1f) else Modifier.width(72.dp)
    Box(
        modifier = mod
            .padding(start = 6.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text.ifBlank { placeholder },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = if (text.isBlank()) ansLabelColor()
                   else if (active) accent
                   else LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun RowScope.LabeledCell(
    label: String,
    text: String,
    placeholder: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = eqLabelColor())
    ParamCell(text = text, placeholder = placeholder, active = active, onClick = onClick)
}

// ---- 方法选择 Tab（扁矩形） ----

@Composable
private fun RowScope.OdeMethodTab(
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
    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

// ============================================================
//  键盘：Tab 行 + 水平滚轮函数条 + 6×4 按键
// ============================================================

@Composable
private fun BoxScope.OdeKeypad(
    state: OdeSubModuleState,
    onIntent: (ModuleIntent) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ===== 顶部 Tab：一阶 / 二阶 =====
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OdeSubTab("一阶", state.order == 1, Modifier.weight(1f)) {
                    onIntent(ModuleIntent.Custom("ode:order", 1))
                }
                OdeSubTab("二阶", state.order == 2, Modifier.weight(1f)) {
                    onIntent(ModuleIntent.Custom("ode:order", 2))
                }
            }

            // ===== 水平滚轮函数条 =====
            FunctionWheel(state, onIntent, Modifier.fillMaxWidth())

            // ===== 6×4 按键：均匀分配剩余高度 =====
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // R1: x y ( )
                OdeKeyRow(Modifier.weight(1f)) {
                    OdeKey("x", OdeVar.Default) { onIntent(ModuleIntent.Input("x")) }
                    OdeKey("y", if (state.order == 2) OdeVar.Disabled else OdeVar.Default) {
                        onIntent(ModuleIntent.Input("y"))
                    }
                    OdeKey("(", OdeVar.Default) { onIntent(ModuleIntent.Input("(")) }
                    OdeKey(")", OdeVar.Default) { onIntent(ModuleIntent.Input(")")) }
                }
                // R2: π e ^ +
                OdeKeyRow(Modifier.weight(1f)) {
                    OdeKey("π", OdeVar.Default) { onIntent(ModuleIntent.Input("pi")) }
                    OdeKey("e", OdeVar.Default) { onIntent(ModuleIntent.Input("e")) }
                    OdeKey("^", OdeVar.Operator) { onIntent(ModuleIntent.Input("^")) }
                    OdeKey("+", OdeVar.Operator) { onIntent(ModuleIntent.Input("+")) }
                }
                // R3: 7 8 9 /
                OdeKeyRow(Modifier.weight(1f)) {
                    OdeKey("7") { onIntent(ModuleIntent.Input("7")) }
                    OdeKey("8") { onIntent(ModuleIntent.Input("8")) }
                    OdeKey("9") { onIntent(ModuleIntent.Input("9")) }
                    OdeKey("/", OdeVar.Operator) { onIntent(ModuleIntent.Input("/")) }
                }
                // R4: 4 5 6 ×
                OdeKeyRow(Modifier.weight(1f)) {
                    OdeKey("4") { onIntent(ModuleIntent.Input("4")) }
                    OdeKey("5") { onIntent(ModuleIntent.Input("5")) }
                    OdeKey("6") { onIntent(ModuleIntent.Input("6")) }
                    OdeKey("×", OdeVar.Operator) { onIntent(ModuleIntent.Input("*")) }
                }
                // R5: 1 2 3 −
                OdeKeyRow(Modifier.weight(1f)) {
                    OdeKey("1") { onIntent(ModuleIntent.Input("1")) }
                    OdeKey("2") { onIntent(ModuleIntent.Input("2")) }
                    OdeKey("3") { onIntent(ModuleIntent.Input("3")) }
                    OdeKey("−", OdeVar.Operator) { onIntent(ModuleIntent.Input("-")) }
                }
                // R6: 0 . ⌫ ↵
                OdeKeyRow(Modifier.weight(1f)) {
                    OdeKey("0") { onIntent(ModuleIntent.Input("0")) }
                    OdeKey(".") { onIntent(ModuleIntent.Input(".")) }
                    OdeKey("⌫", OdeVar.Clear) { onIntent(ModuleIntent.Backspace) }
                    OdeKey("↵", OdeVar.Equal) { onIntent(ModuleIntent.Evaluate) }
                }
            }
        }
    }
}

// ---- 顶部 Tab（一阶/二阶） ----

@Composable
private fun RowScope.OdeSubTab(
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
    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

// ============================================================
//  水平滚轮函数条
// ============================================================

private data class FuncItem(val label: String, val token: String)

// 常用函数在前，不常用在后
private val FUNC_ITEMS: List<FuncItem> = listOf(
    FuncItem("sin", "sin("), FuncItem("cos", "cos("), FuncItem("tan", "tan("),
    FuncItem("ln", "ln("), FuncItem("log", "log("), FuncItem("exp", "exp("),
    FuncItem("√", "sqrt("), FuncItem("abs", "abs("),
    FuncItem("asin", "asin("), FuncItem("acos", "acos("), FuncItem("atan", "atan("),
    FuncItem("sinh", "sinh("), FuncItem("cosh", "cosh("), FuncItem("tanh", "tanh("),
)

@Composable
private fun FunctionWheel(
    state: OdeSubModuleState,
    onIntent: (ModuleIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wheelScroll = rememberScrollState()
    val dark = isDarkTheme()
    val contentColor = LocalContentColor.current

    Box(
        modifier = modifier
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(wheelScroll)
                .padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FUNC_ITEMS.forEach { fi ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale = if (pressed) 0.92f else 1f
                val bg = if (pressed) Color.White.copy(alpha = if (dark) 0.25f else 0.35f)
                         else Color.White.copy(alpha = if (dark) 0.08f else 0.10f)
                val fg = contentColor.copy(alpha = if (dark) 0.95f else 0.90f)
                val border = Color.White.copy(alpha = if (dark) 0.14f else 0.20f)
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onIntent(ModuleIntent.Input(fi.token)) },
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        fi.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = fg,
                    )
                }
            }
        }
    }
}

// ============================================================
//  按键组件（仿程序员模块 GlassKeyVariant）
// ============================================================

private enum class OdeVar { Default, Operator, Clear, Equal, Disabled }

@Composable
private fun OdeKeyRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun RowScope.OdeKey(
    label: String,
    variant: OdeVar = OdeVar.Default,
    onClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        val cap = 52.dp
        val available = minOf(maxWidth, maxHeight)
        val size = minOf(cap, available)
        val disabled = variant == OdeVar.Disabled
        val safeOnClick: () -> Unit = { if (!disabled) onClick() }

        when (variant) {
            OdeVar.Equal -> EqualShell(size) {
                GlassCircleButton(size = size, onClick = safeOnClick) {
                    Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                         color = Color.White, textAlign = TextAlign.Center)
                }
            }
            OdeVar.Disabled -> {
                GlassCircleButton(size = size, onClick = {}) {}
            }
            else -> {
                val color = when (variant) {
                    OdeVar.Clear    -> if (isDarkTheme()) Color(0xFFFF6B6B) else Color(0xFFE53935)
                    OdeVar.Operator -> MaterialTheme.colorScheme.primary
                    else            -> LocalContentColor.current
                }.copy(alpha = if (disabled) 0.25f else 1f)
                val weight = if (label.length >= 2) FontWeight.SemiBold else FontWeight.Medium
                val shrink = if (size <= 42.dp) 0.88f else 1f
                val baseFont = when {
                    label.length >= 3 -> 12.sp
                    label.length == 2 -> 14.sp
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

@Composable
private fun EqualShell(
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
