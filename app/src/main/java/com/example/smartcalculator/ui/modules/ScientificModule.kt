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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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
    val cursorPos: Int = 0,
    val cursorInBase: Boolean = false,
    val radianMode: Boolean = true,
    val shiftMode: Boolean = false,
    val justEvaluated: Boolean = false,
    val errorMsg: String? = null,
    val variables: Map<String, Double> = emptyMap(),
    val ans: Double = 0.0,
    val storeMode: Boolean = false,
    val storePreview: String? = null,
) : ModuleState

// 合法的存储变量名（用户可 STO 的字母）
private val STORE_VAR_TOKENS = setOf("A", "B", "C", "D", "X", "Y", "M")

// ============================================================
//  Token Lists for Backspace (longest-match first)
// ============================================================

// 所有不可分割的 token（按长度降序排列）
private val ATOMIC_TOKENS: List<String> = listOf(
    "log_10(", "log_2(", "log_e(",
    "10^(",
    "asinh(", "acosh(", "atanh(",
    "sinh(", "cosh(", "tanh(", "asin(", "acos(", "atan(",
    "sin(", "cos(", "tan(", "ln(", "exp(", "abs(",
    "sqrt(",
    "sqrt2", "phi", "pi", "ans",
)

// log 函数的识别前缀（用于动态匹配 log_N( 格式）
private val LOG_BASE_PREFIX = "log_"

// 解析表达式为 token 列表，每个 token 记录其字符范围
private data class DisplayToken(val text: String, val start: Int, val end: Int)

private fun parseToDisplayTokens(expr: String): List<DisplayToken> {
    if (expr.isEmpty()) return emptyList()
    val tokens = mutableListOf<DisplayToken>()
    var i = 0
    while (i < expr.length) {
        // 尝试匹配 log_N( 格式（动态底数）
        if (i + 4 < expr.length && expr.substring(i, i + 4) == LOG_BASE_PREFIX) {
            var j = i + 4
            while (j < expr.length && (expr[j].isDigit() || expr[j] == '.' || expr[j].isLetter())) j++
            if (j < expr.length && expr[j] == '(') {
                val fullToken = expr.substring(i, j + 1)
                tokens.add(DisplayToken(fullToken, i, j + 1))
                i = j + 1
                continue
            }
        }
        // 尝试匹配多字符 token（最长匹配优先）
        var matched = false
        for (tok in ATOMIC_TOKENS) {
            if (i + tok.length <= expr.length && expr.substring(i, i + tok.length) == tok) {
                tokens.add(DisplayToken(tok, i, i + tok.length))
                i += tok.length
                matched = true
                break
            }
        }
        if (!matched) {
            tokens.add(DisplayToken(expr[i].toString(), i, i + 1))
            i++
        }
    }
    return tokens
}

// 将字符位置对齐到最近的 token 边界
private fun snapToTokenBoundary(charPos: Int, tokens: List<DisplayToken>, preferAfter: Boolean = false): Int {
    if (tokens.isEmpty()) return 0
    val pos = charPos.coerceIn(0, tokens.last().end)
    // 如果点击位置在某个 token 内部，对齐到最近的边界
    for (token in tokens) {
        if (pos > token.start && pos < token.end) {
            val distToStart = pos - token.start
            val distToEnd = token.end - pos
            return if (preferAfter) token.end else if (distToStart < distToEnd) token.start else token.end
        }
    }
    return pos
}

// ============================================================
//  Reducer
// ============================================================

internal fun reduceScientific(
    state: ScientificModuleState,
    intent: ModuleIntent,
): ScientificModuleState = when (intent) {
    is ModuleIntent.Input -> state.inputToken(intent.value)
    is ModuleIntent.Evaluate -> state.evaluate().copy(storeMode = false, storePreview = null)
    is ModuleIntent.Clear -> ScientificModuleState()
    is ModuleIntent.Backspace -> state.backspaceToken().copy(storeMode = false, storePreview = null)
    is ModuleIntent.Custom -> when (intent.key) {
        "sci:mode" -> state.copy(radianMode = !state.radianMode, storePreview = null)
        "sci:shift" -> state.copy(shiftMode = !state.shiftMode, storeMode = false, storePreview = null)
        "sci:insertLog" -> state.insertLog().copy(storePreview = null)
        "sci:moveCursor" -> {
            val pos = (intent.payload as? Int) ?: state.expression.length
            state.copy(cursorPos = pos, cursorInBase = state.isCursorInBase(pos))
        }
        "sci:store" -> {
            // 进入 STO 等待模式：显示 "值 ➔ ?" 提示用户选择变量
            val v = state.display.toDoubleOrNull()
            if (v == null) state.copy(errorMsg = "无可存储的数值", storeMode = false, storePreview = null)
            else state.copy(storeMode = true, storePreview = "➔ ?", errorMsg = null)
        }
        "sci:clearAllVars" -> {
            // CAV：一键清空所有变量
            state.copy(variables = emptyMap(), storeMode = false, storePreview = null, errorMsg = null)
        }
        else -> state
    }
}

private val OPERATOR_CHARS = setOf('+', '-', '*', '/', '^')
private val SINGLE_OPS = setOf("+", "-", "*", "/", "^", "!")

// ============ log 底数编辑支持 ============

private fun ScientificModuleState.insertLog(): ScientificModuleState {
    val defaultLog = "log_("
    val newExpr = if (expression.isEmpty() || justEvaluated) {
        defaultLog
    } else {
        val pos = cursorPos.coerceIn(0, expression.length)
        expression.substring(0, pos) + defaultLog + expression.substring(pos)
    }
    val newPos = if (expression.isEmpty() || justEvaluated) {
        4 // 光标在 _ 后面，( 前面
    } else {
        val pos = cursorPos.coerceIn(0, expression.length)
        pos + 4 // 光标在插入位置的 log_( 的 ( 前面
    }
    return copy(
        expression = newExpr,
        display = newExpr,
        cursorPos = newPos,
        cursorInBase = true,
        justEvaluated = false,
        errorMsg = null,
    )
}

private fun ScientificModuleState.isCursorInBase(pos: Int): Boolean {
    val tokens = parseToDisplayTokens(expression)
    for (token in tokens) {
        if (token.text.startsWith("log_") && token.text.endsWith("(")) {
            val baseStart = token.start + 4 // 跳过 "log_"
            val baseEnd = token.end - 1 // 跳过 "("
            if (pos in baseStart until baseEnd) return true
        }
    }
    return false
}

private fun ScientificModuleState.updateLogBase(newBase: String): ScientificModuleState {
    val tokens = parseToDisplayTokens(expression)
    var expr = expression
    var offset = 0
    for (token in tokens) {
        if (token.text.startsWith("log_") && token.text.endsWith("(")) {
            val baseStart = token.start + 4 + offset // "+4" 跳过 "log_"
            val baseEnd = token.end - 1 + offset // "-1" 跳过 "("
            val before = expr.substring(0, baseStart)
            val after = expr.substring(baseEnd)
            val replaced = before + newBase + after
            val newCursor = before.length + newBase.length
            return copy(
                expression = replaced,
                display = replaced,
                cursorPos = newCursor,
                cursorInBase = true,
                errorMsg = null,
            )
        }
    }
    return this
}

// ============ 通用输入逻辑 ============

private fun ScientificModuleState.inputToken(token: String): ScientificModuleState {
    // STO 模式下的处理：用户选择存储目标变量
    if (storeMode) {
        return when {
            token in STORE_VAR_TOKENS -> {
                val v = display.toDoubleOrNull()
                if (v == null) copy(storeMode = false, storePreview = null, errorMsg = "无可存储的数值")
                else copy(
                    variables = variables + (token to v),
                    storeMode = false,
                    storePreview = "➔ $token",
                    errorMsg = null,
                )
            }
            else -> {
                // STO 模式下按了非变量键：取消 STO 模式，并把该 token 当作正常输入
                copy(storeMode = false, storePreview = null).inputToken(token)
            }
        }
    }

    // 已显示 "➔ X"（STO 完成后的预览态）：下一次输入清掉 preview，恢复正常
    if (storePreview != null) {
        return copy(storePreview = null).inputToken(token)
    }

    if (errorMsg != null) {
        return copy(errorMsg = null, display = token, expression = token, cursorPos = token.length, justEvaluated = false, cursorInBase = false)
    }

    // 如果刚刚计算完，且不是 log 插入，替换整个表达式
    if (justEvaluated) {
        if (token == "log") return insertLog()
        return copy(
            expression = token,
            display = token,
            cursorPos = token.length,
            cursorInBase = false,
            justEvaluated = false,
            errorMsg = null,
        )
    }

    // 在 log 底数区域输入
    if (cursorInBase) {
        return handleLogBaseInput(token)
    }

    // 特殊按键：log → 插入 log_10( 
    if (token == "log") {
        return insertLog()
    }

    // 特殊按键：( → 如果前面是 log_XXX(，切换到参数区
    if (token == "(" && cursorPos > 0) {
        val beforeChar = expression[cursorPos - 1]
        if (beforeChar == '(' && cursorInBase) {
            // 已在 log 的参数区
        }
    }

    var expr = expression
    var pos = cursorPos.coerceIn(0, expr.length)

    // 容错 1：运算符替换
    if (token.length == 1 && token[0] in OPERATOR_CHARS && pos > 0) {
        val charBefore = expr[pos - 1]
        if (charBefore in OPERATOR_CHARS || charBefore == '!') {
            expr = expr.removeRange(pos - 1, pos)
            pos -= 1
            if (pos > 0 && expr[pos - 1] in OPERATOR_CHARS) {
                expr = expr.removeRange(pos - 1, pos)
                pos -= 1
            }
            val newExpr = expr.substring(0, pos) + token + expr.substring(pos)
            val newPos = pos + token.length
            return copy(
                expression = newExpr,
                display = newExpr,
                cursorPos = newPos,
                cursorInBase = false,
                justEvaluated = false,
                errorMsg = null,
            )
        }
    }

    // 容错 2：小数点防重复
    if (token == "." && pos > 0) {
        val beforeCursor = expr.substring(0, pos)
        val lastSegment = beforeCursor.split(Regex("[+\\-*/^()!]")).lastOrNull() ?: ""
        if ('.' in lastSegment) return this
    }

    // 容错 3：避免在空表达式时输入运算符
    if (expr.isEmpty() && token.length == 1 && token[0] in setOf('+', '*', '/', '^')) {
        return this
    }

    // 容错 5：插入 10^( 前，如果前面是数字/常量/变量/右括号，自动补 * 隐式乘号
    // （避免 "3" + "10^(" → 变成 "310^(" 导致 310^x 而不是 3×10ˣ）
    if (token == "10^(" && pos > 0) {
        val charBefore = expr[pos - 1]
        val needImplicitMul = charBefore.isDigit() || charBefore == '.' ||
                charBefore == ')' || charBefore == '!' ||
                charBefore.isLetter() // 包括常量 pi/e/phi/ans 和变量 A/B/C/...
        if (needImplicitMul) {
            val inserted = "*" + token
            val newExpr = expr.substring(0, pos) + inserted + expr.substring(pos)
            val newPos = pos + inserted.length
            return copy(
                expression = newExpr,
                display = newExpr,
                cursorPos = newPos,
                cursorInBase = false,
                justEvaluated = false,
                errorMsg = null,
            )
        }
    }

    // 容错 4：输入 ( 时，如果光标前是 log_XXX，切换到参数区
    if (token == "(" && pos > 4) {
        val prefix = expr.substring((pos - 5).coerceAtLeast(0), pos)
        if (prefix.startsWith("log_") && prefix.endsWith("(")) {
            // 这是 log 的 (，正常插入
        }
    }

    val newExpr = expr.substring(0, pos) + token + expr.substring(pos)
    val newPos = pos + token.length
    // 检测新光标位置是否在 log 底数区域
    val newCursorInBase = isCursorInBase(newPos)
    return copy(
        expression = newExpr,
        display = newExpr,
        cursorPos = newPos,
        cursorInBase = newCursorInBase,
        justEvaluated = false,
        errorMsg = null,
    )
}

// 处理 log 底数区域的输入
private fun ScientificModuleState.handleLogBaseInput(token: String): ScientificModuleState {
    val tokens = parseToDisplayTokens(expression)
    for (tk in tokens) {
        if (tk.text.startsWith("log_") && tk.text.endsWith("(")) {
            val baseStart = tk.start + 4
            val baseEnd = tk.end - 1
            if (cursorPos in baseStart..baseEnd) {
                val currentBase = expression.substring(baseStart, baseEnd)
                val isEmptyBase = currentBase.isEmpty()
                return when {
                    // 输入数字/字母：替换或追加底数
                    token.all { it.isDigit() || it == '.' || it.isLetter() } -> {
                        updateLogBase(currentBase + token)
                    }
                    // 底数为空时输入 ( 或运算符：自动补充默认底数 10
                    (token == "(" || (token.length == 1 && token[0] in setOf('+', '-', '*', '/', '^'))) && isEmptyBase -> {
                        val updatedBase = updateLogBase("10")
                        val newExpr = updatedBase.expression.substring(0, updatedBase.cursorPos) + token + updatedBase.expression.substring(updatedBase.cursorPos)
                        copy(
                            expression = newExpr,
                            display = newExpr,
                            cursorPos = updatedBase.cursorPos + token.length,
                            cursorInBase = false,
                            justEvaluated = false,
                            errorMsg = null,
                        )
                    }
                    // 输入 ( ：退出底数编辑，光标进入参数区
                    token == "(" -> {
                        val newExpr = expression.substring(0, cursorPos) + token + expression.substring(cursorPos)
                        copy(
                            expression = newExpr,
                            display = newExpr,
                            cursorPos = cursorPos + token.length,
                            cursorInBase = false,
                            justEvaluated = false,
                            errorMsg = null,
                        )
                    }
                    // 输入运算符：退出底数编辑
                    token.length == 1 && token[0] in setOf('+', '-', '*', '/', '^') -> {
                        val newExpr = expression.substring(0, cursorPos) + token + expression.substring(cursorPos)
                        copy(
                            expression = newExpr,
                            display = newExpr,
                            cursorPos = cursorPos + token.length,
                            cursorInBase = false,
                            justEvaluated = false,
                            errorMsg = null,
                        )
                    }
                    // 其他情况：退出底数编辑
                    else -> {
                        val newExpr = expression.substring(0, cursorPos) + token + expression.substring(cursorPos)
                        copy(
                            expression = newExpr,
                            display = newExpr,
                            cursorPos = cursorPos + token.length,
                            cursorInBase = false,
                            justEvaluated = false,
                            errorMsg = null,
                        )
                    }
                }
            }
        }
    }
    return this
}

private fun ScientificModuleState.backspaceToken(): ScientificModuleState {
    val pos = cursorPos.coerceIn(0, expression.length)
    if (pos == 0) return this

    // 如果在 log 底数区域，特殊处理
    if (cursorInBase) {
        val tokens = parseToDisplayTokens(expression)
        for (token in tokens) {
            if (token.text.startsWith("log_") && token.text.endsWith("(")) {
                val baseStart = token.start + 4
                val baseEnd = token.end - 1
                if (pos in baseStart..baseEnd) {
                    val currentBase = expression.substring(baseStart, baseEnd)
                    // 底数为空，删除整个 log_( token
                    if (currentBase.isEmpty()) {
                        val newExpr = expression.substring(0, token.start) + expression.substring(token.end)
                        val newDisplay = if (newExpr.isEmpty()) "0" else newExpr
                        return copy(expression = newExpr, display = newDisplay, cursorPos = token.start, cursorInBase = false, errorMsg = null)
                    }
                    // 删除最后一位
                    val newBase = currentBase.dropLast(1)
                    val newState = updateLogBase(newBase)
                    return newState.copy(cursorPos = newState.cursorPos, cursorInBase = true)
                }
            }
        }
    }

    val exprBefore = expression.substring(0, pos)
    val exprAfter = expression.substring(pos)

    // 检查光标前是否有完整 token
    for (tok in ATOMIC_TOKENS) {
        if (exprBefore.endsWith(tok)) {
            val newExpr = exprBefore.dropLast(tok.length) + exprAfter
            val newPos = pos - tok.length
            val newDisplay = if (newExpr.isEmpty()) "0" else newExpr
            return copy(expression = newExpr, display = newDisplay, cursorPos = newPos, cursorInBase = false, errorMsg = null)
        }
    }

    // 否则删除光标前的一个字符
    val newExpr = exprBefore.dropLast(1) + exprAfter
    val newPos = (pos - 1).coerceAtLeast(0)
    val newDisplay = if (newExpr.isEmpty()) "0" else newExpr
    val newCursorInBase = isCursorInBase(newPos)
    return copy(expression = newExpr, display = newDisplay, cursorPos = newPos, cursorInBase = newCursorInBase, errorMsg = null)
}

private fun dropLastSciToken(s: String): String {
    for (tok in ATOMIC_TOKENS) {
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
            cursorPos = expression.length,
            ans = result,
            justEvaluated = true,
            errorMsg = null,
        )
    } catch (e: Exception) {
        copy(errorMsg = e.message ?: "计算错误", display = "错误", cursorPos = expression.length, justEvaluated = true)
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
                // 特殊处理：log_N( 格式（如 log_10(、log_2(、log_e(）
                i + 4 < input.length && input.substring(i, i + 4) == "log_" -> {
                    var j = i + 4
                    while (j < input.length && (input[j].isDigit() || input[j] == '.' || input[j].isLetter())) j++
                    if (j < input.length && input[j] == '(') {
                        val fullToken = input.substring(i, j + 1)
                        tokens.add(fullToken)
                        i = j + 1
                    } else {
                        throw IllegalArgumentException("log 函数格式错误，应为 log_底数(...)")
                    }
                }
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
                    val hasParen = i < input.length && input[i] == '('
                    when {
                        word == "sqrt2" -> { tokens.add("sqrt2") }
                        hasParen -> {
                            val fullToken = word + "("
                            when (fullToken) {
                                "sin(", "cos(", "tan(", "asin(", "acos(", "atan(",
                                "sinh(", "cosh(", "tanh(", "asinh(", "acosh(", "atanh(",
                                "sin_d(", "cos_d(", "tan_d(", "asin_d(", "acos_d(", "atan_d(",
                                "ln(", "log(", "exp(", "sqrt(", "abs(" -> {
                                    tokens.add(fullToken)
                                    i++
                                }
                                else -> {
                                    tokens.add(originalWord)
                                }
                            }
                        }
                        word == "pi" -> { tokens.add("pi") }
                        word == "phi" -> { tokens.add("phi") }
                        word == "ans" -> { tokens.add("ans") }
                        originalWord.length == 1 && originalWord in VAR_TOKENS -> {
                            tokens.add(originalWord)
                        }
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
        if (t.startsWith("log_")) return true
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
            // 不替换 log_ 开头的（它们用 _ 分隔底数）
            s = s.replace(Regex("""(?<!_)sin\("""), "sin_d(")
            s = s.replace(Regex("""(?<!_)cos\("""), "cos_d(")
            s = s.replace(Regex("""(?<!_)tan\("""), "tan_d(")
            s = s.replace(Regex("""(?<!_)asin\("""), "asin_d(")
            s = s.replace(Regex("""(?<!_)acos\("""), "acos_d(")
            s = s.replace(Regex("""(?<!_)atan\("""), "atan_d(")
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
                t.startsWith("log_") && t.endsWith("(") -> parseLogBase(t)
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

        private fun parseLogBase(token: String): Double {
            // token 格式: log_N( 或 log_(，其中 N 是底数，空则默认 10
            val baseStr = token.removePrefix("log_").removeSuffix("(")
            val base = when {
                baseStr.isEmpty() -> 10.0  // 默认常用对数 lg
                baseStr == "e" -> exp(1.0)
                baseStr == "pi" -> PI
                else -> baseStr.toDoubleOrNull()
                    ?: throw IllegalArgumentException("无法解析 log 底数: $baseStr")
            }
            if (base <= 0 || base == 1.0)
                throw IllegalArgumentException("log 底数必须大于 0 且不等于 1")
            pos++
            val arg = parseExpr()
            if (pos >= tokens.size || tokens[pos] != ")")
                throw IllegalArgumentException("${token.dropLast(1)} 缺少右括号")
            pos++
            // log_b(x) = ln(x) / ln(b)
            val lnBase = ln(base)
            if (abs(lnBase) < 1e-15) throw IllegalArgumentException("log 底数无效")
            return ln(arg) / lnBase
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
        display = { SciDisplay(state, onIntent) },
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
private fun BoxScope.SciDisplay(state: ScientificModuleState, onIntent: (ModuleIntent) -> Unit) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val cursorColor = MaterialTheme.colorScheme.primary

    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            cursorVisible = true
            kotlinx.coroutines.delay(500)
            cursorVisible = false
            kotlinx.coroutines.delay(500)
        }
    }
    val cursorAlpha = if (cursorVisible) 1f else 0f

    val tokens = parseToDisplayTokens(state.expression)
    val isJustEvaluated = state.justEvaluated

    val exprColor = when {
        state.errorMsg != null -> Color(0xFFFF3B30)
        isJustEvaluated -> LocalContentColor.current.copy(alpha = 0.45f)
        else -> LocalContentColor.current
    }
    val resultColor = when {
        state.errorMsg != null -> Color(0xFFFF3B30)
        isJustEvaluated -> MaterialTheme.colorScheme.primary
        else -> LocalContentColor.current.copy(alpha = 0.45f)
    }

    val exprStyle = MaterialTheme.typography.titleMedium.copy(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        color = exprColor,
    )
    val resultStyle = MaterialTheme.typography.headlineLarge.copy(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = resultColor,
    )

    LaunchedEffect(state.expression) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
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

        // 左上：用户输入表达式（token 级渲染 + 精确光标）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .pointerInput(state.expression, state.cursorPos, tokens) {
                    detectTapGestures { offset ->
                        val charPos = hitTestTokenAtPosition(
                            offset.x, tokens, exprStyle, density
                        )
                        val snappedPos = snapToTokenBoundary(charPos, tokens)
                        onIntent(ModuleIntent.Custom("sci:moveCursor", snappedPos))
                    }
                }
        ) {
            val cursorX = calculateCursorX(
                state.cursorPos, tokens, exprStyle, density
            )
            val cursorHeight = with(density) { exprStyle.fontSize.toPx() * 1.2f }
            val cursorWidth = with(density) { 2.dp.toPx() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .drawBehind {
                        val centerY = size.height / 2f
                        drawLine(
                            color = cursorColor.copy(alpha = cursorAlpha),
                            start = Offset(cursorX, centerY - cursorHeight / 2f),
                            end = Offset(cursorX, centerY + cursorHeight / 2f),
                            strokeWidth = cursorWidth,
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tokens.isEmpty()) {
                    Spacer(Modifier.width(1.dp))
                } else {
                    tokens.forEachIndexed { index, token ->
                        if (token.text.startsWith("log_") && token.text.endsWith("(")) {
                            val baseStr = token.text.substring(4, token.text.length - 1)
                            val subStyle = exprStyle.copy(
                                fontSize = exprStyle.fontSize * 0.65f
                            )
                            Text(
                                text = "log",
                                style = exprStyle,
                            )
                            if (baseStr.isEmpty()) {
                                // 空底数显示占位符，引导用户输入
                                Text(
                                    text = "_",
                                    style = subStyle,
                                    color = LocalContentColor.current.copy(alpha = 0.4f),
                                )
                            } else {
                                Text(
                                    text = baseStr,
                                    style = subStyle,
                                )
                            }
                            Text(
                                text = "(",
                                style = exprStyle,
                            )
                        } else {
                            Text(
                                text = token.text,
                                style = exprStyle,
                            )
                        }
                    }
                }
            }
        }

        // 右下：计算结果（有 storePreview 时显示 "值 ➔ 变量" 的形式）
        val preview = state.storePreview
        if (state.errorMsg != null) {
            Text(
                text = state.errorMsg,
                style = resultStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        } else if (preview != null) {
            val arrowColor = MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.display,
                    style = resultStyle,
                    textAlign = TextAlign.End,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = preview,
                    style = resultStyle.copy(
                        color = if (preview.endsWith("?") || preview == "➔ ?") {
                            arrowColor.copy(alpha = 0.85f)
                        } else {
                            arrowColor
                        }
                    ),
                    textAlign = TextAlign.End,
                )
            }
        } else {
            Text(
                text = state.display,
                style = resultStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
    }
}

// 计算光标 x 坐标（基于 token 宽度累加，支持 log 下标渲染）
private fun calculateCursorX(
    cursorPos: Int,
    tokens: List<DisplayToken>,
    style: TextStyle,
    density: Density
): Float {
    if (tokens.isEmpty()) return 0f
    val targetPos = cursorPos.coerceIn(0, tokens.last().end)
    var x = 0f
    for (token in tokens) {
        val tokenEnd = token.end
        if (token.text.startsWith("log_") && token.text.endsWith("(")) {
            val baseStr = token.text.substring(4, token.text.length - 1)
            val baseLen = baseStr.length
            val displayBase = if (baseStr.isEmpty()) "_" else baseStr
            val subStyle = style.copy(fontSize = style.fontSize * 0.65f)
            val logWidth = estimateTextWidth("log", style, density)
            val baseWidth = estimateTextWidth(displayBase, subStyle, density)
            val parenWidth = estimateTextWidth("(", style, density)
            val fullLogWidth = logWidth + baseWidth + parenWidth

            if (tokenEnd <= targetPos) {
                x += fullLogWidth
            } else {
                val relPos = (targetPos - token.start).coerceIn(0, token.text.length)
                when {
                    relPos <= 4 -> x += estimateTextWidth(token.text.substring(0, relPos), style, density)
                    relPos <= 4 + baseLen -> {
                        if (baseLen == 0) {
                            // 空底数，光标在底数区域
                            x += logWidth + baseWidth
                        } else {
                            val subPos = relPos - 4
                            x += logWidth + estimateTextWidth(baseStr.substring(0, subPos.coerceIn(0, baseLen)), subStyle, density)
                        }
                    }
                    else -> {
                        val parenPos = relPos - 4 - baseLen
                        x += logWidth + baseWidth + estimateTextWidth("(".substring(0, parenPos.coerceIn(0, 1)), style, density)
                    }
                }
                return x
            }
        } else {
            val tokenWidth = estimateTextWidth(token.text, style, density)
            if (tokenEnd <= targetPos) {
                x += tokenWidth
            } else {
                val relativePos = (targetPos - token.start).coerceIn(0, token.text.length)
                x += estimateTextWidth(token.text.substring(0, relativePos), style, density)
                return x
            }
        }
    }
    return x
}

// 估算文本宽度（等宽字体近似）
private fun estimateTextWidth(text: String, style: TextStyle, density: Density): Float {
    val fontSizePx = with(density) { style.fontSize.toPx() }
    val charWidth = fontSizePx * 0.6f
    return text.length * charWidth
}

// 点击位置命中测试：找到最接近点击 x 的字符位置（支持 log 下标）
private fun hitTestTokenAtPosition(
    clickX: Float,
    tokens: List<DisplayToken>,
    style: TextStyle,
    density: Density
): Int {
    if (tokens.isEmpty()) return 0
    var x = 0f
    for (token in tokens) {
        if (token.text.startsWith("log_") && token.text.endsWith("(")) {
            val baseStr = token.text.substring(4, token.text.length - 1)
            val baseLen = baseStr.length
            val displayBase = if (baseStr.isEmpty()) "_" else baseStr
            val subStyle = style.copy(fontSize = style.fontSize * 0.65f)
            val logWidth = estimateTextWidth("log", style, density)
            val baseWidth = estimateTextWidth(displayBase, subStyle, density)
            val parenWidth = estimateTextWidth("(", style, density)
            val fullWidth = logWidth + baseWidth + parenWidth

            val logStartX = x
            val logEndX = x + logWidth
            val baseStartX = logEndX
            val baseEndX = logEndX + baseWidth
            val parenStartX = baseEndX
            val parenEndX = baseEndX + parenWidth

            when {
                clickX <= logEndX -> {
                    if (clickX <= (logStartX + logEndX) / 2f) return token.start
                    else return token.start + 4
                }
                clickX <= baseEndX -> {
                    if (baseLen == 0) {
                        // 空底数，点击在底数区域中间
                        return token.start + 4
                    }
                    val relX = (clickX - baseStartX).coerceIn(0f, baseWidth)
                    val charWidth = baseWidth / baseLen
                    val charIndex = (relX / charWidth).toInt().coerceIn(0, baseLen - 1)
                    return token.start + 4 + charIndex + 1
                }
                clickX <= parenEndX -> {
                    return token.start + 4 + baseLen
                }
                else -> {
                    x += fullWidth
                }
            }
        } else {
            val tokenWidth = estimateTextWidth(token.text, style, density)
            val tokenCenter = x + tokenWidth / 2f
            if (clickX <= tokenCenter) {
                return token.start
            }
            if (clickX <= x + tokenWidth) {
                return token.end
            }
            x += tokenWidth
        }
    }
    return tokens.last().end
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
            SciWheel2(state, onIntent, Modifier.fillMaxWidth())

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
                    SciKey(if (state.shiftMode) "CAV" else "STO", SciKeyVariant.Memory) {
                        if (state.shiftMode) {
                            onIntent(ModuleIntent.Custom("sci:clearAllVars"))
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
    SciFuncItem("10ˣ", "10^(", null, null),
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
                onClick = {
                    if (!shiftMode && item.label == "log") {
                        onIntent(ModuleIntent.Custom("sci:insertLog"))
                    } else {
                        onIntent(ModuleIntent.Input(sendToken))
                    }
                },
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
private fun SciWheel2(state: ScientificModuleState, onIntent: (ModuleIntent) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val dark = isDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(vertical = 2.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SCI_VAR_ITEMS.forEach { item ->
            // STO 模式下：常量禁用（不高亮），可存储变量高亮
            val isStoreable = item.token in STORE_VAR_TOKENS
            val highlight = state.storeMode && isStoreable
            val fg = when {
                highlight && dark -> Color(0xFFFFD54F)
                highlight -> Color(0xFFE65100)
                dark && item.isConst -> Color(0xFFFFB74D)
                dark -> Color(0xFFF48FB1)
                item.isConst -> Color(0xFFE65100)
                else -> Color(0xFFAD1457)
            }
            SciWheelButton(
                label = item.label,
                fgColor = fg,
                highlight = highlight,
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
    highlight: Boolean = false,
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

    // 背景：深色 idle 完全透明；浅色保留淡玻璃底；高亮时有明显底色
    val bgColor = when {
        pressed && dark -> fgColor.copy(alpha = 0.18f)
        pressed -> fgColor.copy(alpha = 0.28f)
        highlight && dark -> fgColor.copy(alpha = 0.22f)
        highlight -> fgColor.copy(alpha = 0.18f)
        dark -> Color.Transparent
        else -> Color.White.copy(alpha = 0.10f)
    }
    // 边框：高亮时加粗、颜色更实
    val borderColor = fgColor.copy(
        alpha = when {
            highlight && dark -> 0.95f
            highlight -> 0.90f
            dark -> 0.55f
            else -> 0.65f
        }
    )
    val borderWidth = when {
        highlight -> if (dark) 2.2.dp else 2.dp
        dark -> 1.2.dp
        else -> 1.dp
    }

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
