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
import androidx.compose.foundation.layout.widthIn
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

private val OPERATOR_CHARS = setOf('+', '-', '*', '/', '^')
private val SINGLE_OPS = setOf("+", "-", "*", "/", "^", "!")

private fun ScientificModuleState.inputToken(token: String): ScientificModuleState {
    if (errorMsg != null) {
        return copy(errorMsg = null, display = token, expression = token, justEvaluated = false)
    }

    var expr = expression
    // 如果刚刚计算完，替换整个表达式
    if (justEvaluated) {
        return copy(
            expression = token,
            display = token,
            justEvaluated = false,
            errorMsg = null,
        )
    }

    // 容错 1：运算符替换（如果最后一个字符是运算符，新 token 也是运算符，则替换）
    if (token.length == 1 && token[0] in OPERATOR_CHARS && expr.isNotEmpty()) {
        val lastChar = expr.last()
        if (lastChar in OPERATOR_CHARS || lastChar == '!') {
            // 回退最后一个运算符
            expr = expr.dropLast(1)
            // 避免出现 "5+-" 这种情况，改为 "5-"
            if (expr.isNotEmpty() && expr.last() in OPERATOR_CHARS) {
                expr = expr.dropLast(1)
            }
            return copy(
                expression = expr + token,
                display = expr + token,
                justEvaluated = false,
                errorMsg = null,
            )
        }
    }

    // 容错 2：小数点防重复
    if (token == "." && expr.isNotEmpty()) {
        // 找到最后一个运算符后的所有字符，检查是否已包含小数点
        val lastSegment = expr.split(Regex("[+\\-*/^()!]")).lastOrNull() ?: ""
        if ('.' in lastSegment) return this // 已有小数点，忽略
    }

    // 容错 3：避免在空表达式时输入运算符（除了负号）
    if (expr.isEmpty() && token.length == 1 && token[0] in setOf('+', '*', '/', '^')) {
        return this // 忽略非法起始
    }

    val newExpr = expr + token
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
    // 常量 Token
    private val CONST_TOKENS = setOf("pi", "phi", "sqrt2", "ans")
    // 函数 Token（带括号）
    private val FUNC_TOKENS = setOf(
        "sin(", "cos(", "tan(", "asin(", "acos(", "atan(",
        "sinh(", "cosh(", "tanh(", "asinh(", "acosh(", "atanh(",
        "sin_d(", "cos_d(", "tan_d(", "asin_d(", "acos_d(", "atan_d(",
        "ln(", "log(", "exp(", "sqrt(", "abs(",
    )
    // 变量 Token
    private val VAR_TOKENS = setOf("A", "B", "C", "D", "X", "Y", "M")

    fun eval(expr: String, radianMode: Boolean, vars: Map<String, Double>, ans: Double): Double {
        val processed = preprocess(expr, radianMode)
        val rawTokens = tokenize(processed)
        if (rawTokens.isEmpty()) throw IllegalArgumentException("表达式为空")
        val tokens = injectImplicitMultiplication(rawTokens)
        val parser = SciParser(tokens, vars, ans)
        val result = parser.parseExpr()
        if (parser.pos != tokens.size) throw IllegalArgumentException("表达式不完整")
        if (result.isNaN() || result.isInfinite()) throw IllegalArgumentException("计算结果无效")
        return result
    }

    private fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    if (c == '.') i++
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    // 科学计数法处理 1e5 或 1.5e-3
                    if (i < input.length && (input[i] == 'e' || input[i] == 'E')) {
                        // 确保这不是单词 "e" 或 "exp" 开头
                        if (i + 1 >= input.length || !input[i + 1].isLetter()) {
                            i++
                            if (i < input.length && (input[i] == '+' || input[i] == '-')) i++
                            while (i < input.length && input[i].isDigit()) i++
                        }
                    }
                    tokens.add(input.substring(start, i))
                }
                c.isLetter() -> {
                    val start = i
                    while (i < input.length && (input[i].isLetter() || input[i].isDigit())) i++
                    val word = input.substring(start, i).lowercase()
                    val originalWord = input.substring(start, i)
                    // 检查是否紧跟 "("
                    val hasParen = i < input.length && input[i] == '('
                    // 尝试匹配常量或函数
                    when {
                        // 特殊长 token：sqrt2 (5字符，不以括号结尾)
                        word == "sqrt2" -> { tokens.add("sqrt2") }
                        // 带括号的函数：直接组合 word + "("
                        hasParen -> {
                            val fullToken = word + "("
                            when (fullToken) {
                                "sin(", "cos(", "tan(", "asin(", "acos(", "atan(",
                                "sinh(", "cosh(", "tanh(", "asinh(", "acosh(", "atanh(",
                                "sin_d(", "cos_d(", "tan_d(", "asin_d(", "acos_d(", "atan_d(",
                                "ln(", "log(", "exp(", "sqrt(", "abs(" -> {
                                    tokens.add(fullToken)
                                    i++ // 跳过 '('
                                }
                                else -> {
                                    // word 后面跟了 ( 但不是已知函数，可能是隐式乘法 sin( 但 sin 不是函数？
                                    // 回退：把 word 当普通 token
                                    tokens.add(originalWord)
                                }
                            }
                        }
                        // 常量/变量识别（不带括号）
                        word == "pi" -> { tokens.add("pi") }
                        word == "phi" -> { tokens.add("phi") }
                        word == "ans" -> { tokens.add("ans") }
                        // 单个变量 A-Z
                        originalWord.length == 1 && originalWord in VAR_TOKENS -> {
                            tokens.add(originalWord)
                        }
                        // 独立 'e' (自然对数底)
                        word == "e" -> { tokens.add("e") }
                        else -> throw IllegalArgumentException("未知标识符: $originalWord")
                    }
                }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '!' ||
                c == '(' || c == ')' || c == ',' -> {
                    tokens.add(c.toString())
                    i++
                }
                else -> throw IllegalArgumentException("非法字符: $c")
            }
        }
        return tokens
    }

    // 隐式乘法：在需要的地方插入 "*"
    private fun injectImplicitMultiplication(tokens: List<String>): List<String> {
        if (tokens.size <= 1) return tokens
        val result = mutableListOf<String>()
        for (idx in tokens.indices) {
            val token = tokens[idx]
            result.add(token)
            if (idx == tokens.size - 1) break
            val next = tokens[idx + 1]
            // 检查前一个 token 是否是"值"
            val isValue = isValueLike(token)
            // 检查后一个 token 是否是"起始"
            val isStart = isStartLike(next)
            if (isValue && isStart) {
                // 避免在运算符之间插入乘号
                if (token in setOf("+", "-", "*", "/", "^", "!", "(")) continue
                result.add("*")
            }
        }
        return result
    }

    private fun isValueLike(t: String): Boolean {
        if (t == ")" || t == "!") return true
        if (t[0].isDigit() || t.startsWith(".")) return true
        if (t in CONST_TOKENS) return true
        if (t in VAR_TOKENS) return true
        if (t == "e") return true
        return false
    }

    private fun isStartLike(t: String): Boolean {
        if (t == "(") return true
        if (t.startsWith("sin(") || t.startsWith("cos(") || t.startsWith("tan(")) return true
        if (t.startsWith("asin(") || t.startsWith("acos(") || t.startsWith("atan(")) return true
        if (t.startsWith("sinh(") || t.startsWith("cosh(") || t.startsWith("tanh(")) return true
        if (t.startsWith("asinh(") || t.startsWith("acosh(") || t.startsWith("atanh(")) return true
        if (t.startsWith("sin_d(") || t.startsWith("cos_d(") || t.startsWith("tan_d(")) return true
        if (t.startsWith("asin_d(") || t.startsWith("acos_d(") || t.startsWith("atan_d(")) return true
        if (t.startsWith("ln(") || t.startsWith("log(") || t.startsWith("exp(")) return true
        if (t.startsWith("sqrt(") || t.startsWith("abs(")) return true
        if (t[0].isDigit() || t.startsWith(".")) return true
        if (t in CONST_TOKENS) return true
        if (t in VAR_TOKENS) return true
        if (t == "e") return true
        return false
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
                    "/" -> {
                        pos++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("除数不能为零")
                        r /= divisor
                    }
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
                t == "e" -> { pos++; exp(1.0) }
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
            SciGlassCard(Modifier.fillMaxWidth().weight(0.28f), display)
            SciGlassCard(Modifier.fillMaxWidth().weight(0.72f).padding(top = 12.dp), keypad)
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
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SciWheel1(state, onIntent, Modifier.fillMaxWidth())
            SciWheel2(onIntent, Modifier.fillMaxWidth())

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 控制行：MODE / Shift / ( / ) / Ans
                SciKeyRow(Modifier.weight(1f)) {
                    SciKey("MODE", SciKeyVariant.Mode) {
                        onIntent(ModuleIntent.Custom("sci:mode"))
                    }
                    SciKey("Shift", SciKeyVariant.Mode, highlighted = state.shiftMode) {
                        onIntent(ModuleIntent.Custom("sci:shift"))
                    }
                    SciKey("(", SciKeyVariant.Func) { onIntent(ModuleIntent.Input("(")) }
                    SciKey(")", SciKeyVariant.Func) { onIntent(ModuleIntent.Input(")")) }
                    SciKey("Ans", SciKeyVariant.Memory) { onIntent(ModuleIntent.Input("ans")) }
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
                    SciClearKey(
                        onShortTap = { onIntent(ModuleIntent.Backspace) },
                        onLongPress = { onIntent(ModuleIntent.Clear) },
                    )
                }

                SciKeyRow(Modifier.weight(1f)) {
                    SciKey(if (state.shiftMode) "00" else "0") {
                        val token = if (state.shiftMode) "00" else "0"
                        onIntent(ModuleIntent.Input(token))
                    }
                    SciKey(".") { onIntent(ModuleIntent.Input(".")) }
                    SciKey("±", SciKeyVariant.Func) { onIntent(ModuleIntent.Input("-")) }
                    SciKey("+", SciKeyVariant.Operator) { onIntent(ModuleIntent.Input("+")) }
                    SciKey("AC", SciKeyVariant.Clear) { onIntent(ModuleIntent.Clear) }
                }
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
            SciWheelButton(
                label = displayLabel,
                fgColor = fg,
                onClick = { onIntent(ModuleIntent.Input(sendToken)) },
            )
        }
    }
}

// ============================================================
//  Wheel 2: Variables & Constants
// ============================================================

private data class SciVarItem(val label: String, val token: String, val isConst: Boolean = false)

private val SCI_VAR_ITEMS = listOf(
    SciVarItem("π", "pi", isConst = true),
    SciVarItem("e", "e", isConst = true),
    SciVarItem("φ", "phi", isConst = true),
    SciVarItem("g", "9.80665", isConst = true),
    SciVarItem("A", "A"), SciVarItem("B", "B"),
    SciVarItem("C", "C"), SciVarItem("D", "D"),
    SciVarItem("X", "X"), SciVarItem("Y", "Y"),
    SciVarItem("M", "M"),
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
            SciWheelButton(
                label = item.label,
                fgColor = fg,
                onClick = { onIntent(ModuleIntent.Input(item.token)) },
            )
        }
    }
}

// ============================================================
//  Wheel Button (matches main key style)
// ============================================================

@Composable
private fun SciWheelButton(
    label: String,
    fgColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.94f else 1f,
        androidx.compose.animation.core.tween(durationMillis = 150),
        label = "sciWheelBtnScale",
    )
    val dark = isDarkTheme()
    val density = LocalDensity.current
    val shape = RoundedCornerShape(14.dp)

    // 背景：深色 idle 完全透明；浅色保留淡玻璃底
    val bgColor = when {
        pressed && dark -> fgColor.copy(alpha = 0.18f)
        pressed -> fgColor.copy(alpha = 0.28f)
        dark -> Color.Transparent
        else -> Color.White.copy(alpha = 0.10f)
    }
    // 边框：与主键盘一致，深色 1.2dp，浅色 1dp，颜色用前景色
    val borderColor = fgColor.copy(alpha = if (dark) 0.55f else 0.65f)
    val borderWidth = if (dark) 1.2.dp else 1.dp

    Box(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 62.dp)
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
            .border(borderWidth, borderColor, shape)
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
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = fgColor,
            textAlign = TextAlign.Center,
        )
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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

    // 液态玻璃马卡龙配色：
    // 运算符/等号-琥珀橙 · 功能键-青绿 · 三角-亮紫 · 内存键-靛蓝 · 清除-玫红 · 常量-金黄
    val opColor = Color(0xFFF5A623)
    val funcColor = Color(0xFF0FA47F)
    val trigColor = Color(0xFFB54BE0)
    val clearColor = Color(0xFFFF5F5F)
    val memColor = Color(0xFF4F6BED)
    val constColor = Color(0xFFF59E0B)

    val (bgColor, borderColor, fgColor) = when (variant) {
        SciKeyVariant.Equal -> Triple(
            opColor.copy(alpha = 1f),
            opColor.copy(alpha = 0.85f),
            Color.White,
        )
        SciKeyVariant.Clear -> Triple(
            clearColor.copy(alpha = if (pressed) (if (dark) 0.40f else 0.55f) else (if (dark) 0.20f else 0.25f)),
            clearColor.copy(alpha = if (dark) 0.50f else 0.60f),
            if (dark) clearColor else Color(0xFFE5484D),
        )
        SciKeyVariant.Operator -> Triple(
            opColor.copy(alpha = if (pressed) (if (dark) 0.35f else 0.50f) else (if (dark) 0.18f else 0.22f)),
            opColor.copy(alpha = if (dark) 0.40f else 0.50f),
            if (dark) opColor.copy(alpha = 0.95f) else Color(0xFFC07D0A),
        )
        SciKeyVariant.Func -> Triple(
            funcColor.copy(alpha = if (pressed) (if (dark) 0.25f else 0.35f) else (if (dark) 0.08f else 0.12f)),
            funcColor.copy(alpha = if (dark) 0.40f else 0.50f),
            if (dark) funcColor.copy(alpha = 0.95f) else Color(0xFF0B7A5E),
        )
        SciKeyVariant.Trig -> Triple(
            trigColor.copy(alpha = if (pressed) (if (dark) 0.25f else 0.35f) else (if (dark) 0.08f else 0.12f)),
            trigColor.copy(alpha = if (dark) 0.40f else 0.50f),
            if (dark) trigColor.copy(alpha = 0.95f) else Color(0xFF8E2BAE),
        )
        SciKeyVariant.Mode -> Triple(
            primary.copy(alpha = if (highlighted) (if (dark) 0.35f else 0.50f) else (if (pressed) (if (dark) 0.25f else 0.35f) else (if (dark) 0.10f else 0.15f))),
            primary.copy(alpha = if (highlighted) 0.70f else (if (dark) 0.35f else 0.45f)),
            if (highlighted) primary else primary.copy(alpha = 0.90f),
        )
        SciKeyVariant.Memory -> Triple(
            memColor.copy(alpha = if (pressed) (if (dark) 0.25f else 0.35f) else (if (dark) 0.08f else 0.12f)),
            memColor.copy(alpha = if (dark) 0.40f else 0.50f),
            if (dark) memColor.copy(alpha = 0.95f) else Color(0xFF3E5AD9),
        )
        SciKeyVariant.Const -> Triple(
            constColor.copy(alpha = if (pressed) (if (dark) 0.25f else 0.35f) else (if (dark) 0.08f else 0.12f)),
            constColor.copy(alpha = if (dark) 0.40f else 0.50f),
            if (dark) constColor.copy(alpha = 0.95f) else Color(0xFFC07D0A),
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
        label.length >= 4 -> 12.sp
        label.length >= 3 -> 13.sp
        label.length == 2 -> 15.sp
        else -> 18.sp
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
    val clearColor = if (dark) Color(0xFFFF5F5F) else Color(0xFFE5484D)

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
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = clearColor,
            textAlign = TextAlign.Center,
        )
    }
}
