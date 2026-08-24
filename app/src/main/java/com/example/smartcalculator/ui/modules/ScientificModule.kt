package com.example.smartcalculator.ui.modules

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.ui.components.GlassPillButton
import com.example.smartcalculator.ui.components.isDarkTheme
import com.example.smartcalculator.ui.theme.Background100
import com.example.smartcalculator.ui.theme.PanelAppleDark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private fun sinh(x: Double): Double = (exp(x) - exp(-x)) / 2.0
private fun cosh(x: Double): Double = (exp(x) + exp(-x)) / 2.0
private fun tanh(x: Double): Double = sinh(x) / cosh(x)

// ============================================================
//  Scientific Module — State
// ============================================================

data class ScientificModuleState(
    val display: String = "0",
    val expression: String = "",
    val radianMode: Boolean = true,
    val shiftMode: Boolean = false,
    val justEvaluated: Boolean = false,
    val errorMsg: String? = null,
    val variables: Map<String, Double> = emptyMap(),
    val ans: Double = 0.0,
) : ModuleState

// ============================================================
//  Token Lists for Backspace (longest-match first)
// ============================================================

private val FUNC_TOKENS: List<String> = listOf(
    "*10^(",
    "asinh(", "acosh(", "atanh(",
    "sinh(", "cosh(", "tanh(", "asin(", "acos(", "atan(",
    "sin(", "cos(", "tan(", "log(", "exp(", "abs(",
    "sqrt(", "ln(",
)

private val CONST_TOKENS: List<String> = listOf(
    "pi", "phi", "sqrt2",
)

private val VAR_TOKENS: List<String> = listOf(
    "ans",
)

// ============================================================
//  Reducer
// ============================================================

internal fun reduceScientific(
    state: ScientificModuleState,
    intent: ModuleIntent,
): ScientificModuleState = when (intent) {
    is ModuleIntent.Input -> state.inputToken(intent.value)
    is ModuleIntent.Evaluate -> state.evaluate()
    is ModuleIntent.Clear -> ScientificModuleState()
    is ModuleIntent.Backspace -> state.backspaceToken()
    is ModuleIntent.Custom -> when (intent.key) {
        "sci:mode" -> state.copy(radianMode = !state.radianMode)
        "sci:shift" -> state.copy(shiftMode = !state.shiftMode)
        "sci:store" -> {
            val v = state.display.toDoubleOrNull() ?: return state
            state.copy(
                variables = state.variables + (state.ans.toString() to v),
                errorMsg = null,
            )
        }
        "sci:recall" -> {
            val v = state.variables[state.ans.toString()]
            if (v != null) state.inputToken(v.formatSci()) else state
        }
        else -> state
    }
}

private fun ScientificModuleState.inputToken(token: String): ScientificModuleState {
    if (errorMsg != null) return copy(errorMsg = null, display = token, expression = token, justEvaluated = false)
    val newExpr = if (justEvaluated) token else expression + token
    return copy(
        expression = newExpr,
        display = newExpr,
        justEvaluated = false,
        errorMsg = null,
    )
}

private fun ScientificModuleState.backspaceToken(): ScientificModuleState {
    if (expression.isEmpty()) return this
    val dropped = dropLastSciToken(expression)
    val newDisplay = if (dropped.isEmpty()) "0" else dropped
    return copy(expression = dropped, display = newDisplay, errorMsg = null)
}

private fun dropLastSciToken(s: String): String {
    for (tok in FUNC_TOKENS) {
        if (s.endsWith(tok)) return s.dropLast(tok.length)
    }
    for (tok in CONST_TOKENS) {
        if (s.endsWith(tok)) return s.dropLast(tok.length)
    }
    for (tok in VAR_TOKENS) {
        if (s.endsWith(tok)) return s.dropLast(tok.length)
    }
    return s.dropLast(1)
}

private fun ScientificModuleState.evaluate(): ScientificModuleState {
    if (expression.isBlank()) return this
    return try {
        val result = SciEvaluator.eval(expression, radianMode, variables, ans)
        val formatted = result.formatSci()
        copy(
            display = formatted,
            expression = expression,
            ans = result,
            justEvaluated = true,
            errorMsg = null,
        )
    } catch (e: Exception) {
        copy(errorMsg = e.message ?: "计算错误", display = "错误", justEvaluated = true)
    }
}

private fun Double.formatSci(): String {
    if (isNaN() || isInfinite()) return "错误"
    val rounded = round(this * 1e12) / 1e12
    val l = rounded.toLong()
    return if (abs(rounded - l) < 1e-9) l.toString()
    else "%.10f".format(rounded).trimEnd('0').trimEnd('.')
}

// ============================================================
//  Scientific Evaluator
// ============================================================

private object SciEvaluator {
    private val tokenizerRegex = Regex(
        """\s*(pi|phi|sqrt2|ans|asinh|acosh|atanh|asin|acos|atan|sinh|cosh|tanh|sin|cos|tan|sqrt|ln|log|exp|abs)\s*\(|""" +
        """\s*(\d+\.?\d*|\.\d+)\s*|""" +
        """\s*([A-DX-YM])\s*|""" +
        """\s*([+\-*/^!(),])\s*"""
    )

    fun eval(expr: String, radianMode: Boolean, vars: Map<String, Double>, ans: Double): Double {
        val processed = preprocess(expr, radianMode)
        val tokens = tokenizerRegex.findAll(processed).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
        if (tokens.isEmpty()) throw IllegalArgumentException("表达式为空")
        val parser = SciParser(tokens, vars, ans)
        val result = parser.parseExpr()
        if (parser.pos != tokens.size) throw IllegalArgumentException("表达式不完整")
        if (result.isNaN() || result.isInfinite()) throw IllegalArgumentException("计算结果无效")
        return result
    }

    private fun preprocess(raw: String, radianMode: Boolean): String {
        var s = raw
        s = s.replace("π", "pi").replace("φ", "phi").replace("√2", "sqrt2")
        s = s.replace("×", "*").replace("÷", "/")
        s = s.replace("×10^", "10^")
        if (!radianMode) {
            s = s.replace(Regex("""sin\("""), "sin_d(")
            s = s.replace(Regex("""cos\("""), "cos_d(")
            s = s.replace(Regex("""tan\("""), "tan_d(")
            s = s.replace(Regex("""asin\("""), "asin_d(")
            s = s.replace(Regex("""acos\("""), "acos_d(")
            s = s.replace(Regex("""atan\("""), "atan_d(")
        }
        return s
    }

    private class SciParser(
        val tokens: List<String>,
        val vars: Map<String, Double>,
        val ans: Double,
    ) {
        var pos = 0

        fun parseExpr(): Double {
            var r = parseTerm()
            while (pos < tokens.size) {
                when (tokens[pos]) {
                    "+" -> { pos++; r += parseTerm() }
                    "-" -> { pos++; r -= parseTerm() }
                    else -> break
                }
            }
            return r
        }

        private fun parseTerm(): Double {
            var r = parseFactor()
            while (pos < tokens.size) {
                when (tokens[pos]) {
                    "*" -> { pos++; r *= parseFactor() }
                    "/" -> { pos++; r /= parseFactor() }
                    else -> break
                }
            }
            return r
        }

        private fun parseFactor(): Double {
            var base = parseBase()
            while (pos < tokens.size && tokens[pos] == "!") {
                pos++
                base = factorial(base)
            }
            if (pos < tokens.size && tokens[pos] == "^") {
                pos++
                return base.pow(parseFactor())
            }
            return base
        }

        private fun parseBase(): Double {
            if (pos < tokens.size && tokens[pos] == "-") { pos++; return -parseBase() }
            if (pos < tokens.size && tokens[pos] == "+") { pos++; return parseBase() }

            val t = tokens.getOrNull(pos) ?: throw IllegalArgumentException("表达式不完整")
            return when {
                t == "(" -> {
                    pos++
                    val r = parseExpr()
                    if (pos >= tokens.size || tokens[pos] != ")")
                        throw IllegalArgumentException("缺少右括号")
                    pos++
                    r
                }
                t[0].isDigit() || (t.startsWith(".") && t.length > 1) -> {
                    pos++; t.toDouble()
                }
                t in vars -> { pos++; vars[t]!! }
                t == "ans" -> { pos++; ans }
                t == "pi" -> { pos++; PI }
                t == "phi" -> { pos++; (1.0 + sqrt(5.0)) / 2.0 }
                t == "sqrt2" -> { pos++; sqrt(2.0) }
                t.startsWith("sin(") -> parseFunc(t) { a -> sin(a) }
                t.startsWith("cos(") -> parseFunc(t) { a -> cos(a) }
                t.startsWith("tan(") -> parseFunc(t) { a -> tan(a) }
                t.startsWith("asin(") -> parseFunc(t) { a -> asin(a) }
                t.startsWith("acos(") -> parseFunc(t) { a -> acos(a) }
                t.startsWith("atan(") -> parseFunc(t) { a -> atan(a) }
                t.startsWith("asinh(") -> parseFunc(t) { a -> ln(a + sqrt(a * a + 1.0)) }
                t.startsWith("acosh(") -> parseFunc(t) { a -> ln(a + sqrt(a * a - 1.0)) }
                t.startsWith("atanh(") -> parseFunc(t) { a -> 0.5 * ln((1.0 + a) / (1.0 - a)) }
                t.startsWith("sinh(") -> parseFunc(t) { a -> sinh(a) }
                t.startsWith("cosh(") -> parseFunc(t) { a -> cosh(a) }
                t.startsWith("tanh(") -> parseFunc(t) { a -> tanh(a) }
                t.startsWith("sin_d(") -> parseFunc(t) { a -> sin(a * PI / 180.0) }
                t.startsWith("cos_d(") -> parseFunc(t) { a -> cos(a * PI / 180.0) }
                t.startsWith("tan_d(") -> parseFunc(t) { a -> tan(a * PI / 180.0) }
                t.startsWith("asin_d(") -> parseFunc(t) { a -> asin(a) * 180.0 / PI }
                t.startsWith("acos_d(") -> parseFunc(t) { a -> acos(a) * 180.0 / PI }
                t.startsWith("atan_d(") -> parseFunc(t) { a -> atan(a) * 180.0 / PI }
                t.startsWith("ln(") -> parseFunc(t) { a -> ln(a) }
                t.startsWith("log(") -> parseFunc(t) { a -> log10(a) }
                t.startsWith("exp(") -> parseFunc(t) { a -> exp(a) }
                t.startsWith("sqrt(") -> parseFunc(t) { a -> sqrt(a) }
                t.startsWith("abs(") -> parseFunc(t) { a -> abs(a) }
                else -> throw IllegalArgumentException("未知 token: $t")
            }
        }

        private fun parseFunc(token: String, fn: (Double) -> Double): Double {
            pos++
            val arg = parseExpr()
            if (pos >= tokens.size || tokens[pos] != ")")
                throw IllegalArgumentException("${token.dropLast(1)} 缺少右括号")
            pos++
            return fn(arg)
        }

        private fun factorial(n: Double): Double {
            val ni = n.toInt()
            if (n < 0 || n != ni.toDouble()) throw IllegalArgumentException("阶乘仅支持非负整数")
            if (ni > 170) throw IllegalArgumentException("阶乘溢出")
            var r = 1.0
            for (i in 2..ni) r *= i
            return r
        }
    }
}

// ============================================================
//  UI Entry
// ============================================================

@Composable
fun ScientificModule(
    state: ScientificModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    SciLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = { SciDisplay(state) },
        keypad = { SciKeypad(state, onIntent) },
    )
}

// ============================================================
//  Layout
// ============================================================

@Composable
private fun SciLayout(
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    display: @Composable BoxScope.() -> Unit,
    keypad: @Composable BoxScope.() -> Unit,
) {
    if (isLandscape) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SciGlassCard(Modifier.weight(1f), display)
            SciGlassCard(Modifier.weight(1f), keypad)
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            SciGlassCard(Modifier.fillMaxWidth().weight(0.32f), display)
            SciGlassCard(Modifier.fillMaxWidth().weight(0.68f).padding(top = 12.dp), keypad)
        }
    }
}

@Composable
private fun SciGlassCard(
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
//  Display
// ============================================================

@Composable
private fun BoxScope.SciDisplay(state: ScientificModuleState) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.display, state.expression) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        val exprText = state.expression.ifBlank { " " }
        Text(
            text = exprText,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = LocalContentColor.current.copy(alpha = 0.55f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        )
        Spacer(Modifier.height(8.dp))
        val displayText = state.errorMsg ?: state.display
        val displayColor = when {
            state.errorMsg != null -> Color(0xFFFF3B30)
            state.justEvaluated -> MaterialTheme.colorScheme.primary
            else -> LocalContentColor.current
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = displayColor,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (state.radianMode) "RAD" else "DEG",
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.45f),
            )
            if (state.shiftMode) {
                Text(
                    text = "SHIFT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ============================================================
//  Keypad
// ============================================================

@Composable
private fun BoxScope.SciKeypad(
    state: ScientificModuleState,
    onIntent: (ModuleIntent) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SciWheel1(state, onIntent, Modifier.fillMaxWidth())
            SciWheel2(onIntent, Modifier.fillMaxWidth())

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                SciKeyRow(Modifier.weight(1f)) {
                    SciKey("MODE", SciKeyVariant.Mode) {
                        onIntent(ModuleIntent.Custom("sci:mode"))
                    }
                    SciKey("Shift", SciKeyVariant.Mode, highlighted = state.shiftMode) {
                        onIntent(ModuleIntent.Custom("sci:shift"))
                    }
                    SciKey(if (state.shiftMode) "sin⁻¹" else "sin", SciKeyVariant.Trig) {
                        onIntent(ModuleIntent.Input(if (state.shiftMode) "asin(" else "sin("))
                    }
                    SciKey(if (state.shiftMode) "cos⁻¹" else "cos", SciKeyVariant.Trig) {
                        onIntent(ModuleIntent.Input(if (state.shiftMode) "acos(" else "cos("))
                    }
                    SciKey(if (state.shiftMode) "tan⁻¹" else "tan", SciKeyVariant.Trig) {
                        onIntent(ModuleIntent.Input(if (state.shiftMode) "atan(" else "tan("))
                    }
                }

                SciKeyRow(Modifier.weight(1f)) {
                    SciKey(if (state.shiftMode) "10ˣ" else "log", SciKeyVariant.Func) {
                        val token = if (state.shiftMode) "10^(" else "log("
                        onIntent(ModuleIntent.Input(token))
                    }
                    SciKey(if (state.shiftMode) "eˣ" else "ln", SciKeyVariant.Func) {
                        val token = if (state.shiftMode) "exp(" else "ln("
                        onIntent(ModuleIntent.Input(token))
                    }
                    SciKey(if (state.shiftMode) "x²" else "√", SciKeyVariant.Func) {
                        val token = if (state.shiftMode) "^2" else "sqrt("
                        onIntent(ModuleIntent.Input(token))
                    }
                    SciKey(if (state.shiftMode) "y√x" else "xʸ", SciKeyVariant.Func) {
                        val token = if (state.shiftMode) "^(1/" else "^"
                        onIntent(ModuleIntent.Input(token))
                    }
                    SciKey(if (state.shiftMode) "e" else "π", SciKeyVariant.Const) {
                        val token = if (state.shiftMode) "e" else "pi"
                        onIntent(ModuleIntent.Input(token))
                    }
                }

                SciKeyRow(Modifier.weight(1f)) {
                    SciKey("7") { onIntent(ModuleIntent.Input("7")) }
                    SciKey("8") { onIntent(ModuleIntent.Input("8")) }
                    SciKey("9") { onIntent(ModuleIntent.Input("9")) }
                    SciKey("÷", SciKeyVariant.Operator) { onIntent(ModuleIntent.Input("/")) }
                    SciKey("=", SciKeyVariant.Equal) { onIntent(ModuleIntent.Evaluate) }
                }

                SciKeyRow(Modifier.weight(1f)) {
                    SciKey("4") { onIntent(ModuleIntent.Input("4")) }
                    SciKey("5") { onIntent(ModuleIntent.Input("5")) }
                    SciKey("6") { onIntent(ModuleIntent.Input("6")) }
                    SciKey("×", SciKeyVariant.Operator) { onIntent(ModuleIntent.Input("*")) }
                    SciKey(if (state.shiftMode) "RCL" else "STO", SciKeyVariant.Memory) {
                        if (state.shiftMode) {
                            onIntent(ModuleIntent.Custom("sci:recall"))
                        } else {
                            onIntent(ModuleIntent.Custom("sci:store"))
                        }
                    }
                }

                SciKeyRow(Modifier.weight(1f)) {
                    SciKey("1") { onIntent(ModuleIntent.Input("1")) }
                    SciKey("2") { onIntent(ModuleIntent.Input("2")) }
                    SciKey("3") { onIntent(ModuleIntent.Input("3")) }
                    SciKey("−", SciKeyVariant.Operator) { onIntent(ModuleIntent.Input("-")) }
                    SciKey(if (state.shiftMode) "|x|" else "1/x", SciKeyVariant.Func) {
                        val token = if (state.shiftMode) "abs(" else "1/("
                        onIntent(ModuleIntent.Input(token))
                    }
                }

                SciKeyRow(Modifier.weight(1f)) {
                    SciKey(if (state.shiftMode) "00" else "0") {
                        val token = if (state.shiftMode) "00" else "0"
                        onIntent(ModuleIntent.Input(token))
                    }
                    SciKey(".") { onIntent(ModuleIntent.Input(".")) }
                    SciKey("±", SciKeyVariant.Func) { onIntent(ModuleIntent.Input("-")) }
                    SciKey("+", SciKeyVariant.Operator) { onIntent(ModuleIntent.Input("+")) }
                    SciKey("x!", SciKeyVariant.Func) { onIntent(ModuleIntent.Input("!")) }
                }
            }

            SciKeyRow(Modifier.height(50.dp)) {
                SciKey("AC", SciKeyVariant.Clear) { onIntent(ModuleIntent.Clear) }
                if (state.shiftMode) {
                    SciKey("DEL", SciKeyVariant.Clear) { onIntent(ModuleIntent.Clear) }
                } else {
                    SciClearKey(
                        onShortTap = { onIntent(ModuleIntent.Backspace) },
                        onLongPress = { onIntent(ModuleIntent.Clear) },
                    )
                }
                SciKey("Ans", SciKeyVariant.Memory) { onIntent(ModuleIntent.Input("ans")) }
                SciKey(if (state.shiftMode) ")" else "(", SciKeyVariant.Func) {
                    val token = if (state.shiftMode) ")" else "("
                    onIntent(ModuleIntent.Input(token))
                }
                SciKey(")", SciKeyVariant.Func) { onIntent(ModuleIntent.Input(")")) }
            }
        }
    }
}

// ============================================================
//  Wheel 1: Science Functions
// ============================================================

private data class SciFuncItem(
    val label: String,
    val token: String,
    val shiftToken: String? = null,
    val shiftLabel: String? = null,
)

private val SCI_FUNC_ITEMS = listOf(
    SciFuncItem("sin", "sin(", "asin(", "sin⁻¹"),
    SciFuncItem("cos", "cos(", "acos(", "cos⁻¹"),
    SciFuncItem("tan", "tan(", "atan(", "tan⁻¹"),
    SciFuncItem("log", "log(", "10^(", "10ˣ"),
    SciFuncItem("ln", "ln(", "exp(", "eˣ"),
    SciFuncItem("√", "sqrt(", "^2", "x²"),
    SciFuncItem("xʸ", "^", "^(1/", "y√x"),
    SciFuncItem("1/x", "1/(", "abs(", "|x|"),
    SciFuncItem("x!", "!", null, null),
    SciFuncItem("×10ˣ", "*10^(", null, null),
    SciFuncItem("sinh", "sinh(", "asinh(", "sinh⁻¹"),
    SciFuncItem("cosh", "cosh(", "acosh(", "cosh⁻¹"),
    SciFuncItem("tanh", "tanh(", "atanh(", "tanh⁻¹"),
)

@Composable
private fun SciWheel1(
    state: ScientificModuleState,
    onIntent: (ModuleIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val dark = isDarkTheme()
    val contentColor = LocalContentColor.current
    val shiftMode = state.shiftMode

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(vertical = 2.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SCI_FUNC_ITEMS.forEach { item ->
            val fg = contentColor.copy(alpha = if (dark) 0.95f else 0.90f)
            val sendToken = if (shiftMode && item.shiftToken != null) item.shiftToken else item.token
            val displayLabel = if (shiftMode && item.shiftLabel != null) item.shiftLabel else item.label
            GlassPillButton(
                modifier = Modifier.height(30.dp),
                cornerRadius = 10.dp,
                onClick = { onIntent(ModuleIntent.Input(sendToken)) },
            ) {
                CompositionLocalProvider(LocalContentColor provides fg) {
                    Text(
                        displayLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalContentColor.current,
                    )
                }
            }
        }
    }
}

// ============================================================
//  Wheel 2: Variables & Constants
// ============================================================

private data class SciVarItem(val label: String, val token: String, val isConst: Boolean = false)

private val SCI_VAR_ITEMS = listOf(
    SciVarItem("A", "A"), SciVarItem("B", "B"),
    SciVarItem("C", "C"), SciVarItem("D", "D"),
    SciVarItem("X", "X"), SciVarItem("Y", "Y"),
    SciVarItem("M", "M"),
    SciVarItem("π", "pi", isConst = true),
    SciVarItem("e", "e", isConst = true),
    SciVarItem("φ", "phi", isConst = true),
    SciVarItem("√2", "sqrt2", isConst = true),
    SciVarItem("g", "9.80665", isConst = true),
)

@Composable
private fun SciWheel2(onIntent: (ModuleIntent) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val dark = isDarkTheme()
    val contentColor = LocalContentColor.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(vertical = 2.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SCI_VAR_ITEMS.forEach { item ->
            val fg = when {
                dark && item.isConst -> Color(0xFFFFB74D)
                dark -> Color(0xFFF48FB1)
                item.isConst -> Color(0xFFE65100)
                else -> Color(0xFFAD1457)
            }
            GlassPillButton(
                modifier = Modifier.height(28.dp),
                cornerRadius = 10.dp,
                onClick = { onIntent(ModuleIntent.Input(item.token)) },
            ) {
                CompositionLocalProvider(LocalContentColor provides fg) {
                    Text(
                        item.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalContentColor.current,
                    )
                }
            }
        }
    }
}

// ============================================================
//  Key Components
// ============================================================

private enum class SciKeyVariant {
    Default, Operator, Func, Trig, Mode, Clear, Equal, Memory, Const,
}

@Composable
private fun SciKeyRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun RowScope.SciKey(
    label: String,
    variant: SciKeyVariant = SciKeyVariant.Default,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed) 0.94f else 1f
    val dark = isDarkTheme()
    val primary = MaterialTheme.colorScheme.primary

    val (bgColor, borderColor, fgColor) = when (variant) {
        SciKeyVariant.Equal -> Triple(
            primary.copy(alpha = 1f),
            primary.copy(alpha = 0.85f),
            Color.White,
        )
        SciKeyVariant.Clear -> Triple(
            Color(0xFFFF6B6B).copy(alpha = if (pressed) (if (dark) 0.40f else 0.55f) else (if (dark) 0.20f else 0.25f)),
            Color(0xFFFF6B6B).copy(alpha = if (dark) 0.50f else 0.60f),
            if (dark) Color(0xFFFF6B6B) else Color(0xFFE53935),
        )
        SciKeyVariant.Operator -> Triple(
            primary.copy(alpha = if (pressed) (if (dark) 0.35f else 0.50f) else (if (dark) 0.18f else 0.22f)),
            primary.copy(alpha = if (dark) 0.40f else 0.50f),
            primary,
        )
        SciKeyVariant.Func -> Triple(
            Color(0xFF15803D).copy(alpha = if (pressed) (if (dark) 0.20f else 0.35f) else (if (dark) 0f else 0.08f)),
            Color(0xFF15803D).copy(alpha = if (dark) 0.30f else 0.40f),
            Color(0xFF15803D),
        )
        SciKeyVariant.Trig -> Triple(
            Color(0xFFA21CAF).copy(alpha = if (pressed) (if (dark) 0.20f else 0.35f) else (if (dark) 0f else 0.08f)),
            Color(0xFFA21CAF).copy(alpha = if (dark) 0.30f else 0.40f),
            Color(0xFFA21CAF),
        )
        SciKeyVariant.Mode -> Triple(
            primary.copy(alpha = if (highlighted) (if (dark) 0.35f else 0.50f) else (if (pressed) (if (dark) 0.25f else 0.35f) else (if (dark) 0.10f else 0.15f))),
            primary.copy(alpha = if (highlighted) 0.70f else (if (dark) 0.35f else 0.45f)),
            if (highlighted) primary else primary.copy(alpha = 0.90f),
        )
        SciKeyVariant.Memory -> Triple(
            Color(0xFFA16207).copy(alpha = if (pressed) (if (dark) 0.20f else 0.35f) else (if (dark) 0f else 0.08f)),
            Color(0xFFA16207).copy(alpha = if (dark) 0.30f else 0.40f),
            Color(0xFFA16207),
        )
        SciKeyVariant.Const -> Triple(
            Color(0xFFB45309).copy(alpha = if (pressed) (if (dark) 0.20f else 0.35f) else (if (dark) 0f else 0.08f)),
            Color(0xFFB45309).copy(alpha = if (dark) 0.30f else 0.40f),
            Color(0xFFB45309),
        )
        else -> Triple(
            Color.White.copy(
                alpha = when {
                    pressed && dark -> 0.14f
                    pressed -> 0.35f
                    dark -> 0f
                    else -> 0.10f
                }
            ),
            Color.White.copy(alpha = if (dark) 0.14f else 0.20f),
            LocalContentColor.current.copy(alpha = 0.95f),
        )
    }

    val baseFont = when {
        label.length >= 4 -> 11.sp
        label.length >= 3 -> 12.sp
        label.length == 2 -> 14.sp
        else -> 17.sp
    }
    val weight = if (label.length >= 2) FontWeight.SemiBold else FontWeight.Medium
    val shape = RoundedCornerShape(14.dp)
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .scale(scale)
            .shadow(
                elevation = if (dark) 0.dp else 3.dp,
                shape = shape,
                clip = false,
                ambientColor = if (dark) Color.Unspecified else Color(0xFF000000).copy(alpha = 0.05f),
                spotColor = if (dark) Color.Unspecified else Color(0xFF000000).copy(alpha = 0.05f),
            )
            .clip(shape)
            .background(bgColor)
            .border(if (dark) 1.2.dp else 1.dp, borderColor, shape)
            .let { mod ->
                if (dark) mod else
                mod.then(Modifier.drawBehind {
                    val w = size.width
                    val radiusPx = with(density) { 14.dp.toPx() }
                    val insetX = radiusPx * 0.5f
                    if (w > insetX * 2 + 2f) {
                        drawLine(
                            color = Color(0xFFFFFFFF).copy(alpha = 0.67f),
                            start = Offset(insetX, 0.5f),
                            end = Offset(w - insetX, 0.5f),
                            strokeWidth = 1f,
                        )
                    }
                })
            }
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = baseFont,
            fontWeight = weight,
            color = fgColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RowScope.SciClearKey(
    onShortTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val view = LocalView.current
    val dark = isDarkTheme()
    val clearColor = if (dark) Color(0xFFFF6B6B) else Color(0xFFE53935)

    val pressedState = remember { mutableStateOf(false) }
    val pressed = pressedState.value
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.94f else 1f,
        androidx.compose.animation.core.tween(durationMillis = 150),
        label = "sciClearScale",
    )

    val shape = RoundedCornerShape(14.dp)
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .scale(scale)
            .shadow(
                elevation = if (dark) 0.dp else 3.dp,
                shape = shape,
                clip = false,
                ambientColor = if (dark) Color.Unspecified else Color(0xFF000000).copy(alpha = 0.05f),
                spotColor = if (dark) Color.Unspecified else Color(0xFF000000).copy(alpha = 0.05f),
            )
            .clip(shape)
            .background(
                clearColor.copy(
                    alpha = when {
                        pressed && dark -> 0.30f
                        pressed -> 0.50f
                        dark -> 0f
                        else -> 0.18f
                    }
                )
            )
            .border(
                if (dark) 1.2.dp else 1.dp,
                clearColor.copy(alpha = if (dark) 0.40f else 0.50f),
                shape,
            )
            .let { mod ->
                if (dark) mod else
                mod.then(Modifier.drawBehind {
                    val w = size.width
                    val radiusPx = with(density) { 14.dp.toPx() }
                    val insetX = radiusPx * 0.5f
                    if (w > insetX * 2 + 2f) {
                        drawLine(
                            color = Color(0xFFFFFFFF).copy(alpha = 0.67f),
                            start = Offset(insetX, 0.5f),
                            end = Offset(w - insetX, 0.5f),
                            strokeWidth = 1f,
                        )
                    }
                })
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressedState.value = true
                        tryAwaitRelease()
                        pressedState.value = false
                    },
                    onTap = { onShortTap() },
                    onLongPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongPress()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "⌫",
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = clearColor,
            textAlign = TextAlign.Center,
        )
    }
}
