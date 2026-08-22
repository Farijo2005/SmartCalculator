package com.example.smartcalculator.calc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.graphics.Color
import com.example.smartcalculator.ui.theme.ThemeMode
import com.example.smartcalculator.ui.theme.ThemeColorPresets
import com.example.smartcalculator.ui.theme.parseThemeColor
import com.example.smartcalculator.ui.theme.serializeThemeColor
import com.example.smartcalculator.ui.modules.ModuleIntent
import com.example.smartcalculator.ui.modules.ModuleState
import com.example.smartcalculator.ui.modules.StandardModuleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 精简版 ViewModel：只保留模式切换、历史记录、菜单顺序。
 * （计算器计算功能将在后续步骤中逐步添加）
 */
class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, 0)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    // ===== 模式切换 =====
    fun setMode(mode: CalcMode) {
        _uiState.update { it.copy(mode = mode) }
        persist()
    }

    // ===== 主题模式 =====
    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        persist()
    }

    // ===== 主题色 =====
    fun setThemeColor(color: Color) {
        _uiState.update { it.copy(themeColor = color) }
        persist()
    }

    // ===== 历史记录 =====
    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
        persist()
    }

    // ===== 模块意图分发 =====
    /**
     * 各模块按键意图统一入口。
     * 每人加自己模式的 reducer 分支，Git 不冲突。
     */
    fun dispatchModuleIntent(mode: CalcMode, intent: com.example.smartcalculator.ui.modules.ModuleIntent) {
        _uiState.update { s ->
            val states = s.moduleStates.toMutableMap()
            when (mode) {
                CalcMode.EquationSolver -> {
                    val old = states[mode] as? com.example.smartcalculator.ui.modules.EquationSolverModuleState
                        ?: com.example.smartcalculator.ui.modules.EquationSolverModuleState()
                    val newState = com.example.smartcalculator.ui.modules.reduceEquationSolver(old, intent)
                    states[mode] = newState

                    // Evaluate 成功时写入一条历史
                    if (intent is com.example.smartcalculator.ui.modules.ModuleIntent.Evaluate &&
                        newState.results.isNotEmpty() && newState.errorMsg == null
                    ) {
                        val expr = when (newState.subType) {
                            com.example.smartcalculator.ui.modules.SUB_POLY ->
                                newState.polyCoeffs.joinToString(", ") + " = 0"
                            else -> buildString {
                                // 多元一次 5×6 网格：和 evaluateSafe 相同逻辑（含"鸡兔同笼人性化移项"）生成人类可读方程
                                val flat = newState.linearFlat
                                val rows = 5; val aCols = 5; val cols = 6
                                fun isFilled(s: String): Boolean {
                                    val t = s.trim(); return t.isNotEmpty() && t != "-" && t != "." && t != "/"
                                }
                                val filledMat = flat.map(::isFilled)
                                data class RowSpec(
                                    val rowIdx: Int,
                                    val a: DoubleArray,
                                    val b: Double,
                                    val maxACol: Int,
                                )
                                val rowsInfo = (0 until rows).mapNotNull { r ->
                                    val bIdx = r * cols + aCols
                                    val bFilled = filledMat[bIdx]
                                    var lastACol = -1
                                    for (c in (aCols - 1) downTo 0) {
                                        if (filledMat[r * cols + c]) { lastACol = c; break }
                                    }
                                    if (lastACol < 0) return@mapNotNull null
                                    val coeffsRaw = DoubleArray(aCols) { c ->
                                        com.example.smartcalculator.ui.modules.parseCellToDouble(flat[r * cols + c])
                                    }
                                    val bRaw: Double
                                    val effMaxA: Int
                                    if (bFilled) {
                                        bRaw = com.example.smartcalculator.ui.modules.parseCellToDouble(flat[bIdx])
                                        effMaxA = lastACol
                                    } else {
                                        // 人性化：常数在左边 → 自动移到右边变号
                                        val constVal = coeffsRaw[lastACol]
                                        coeffsRaw[lastACol] = 0.0
                                        bRaw = -constVal
                                        effMaxA = (lastACol - 1 downTo 0).firstOrNull { c ->
                                            kotlin.math.abs(coeffsRaw[c]) > 1e-12
                                        } ?: -1
                                    }
                                    RowSpec(r, coeffsRaw, bRaw, effMaxA)
                                }
                                if (rowsInfo.isEmpty()) { append("(空增广矩阵)") }
                                else {
                                    val N = (rowsInfo.maxOf { it.maxACol } + 1).coerceAtLeast(1)
                                    val vars = "xyzuvw".toCharArray().take(N)
                                    val fmt = { d: Double ->
                                        val rounded = kotlin.math.round(d * 1e9) / 1e9   // 四舍五入到 1e-9（防 "-0" 等）
                                        val long = rounded.toLong()
                                        if (kotlin.math.abs(rounded - long) < 1e-9) long.toString()
                                        else "%.6f".format(rounded).trimEnd('0').trimEnd('.')
                                    }
                                    rowsInfo.forEach { ri ->
                                        append(vars.mapIndexed { i, vn ->
                                            val v = ri.a[i]
                                            when {
                                                kotlin.math.abs(v) < 1e-12 -> null
                                                i == 0 -> "(${fmt(v)})$vn"
                                                v < 0 -> " - (${fmt(-v)})$vn"
                                                else -> " + (${fmt(v)})$vn"
                                            }
                                        }.filterNotNull().joinToString("").ifEmpty { "0" })
                                        append(" = ${fmt(ri.b)} ; ")
                                    }
                                }
                            }
                        }
                        s.copy(
                            moduleStates = states,
                            history = listOf(
                                HistoryItem(
                                    expression = expr,
                                    result = newState.results.joinToString(" ; ")
                                )
                            ) + s.history
                        )
                    } else s.copy(moduleStates = states)
                }
                CalcMode.Programmer -> {
                    val old = states[mode] as? com.example.smartcalculator.ui.modules.ProgrammerModuleState
                        ?: com.example.smartcalculator.ui.modules.ProgrammerModuleState()
                    val newState = com.example.smartcalculator.ui.modules.reduceProgrammer(old, intent)
                    states[mode] = newState
                    s.copy(moduleStates = states)
                }
                else -> s
            }
        }
    }

    // ===== 菜单排序 =====
    fun moveMenuMode(from: Int, to: Int) {
        _uiState.update { s ->
            val order = s.menuOrder.toMutableList()
            if (from !in order.indices || to !in order.indices) return@update s
            val item = order.removeAt(from)
            order.add(to, item)
            s.copy(menuOrder = order)
        }
        persist()
    }

    // ===== 模块意图分发入口 =====
    /**
     * 所有模块统一通过此入口发送 [ModuleIntent]。
     * 目前实现 [CalcMode.Standard] 的意图；其他模块接入时，在下面补充分支即可。
     */
    fun onModuleIntent(mode: CalcMode, intent: ModuleIntent) {
        when (mode) {
            CalcMode.Standard -> reduceStandard(intent)
            // 其他模块（解方程等）统一走 dispatchModuleIntent 按模块 reducer 分发
            else -> dispatchModuleIntent(mode, intent)
        }
    }

    // ===== 标准模块：私有 reducer 数据 =====
    // stdExpr：输入行原始字符串（唯一真相源，无空格，严格按键顺序，如 "9+7+7+7"）
    // stdOperandStart：当前正在输入的"数字片段"在 stdExpr 中的起始下标。
    //     用于 ± / % / ⌫ 等需要修改当前数字时，精准替换表达式尾部。
    // stdJustEvaluated：刚按 = 后为 true —— 下一次数字输入会重置表达式（开启新一轮）
    private val stdExpr = StringBuilder()
    private var stdOperandStart: Int = 0
    private var stdJustEvaluated: Boolean = false

    private val OP_CHARS = setOf('+', '−', '×', '÷')

    private fun standardState(): StandardModuleState {
        val cur = _uiState.value.moduleStates[CalcMode.Standard] as? StandardModuleState
        return cur ?: StandardModuleState()
    }

    private fun updateStandardState(transform: (StandardModuleState) -> StandardModuleState) {
        _uiState.update { s ->
            val cur = s.moduleStates[CalcMode.Standard] as? StandardModuleState
                ?: StandardModuleState()
            val next = transform(cur)
            s.copy(moduleStates = s.moduleStates + (CalcMode.Standard to next))
        }
    }

    /**
     * 把输入行字符串解析成 token 列表（数字字符串 与 运算符 交替），
     * 并保证最后一个 token 一定是数字（如果末尾是运算符则丢弃）。
     */
    private fun parseExprTokens(exprStr: String): MutableList<String> {
        if (exprStr.isBlank()) return mutableListOf()
        val tokens = mutableListOf<String>()
        val buf = StringBuilder()
        for (ch in exprStr) {
            if (ch in OP_CHARS) {
                if (buf.isNotEmpty()) {
                    tokens.add(buf.toString())
                    buf.clear()
                }
                tokens.add(ch.toString())
            } else {
                buf.append(ch)
            }
        }
        if (buf.isNotEmpty()) tokens.add(buf.toString())
        val last = tokens.lastOrNull()
        if (last != null && last.length == 1 && last[0] in OP_CHARS) {
            tokens.removeLast()
        }
        return tokens
    }

    /** 对 tokens 从左到右求值（严格按用户输入顺序，不考虑 ×÷ 优先级）。 */
    private fun evalTokens(tokens: List<String>): Double? {
        if (tokens.isEmpty()) return null
        var acc: Double = tokens[0].toDoubleOrNull() ?: return null
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]
            val num = tokens[i + 1].toDoubleOrNull() ?: return null
            acc = applyOp(op, acc, num)
            i += 2
        }
        return acc
    }

    /** 根据 stdExpr 重新计算答案行，同时更新输入行；同时把 evaluated 置为 false（进入输入态）。 */
    private fun refreshStandardDisplay(resetEvaluated: Boolean = true) {
        val exprStr = stdExpr.toString()
        val tokens = parseExprTokens(exprStr)
        val value = evalTokens(tokens)
        val result = if (value == null) "0" else formatResult(value)
        updateStandardState {
            it.copy(
                expression = exprStr,
                display = result,
                evaluated = if (resetEvaluated) false else it.evaluated,
            )
        }
    }

    /** 取"当前操作数字符串"（表达式中 stdOperandStart 之后的内容）。 */
    private fun currentOperand(): String =
        if (stdOperandStart in 0..stdExpr.length) stdExpr.substring(stdOperandStart) else ""

    /** 替换 stdExpr 中当前操作数字符串为新值。 */
    private fun replaceCurrentOperand(newOperand: String) {
        val prefix = stdExpr.substring(0, stdOperandStart)
        stdExpr.clear()
        stdExpr.append(prefix).append(newOperand)
    }

    /** 标准模块按键处理（输入行 + 实时答案行，两行始终亮色）。 */
    private fun reduceStandard(intent: ModuleIntent) {
        when (intent) {
            is ModuleIntent.Clear -> {
                stdExpr.clear()
                stdOperandStart = 0
                stdJustEvaluated = false
                updateStandardState { StandardModuleState() }
            }
            is ModuleIntent.Backspace -> handleStandardBackspace()
            is ModuleIntent.Input -> {
                when (intent.value) {
                    "+", "−", "×", "÷" -> handleStandardOperator(intent.value)
                    "." -> handleStandardDecimal()
                    else -> handleStandardDigit(intent.value)
                }
            }
            is ModuleIntent.Evaluate -> handleStandardEquals()
            is ModuleIntent.Custom -> {
                when (intent.key) {
                    "negate" -> handleStandardNegate()
                    "percent" -> handleStandardPercent()
                }
            }
        }
    }

    private fun handleStandardDigit(digit: String) {
        if (digit.length != 1 || !digit[0].isDigit()) return
        if (stdJustEvaluated) {
            stdExpr.clear()
            stdExpr.append(digit)
            stdOperandStart = 0
            stdJustEvaluated = false
            refreshStandardDisplay()
            return
        }
        val operand = currentOperand()
        val digitsOnly = operand.filter { it.isDigit() }
        if (digitsOnly.length >= 15) return
        when {
            operand == "0" -> replaceCurrentOperand(digit)
            operand == "-0" -> replaceCurrentOperand("-$digit")
            else -> stdExpr.append(digit)
        }
        refreshStandardDisplay()
    }

    private fun handleStandardDecimal() {
        if (stdJustEvaluated) {
            stdExpr.clear()
            stdExpr.append("0.")
            stdOperandStart = 0
            stdJustEvaluated = false
            refreshStandardDisplay()
            return
        }
        val operand = currentOperand()
        if ('.' in operand) return
        if (operand.isEmpty()) stdExpr.append("0.") else stdExpr.append('.')
        refreshStandardDisplay()
    }

    private fun handleStandardOperator(op: String) {
        val opChar = op.firstOrNull() ?: return
        if (stdJustEvaluated) {
            stdExpr.clear()
            val lastResult = standardState().display.takeIf { it != "错误" } ?: "0"
            stdExpr.append(lastResult)
            stdOperandStart = 0
            stdJustEvaluated = false
        }
        if (stdExpr.isEmpty()) return
        val last = stdExpr.last()
        if (last in OP_CHARS) {
            stdExpr.setCharAt(stdExpr.length - 1, opChar)
        } else {
            stdExpr.append(opChar)
            stdOperandStart = stdExpr.length
        }
        refreshStandardDisplay()
    }

    private fun handleStandardBackspace() {
        if (stdExpr.isEmpty()) return
        // 按 = 后紧接着按退格 → 取消 evaluated 状态（回到输入态），继续编辑表达式
        if (stdJustEvaluated) stdJustEvaluated = false
        val deleted = stdExpr[stdExpr.length - 1]
        stdExpr.deleteCharAt(stdExpr.length - 1)
        if (deleted in OP_CHARS) {
            val idx = stdExpr.indexOfLast { it in OP_CHARS }
            stdOperandStart = idx + 1
        }
        refreshStandardDisplay()
    }

    private fun handleStandardEquals() {
        val tokens = parseExprTokens(stdExpr.toString())
        if (tokens.isEmpty()) return
        val value = evalTokens(tokens) ?: return
        val resultStr = formatResult(value)
        val historyExpr = tokens.joinToString("")
        val item = HistoryItem(expression = historyExpr, result = resultStr)
        _uiState.update { s ->
            s.copy(history = listOf(item) + s.history.take(99))
        }
        persist()
        stdJustEvaluated = true
        // = 之后：翻转显示（上小暗 / 下大亮），把 evaluated=true 写入 state
        val exprStr = stdExpr.toString()
        updateStandardState {
            it.copy(
                expression = exprStr,
                display = resultStr,
                evaluated = true,
            )
        }
    }

    private fun handleStandardNegate() {
        val operand = currentOperand()
        if (operand.isEmpty()) return
        val newOperand = when {
            operand == "0" -> "-0"
            operand.startsWith('-') -> operand.drop(1)
            else -> "-$operand"
        }
        replaceCurrentOperand(newOperand)
        refreshStandardDisplay()
    }

    private fun handleStandardPercent() {
        val operand = currentOperand()
        val v = operand.toDoubleOrNull() ?: return
        val newOperand = formatNumber(v / 100.0)
        replaceCurrentOperand(newOperand)
        refreshStandardDisplay()
    }

    private fun applyOp(op: String, lhs: Double, rhs: Double): Double = when (op) {
        "+" -> lhs + rhs
        "−" -> lhs - rhs
        "×" -> lhs * rhs
        "÷" -> if (rhs == 0.0) Double.NaN else lhs / rhs
        else -> Double.NaN
    }

    /** 数字格式化：去除 .0 小数尾零，NaN / Infinity 显示 "错误"，大数字用科学计数法兜底。 */
    private fun formatResult(v: Double): String = when {
        v.isNaN() || v.isInfinite() -> "错误"
        else -> formatNumber(v)
    }

    private fun formatNumber(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "错误"
        // 整数：直接 toString 去掉 .0
        if (v == v.toLong().toDouble() && v in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
            return v.toLong().toString()
        }
        // 普通小数：先拼字符串后去尾零
        val raw = v.toString()
        return if (raw.contains('E') || raw.contains('e')) raw
        else raw.trimEnd('0').trimEnd('.')
    }

    // ===== 持久化 =====
    private fun persist() {
        val s = _uiState.value
        with(prefs.edit()) {
            putString("mode", s.mode.name)
            putString("menu_order", s.menuOrder.joinToString(",") { it.name })
            putString("theme_mode", s.themeMode.name)
            putString("theme_color", serializeThemeColor(s.themeColor))
            apply()
        }
    }

    private fun loadInitialState(): CalculatorUiState {
        val mode = prefs.getString("mode", null)
            ?.let { runCatching { CalcMode.valueOf(it) }.getOrNull() }
            ?: CalcMode.Standard
        val menuOrder = prefs.getString("menu_order", null)
            ?.split(",")
            ?.mapNotNull { runCatching { CalcMode.valueOf(it) }.getOrNull() }
            // 旧数据可能缺少新增模块，过滤无效项后用默认顺序补全
            ?.let { saved ->
                val full = CalcMode.defaultOrder
                val merged = saved.toMutableList()
                full.forEach { mode -> if (merged.none { it == mode }) merged.add(mode) }
                merged.filter { it in CalcMode.defaultOrder }
            }
            ?.takeIf { it.isNotEmpty() }
            ?: CalcMode.defaultOrder
        val themeMode = prefs.getString("theme_mode", null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.Auto
        val themeColor = parseThemeColor(prefs.getString("theme_color", null))
        return CalculatorUiState(
            mode = mode,
            menuOrder = menuOrder,
            history = emptyList(),
            themeMode = themeMode,
            themeColor = themeColor,
        )
    }

    companion object {
        private const val PREFS_NAME = "smartcalc_prefs"

        class Factory(private val app: Application) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CalculatorViewModel(app) as T
            }
        }
    }
}

/**
 * 计算器模式。
 *
 * 每个模式视为一个独立模块（独立 UI / 独立逻辑），但共用同一份历史记录，
 * 模块之间可以无缝切换 / 整合。后续如需多人协同，可在 ViewModel 层接入
 * 远端同步通道，UI 层无需改动。
 *
 * - [Standard]      标准
 * - [Scientific]     科学
 * - [Programmer]     程序员
 * - [MatlabSet]      MATLAB 集（带二级子菜单，子模块逐步接入）
 * - [Statistics]     统计
 * - [UnitConversion] 单位换算
 * - [EquationSolver] 解方程
 * - [Plotting]       绘图
 */
enum class CalcMode {
    Standard, Scientific, Programmer, MatlabSet,
    Statistics, UnitConversion, EquationSolver, Plotting;

    /** 默认菜单顺序，[ModeDrawer] 与持久化层共用。 */
    companion object {
        val defaultOrder: List<CalcMode> = listOf(
            Standard, Scientific, Programmer, MatlabSet,
            Statistics, UnitConversion, EquationSolver, Plotting,
        )
    }
}

/** 模式 → 中文显示名（菜单抽屉 & Header 共用） */
fun CalcMode.displayName(): String = when (this) {
    CalcMode.Standard       -> "标准"
    CalcMode.Scientific     -> "科学"
    CalcMode.Programmer     -> "程序员"
    CalcMode.MatlabSet      -> "MATLAB 集"
    CalcMode.Statistics     -> "统计"
    CalcMode.UnitConversion -> "单位换算"
    CalcMode.EquationSolver -> "解方程"
    CalcMode.Plotting       -> "绘图"
}

/** 该模式是否带二级子菜单。目前只有 MATLAB 集。 */
val CalcMode.hasSubMenu: Boolean
    get() = this == CalcMode.MatlabSet

/**
 * 历史记录项（暂时只用展示，后续计算功能完善后再写入）
 */
data class HistoryItem(
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * UI 状态（精简版：仅模式 / 历史 / 菜单顺序 / 主题）
 */
data class CalculatorUiState(
    val mode: CalcMode = CalcMode.Standard,
    val history: List<HistoryItem> = emptyList(),
    val menuOrder: List<CalcMode> = CalcMode.defaultOrder,
    val themeMode: ThemeMode = ThemeMode.Auto,
    val themeColor: Color = ThemeColorPresets.Blue.color,
    /**
     * 各模块私有状态（按 [CalcMode] 索引）。
     *
     * - 模块之间互不读取对方的状态，避免耦合
     * - 多人协同时每人只改自己模块的 State 类与 reducer，
     *   本字段是单一 Map 容器，**新增模块不需修改本字段定义**
     */
    val moduleStates: Map<CalcMode, com.example.smartcalculator.ui.modules.ModuleState> = emptyMap(),
)
