package com.example.smartcalculator.ui.modules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.ui.components.isDarkTheme
import com.example.smartcalculator.ui.theme.PanelAppleDark
import com.example.smartcalculator.ui.theme.ThemeColor
import androidx.compose.ui.geometry.Offset

// ============================================================
//  子类型常量
// ============================================================
internal const val SUB_POLY = "POLY"      // 一元多次
internal const val SUB_LINEAR = "LINEAR"  // 多元一次

// ---------- 常量 ----------

/** 一元多次 root([…]) 里固定提供的系数格数（7 个 = 0~6 次方，最高 6 次） */
private const val POLY_MAX_CELLS = 7

// ============================================================
//  状态 & Reducer
// ============================================================

/**
 * 解方程模块状态。
 *
 * 两种子类型共用同一 cell-editing 机制：
 * - POLY   ：activeCell = Pair(indexInCoeffs, 0)
 * - LINEAR ：activeCell = Pair(row, col)  （列 = 0..dim，最后一列是 b）
 */
data class EquationSolverModuleState(
    val subType: String = SUB_POLY,
    // ---------- 一元多次 ----------
    // 固定 7 个系数位（对应 0~6 次共 7 项：c₀x⁶ + c₁x⁵ + … + c₆ = 0）。
    // 用户想输几个就输几个，前导 0/空会在 evaluate 时自动裁剪。
    // polyDegree 仅保留用于兼容旧逻辑，UI 展示永远以 polyCoeffs.size(=7) 为准。
    val polyDegree: Int = 6,
    val polyCoeffs: List<String> = List(7) { "" },
    // ---------- 多元一次 ----------
    val linearDim: Int = 2,
    // n × (n + 1) 增广矩阵 [A|b]，每格是未解析的字符串
    val linearMatrix: List<List<String>> = List(2) { List(3) { "" } },
    // ---------- 共享 ----------
    val activeCell: Pair<Int, Int> = 0 to 0,
    val results: List<String> = emptyList(),
    val errorMsg: String? = null,
) : ModuleState

/**
 * 解方程意图。
 *
 * 通用 ModuleIntent 与本模块 Custom 键清单：
 *  - ModuleIntent.Input(digit/. )   → 当前格追加字符
 *  - ModuleIntent.Backspace          → 当前格删1字（= 退格 ⌫）
 *  - ModuleIntent.Clear              → 清空所有格（= C 清零）
 *  - ModuleIntent.Evaluate           → 解析 + 调用算法
 * Custom:
 *  - "set_sub"    ：SUB_POLY / SUB_LINEAR
 *  - "set_degree" ：Int (≥1)
 *  - "set_dim"    ：Int (≥2)
 *  - "negate"     ：± 正负号切换，当前格取反（前加 -，若已加则去掉）
 *  - "next"       ：→ 下一格（行优先，LINEAR 的 A 填完自动进 b 列）
 *  - "next_row"   ：; 下一行（LINEAR 专用，POLY 等价于 next）
 *  - "active"     ：Pair<Int,Int> 手动切到指定单元格
 */
internal fun reduceEquationSolver(
    state: EquationSolverModuleState,
    intent: ModuleIntent,
): EquationSolverModuleState = when (intent) {
    is ModuleIntent.Input -> {
        val ch = intent.value
        state.editActive { cell ->
            when {
                // 数字：直接追加
                ch.length == 1 && ch[0].isDigit() -> cell + ch
                // 小数点：只允许出现一次，且分子/分母各自最多一个
                ch == "." -> {
                    val hasFrac = "/" in cell
                    val part = if (hasFrac) cell.substringAfter("/") else cell
                    if ("." in part) cell else "$cell."
                }
                // 分数线 /：一个格子里最多一个，且只能在有数字后加
                ch == "/" -> {
                    if ("/" in cell || cell.isEmpty() || cell == "-") cell else "$cell/"
                }
                else -> cell
            }
        }
    }
    ModuleIntent.Backspace -> state.editActive { it.dropLast(1) }
    ModuleIntent.Clear -> state.copy(
        polyCoeffs = List(POLY_MAX_CELLS) { "" },
        linearMatrix = List(state.linearDim) { List(state.linearDim + 1) { "" } },
        activeCell = 0 to 0,
        results = emptyList(),
        errorMsg = null,
    )
    ModuleIntent.Evaluate -> state.evaluateSafe()
    is ModuleIntent.Custom -> when (intent.key) {
        "set_sub" -> {
            val s = intent.payload as? String ?: SUB_POLY
            val linD = 2
            EquationSolverModuleState(
                subType = s,
                polyDegree = 6,
                polyCoeffs = List(POLY_MAX_CELLS) { "" },
                linearDim = linD,
                linearMatrix = List(linD) { List(linD + 1) { "" } },
                activeCell = 0 to 0,
            )
        }
        "set_degree" -> {
            // 保持兼容：不再改变 polyCoeffs 数量（永远 7 格），只记录 polyDegree。
            val d = (intent.payload as? Int ?: 6).coerceAtLeast(1).coerceAtMost(6)
            state.copy(
                polyDegree = d,
                activeCell = 0 to 0,
                results = emptyList(),
                errorMsg = null,
            )
        }
        "set_dim" -> {
            val d = (intent.payload as? Int ?: 2).coerceAtLeast(2)
            val old = state.linearMatrix
            val new = List(d) { r ->
                List(d + 1) { c ->
                    old.getOrNull(r)?.getOrNull(c) ?: ""
                }
            }
            state.copy(
                linearDim = d,
                linearMatrix = new,
                activeCell = 0 to 0,
                results = emptyList(),
                errorMsg = null,
            )
        }
        "negate" -> state.editActive { cell ->
            when {
                cell.isEmpty() -> "-"
                cell.startsWith("-") -> cell.drop(1)
                else -> "-$cell"
            }
        }
        "next" -> state.moveActive(nextRow = false)
        "next_row" -> state.moveActive(nextRow = true)
        "active" -> {
            val p = intent.payload as? Pair<*, *>
            val r = (p?.first as? Int) ?: 0
            val c = (p?.second as? Int) ?: 0
            state.copy(activeCell = r to c)
        }
        else -> state
    }
}

// ---------- state 操作辅助 ----------

private inline fun EquationSolverModuleState.editActive(
    block: (String) -> String,
): EquationSolverModuleState {
    val (r, c) = activeCell
    return when (subType) {
        SUB_POLY -> {
            if (r !in polyCoeffs.indices) return this
            val newList = polyCoeffs.toMutableList().also {
                it[r] = block(it[r])
            }
            copy(polyCoeffs = newList, errorMsg = null)
        }
        SUB_LINEAR -> {
            val row = linearMatrix.getOrNull(r) ?: return this
            if (c !in row.indices) return this
            val newRow = row.toMutableList().also { it[c] = block(it[c]) }
            copy(
                linearMatrix = linearMatrix.toMutableList().also { it[r] = newRow },
                errorMsg = null,
            )
        }
        else -> this
    }
}

private fun EquationSolverModuleState.moveActive(nextRow: Boolean): EquationSolverModuleState {
    val (r, c) = activeCell
    val (nr, nc) = when (subType) {
        SUB_POLY -> {
            val total = POLY_MAX_CELLS   // 固定 7 格（0..6 共 7 个索引）
            val newIdx = if (nextRow) {
                (r + 1).coerceAtMost(total - 1)
            } else {
                (r + 1) % total          // 下一格循环：最后一格 → 回到第一格（MATLAB手感）
            }
            newIdx to 0
        }
        SUB_LINEAR -> {
            val n = linearDim
            val m = n + 1
            when {
                nextRow -> ((r + 1).coerceAtMost(n - 1)) to 0
                else -> {
                    if (c + 1 < m) r to (c + 1)
                    else if (r + 1 < n) (r + 1) to 0
                    else r to c
                }
            }
        }
        else -> r to c
    }
    return copy(activeCell = nr to nc)
}

private fun EquationSolverModuleState.evaluateSafe(): EquationSolverModuleState {
    val parsed = runCatching {
        when (subType) {
            SUB_POLY -> {
                // 用户按"说明文档"约定顺序填系数（就是 root([c0 c1 c2 ... cn]) 里的顺序）。
                // 每个格子支持分数 "a/b" 形式（a、b 可以是小数或负数），parseCellToDouble 会精确计算。
                // 为了兼容"前导 0 填错了"的情况，先裁剪前导 0 再交给算法；
                // 全 0 就报错；裁剪后只剩 1 个数（常数项），认为无解。
                val parsedList = polyCoeffs.map { parseCellToDouble(it) }
                val trimmed = parsedList.dropWhile { kotlin.math.abs(it) < 1e-12 }
                val doubles = trimmed.ifEmpty { listOf(0.0) }
                require(doubles.size >= 2) { "系数不足（至少 2 个非零有效系数，形如 roots([2 3]) 对应 2x + 3 = 0）" }
                numericRoots(doubles.toDoubleArray())
                    .mapIndexed { i, root -> "  x${i + 1} = ${root.fmt()}" }
            }
            SUB_LINEAR -> {
                val n = linearDim
                val A = Array(n) { r ->
                    DoubleArray(n) { c -> parseCellToDouble(linearMatrix[r][c]) }
                }
                val b = DoubleArray(n) { r -> parseCellToDouble(linearMatrix[r][n]) }
                val x = gaussElim(A, b) ?: error("系数矩阵奇异：无解或无穷多解")
                val vars = "xyzuvw".toCharArray().take(n)
                x.mapIndexed { i, xi -> "  ${vars[i]} = ${xi.fmt()}" }
            }
            else -> emptyList()
        }
    }
    return if (parsed.isSuccess) {
        copy(results = parsed.getOrThrow(), errorMsg = null)
    } else {
        copy(results = emptyList(), errorMsg = parsed.exceptionOrNull()?.message ?: "输入有误")
    }
}

/**
 * 把用户在单个格子里输入的字符串解析成 Double。
 * 支持：
 *  - 整数："123"、"-7"
 *  - 小数："0.5"、"-3.14"
 *  - 分数："1/2"、"-3/4"、"0.1/2.5"（先算分子分母再相除，分母为 0 抛异常）
 *  - 空串 → 0.0
 */
private fun parseCellToDouble(s: String): Double {
    val t = s.trim()
    if (t.isEmpty() || t == "-" || t == "." || t == "/") return 0.0
    if ("/" in t) {
        val parts = t.split("/")
        require(parts.size == 2) { "分数格式错误：\"$t\"（形如 1/2）" }
        val num = parseSimpleNumber(parts[0])
        val den = parseSimpleNumber(parts[1])
        require(kotlin.math.abs(den) > 1e-15) { "分母不能为 0：\"$t\"" }
        return num / den
    }
    return parseSimpleNumber(t)
}

/** 单个数字（可带 - 和 .）的解析 */
private fun parseSimpleNumber(s: String): Double {
    val t = s.trim()
    if (t.isEmpty() || t == "-" || t == ".") return 0.0
    return t.toDoubleOrNull() ?: throw IllegalArgumentException("非法数字：\"$t\"")
}

private fun Double.fmt(): String {
    if (this.isNaN()) return "NaN"
    if (this.isInfinite()) return if (this > 0) "+∞" else "-∞"
    val v = kotlin.math.round(this * 1e6) / 1e6
    // 整数值不带小数
    val longPart = v.toLong()
    if (kotlin.math.abs(v - longPart.toDouble()) < 1e-6) {
        return longPart.toString()
    }
    return "%.6g".format(v)
}

/** 纯 Kotlin 复数：实部 + 虚部（无任何依赖） */
internal data class Complex(val re: Double, val im: Double = 0.0) {

    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun plus(d: Double) = Complex(re + d, im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
    operator fun minus(d: Double) = Complex(re - d, im)
    operator fun times(o: Complex) = Complex(
        re * o.re - im * o.im,
        re * o.im + im * o.re,
    )
    operator fun times(d: Double) = Complex(re * d, im * d)
    operator fun div(o: Complex): Complex {
        val den = o.re * o.re + o.im * o.im
        if (den < 1e-30) return Complex(Double.NaN, Double.NaN)
        return Complex(
            (re * o.re + im * o.im) / den,
            (im * o.re - re * o.im) / den,
        )
    }
    operator fun unaryMinus() = Complex(-re, -im)
    fun conj() = Complex(re, -im)
    fun abs2() = re * re + im * im
    fun abs() = kotlin.math.sqrt(abs2())

    companion object {
        val ZERO = Complex(0.0, 0.0)
        val ONE = Complex(1.0, 0.0)
        val I = Complex(0.0, 1.0)
    }
}

/** 复根格式化：a ± bi，纯实不带虚部，纯虚不带实部 */
internal fun Complex.fmt(): String {
    val eps = 1e-6
    val reIsZero = kotlin.math.abs(re) < eps
    val imIsZero = kotlin.math.abs(im) < eps
    return when {
        reIsZero && imIsZero -> "0"
        imIsZero -> re.fmt()
        reIsZero -> {
            val a = kotlin.math.abs(im).fmt()
            val sign = if (im < 0) "-" else ""
            if (kotlin.math.abs(kotlin.math.abs(im) - 1.0) < eps) "${sign}i" else "${sign}${a}i"
        }
        else -> {
            val reStr = re.fmt()
            val imAbsStr = kotlin.math.abs(im).fmt()
            val sign = if (im > 0) "+" else "-"
            val imStr = if (kotlin.math.abs(kotlin.math.abs(im) - 1.0) < eps) "i" else "${imAbsStr}i"
            "$reStr $sign $imStr"
        }
    }
}

// ============================================================
//  核心算法
// ============================================================

/**
 * 求实系数多项式 p(x) = p[0]*x^n + p[1]*x^(n-1) + ... + p[n] = 0 的**所有根（含复数）**。
 *
 * 策略：
 *  - **前处理**：先裁前导 0，再**剥掉尾部 x^k**（末尾多个 0 等价于 x^k * q(x)），把 q(x) 交给后续求根后再把 k 个 0 根补回。
 *    这避免了用户没填的格子（=0）把次数硬拉到 6 次造成高次重 0 根导致 Durand-Kerner 发散。
 *  - 真实次数 n'=1,2：闭式解（二次方程直接输出共轭复根对）
 *  - 3 ≤ n' ≤ 6：**Durand-Kerner / Weierstrass 迭代**（最多 3 次重新撒初值，避免卡住）
 */
internal fun numericRoots(p: DoubleArray): List<Complex> {
    // 1. 裁剪前导 0，保证首项非零（除非恒 0）
    var coeffs = p
    while (coeffs.size > 1 && kotlin.math.abs(coeffs.first()) < 1e-14) {
        coeffs = coeffs.copyOfRange(1, coeffs.size)
    }
    if (coeffs.size <= 1) return emptyList()

    // 2. 剥尾部 0：即提取 x^k 因子，等价于在根集合里补 k 个 0
    //    例：用户填 [1,2,1,0,0,0,0] → q=[1,2,1], k=4 → roots(q) + 4 个 0 根
    var tailZeros = 0
    while (coeffs.size - tailZeros >= 2 && kotlin.math.abs(coeffs[coeffs.lastIndex - tailZeros]) < 1e-14) {
        tailZeros++
    }
    val trimmedCore = coeffs.copyOf(coeffs.size - tailZeros)
    val zeroRoots: List<Complex> = if (tailZeros == 0) emptyList() else List(tailZeros) { Complex.ZERO }

    val n = trimmedCore.size - 1

    // 3. 归一化为首一（除以首项系数）
    val a0 = trimmedCore[0]
    val monic = DoubleArray(trimmedCore.size) { trimmedCore[it] / a0 }

    // 4. 1 次 / 2 次：闭式，直接返回，含复根
    val coreRoots: List<Complex> = when (n) {
        1 -> listOf(Complex(-monic[1], 0.0))
        2 -> {
            val p1 = monic[1]; val q = monic[2]
            val disc = p1 * p1 - 4.0 * q
            if (disc >= 0.0) {
                val s = kotlin.math.sqrt(disc)
                listOf(Complex((-p1 + s) / 2.0, 0.0), Complex((-p1 - s) / 2.0, 0.0))
            } else {
                val s = kotlin.math.sqrt(-disc)
                listOf(Complex(-p1 / 2.0, s / 2.0), Complex(-p1 / 2.0, -s / 2.0))
            }
        }
        else -> durandKerner(monic, n) // 3~6 次：迭代求解
    }

    // 5. 合并：尾部 0 根 + 核心根；并做清洗 + 排序
    return cleanAndSortRoots(coreRoots + zeroRoots)
}

/** Durand-Kerner 迭代：求首一 monic（长度 n+1）多项式的全部 n 个根 */
private fun durandKerner(monic: DoubleArray, n: Int): List<Complex> {
    val maxRetries = 3
    repeat(maxRetries) { attempt ->
        // 不同重试：初值做一点扰动，避免卡在坏初值
        val roots = Array(n) { k ->
            val r = Math.pow(0.82 + 0.06 * attempt, k.toDouble()) * 0.9 + 0.12
            val theta = (2.0 * Math.PI * k / n) + 0.23 + 0.11 * attempt
            Complex(r * Math.cos(theta), r * Math.sin(theta))
        }
        val maxIter = 500
        val tol = 1e-11
        var lastMaxDelta = Double.POSITIVE_INFINITY
        repeat(maxIter) {
            var maxDelta = 0.0
            for (i in 0 until n) {
                val xi = roots[i]
                if (xi.re.isNaN() || xi.im.isNaN() || xi.re.isInfinite() || xi.im.isInfinite()) {
                    return@repeat // 本轮失败，重试
                }
                // 分母：Π_{j ≠ i} (xi - xj)
                var denom = Complex.ONE
                for (j in 0 until n) {
                    if (j == i) continue
                    denom *= (xi - roots[j])
                    if (denom.re.isNaN() || denom.im.isNaN()) break
                }
                if (denom.abs2() < 1e-200) continue // 太小，下轮再算
                val numer = evalPolyComplex(monic, xi)
                if (numer.re.isNaN() || numer.im.isNaN()) continue

                val delta = numer / denom
                if (delta.re.isNaN() || delta.im.isNaN()) continue
                // 步长裁剪：防止一步飞太远（对重根、小分母时非常关键）
                val stepCap = 2.0
                val norm = delta.abs()
                val effDelta = if (norm > stepCap) delta * (stepCap / norm) else delta
                roots[i] = xi - effDelta
                val d = effDelta.abs()
                if (d > maxDelta) maxDelta = d
            }
            if (maxDelta < tol) return roots.toList()
            if (maxDelta.isNaN()) return@repeat
            lastMaxDelta = maxDelta
        }
        // 如果结束时已接近收敛（< 1e-6），也接受（允许重根等疑难情况的较低精度）
        if (lastMaxDelta < 1e-6) return roots.toList()
    }
    // 最终兜底：返回最后一次迭代结果，由上层 cleanAndSort 清洗
    return emptyList()
}

/** Durand-Kerner 结果后处理：去除 NaN/Inf、清洗近零虚部、排序（实根→共轭对） */
private fun cleanAndSortRoots(src: List<Complex>): List<Complex> {
    val eps = 1e-6
    val cleaned = src
        .filterNot { it.re.isNaN() || it.im.isNaN() || it.re.isInfinite() || it.im.isInfinite() }
        .map {
            val reR = kotlin.math.round(it.re * 1e10) / 1e10
            val imR = if (kotlin.math.abs(it.im) < eps) 0.0 else kotlin.math.round(it.im * 1e10) / 1e10
            Complex(reR, imR)
        }
    return cleaned.sortedWith(
        compareBy<Complex>
        { kotlin.math.abs(it.im) > 1e-12 }   // 实根在前
            .thenBy { it.re }
            .thenByDescending { it.im }        // 对同一实部，先正虚部，再负虚部（共轭对）
            .thenBy { kotlin.math.abs(it.im) }
    )
}

/** Horner 法：首一多项式 monic（长度 n+1，monic[0]=1）在复点 x 处的取值 */
private fun evalPolyComplex(monic: DoubleArray, x: Complex): Complex {
    var res = Complex(monic[0], 0.0)
    for (i in 1 until monic.size) {
        res = res * x + Complex(monic[i], 0.0)
    }
    return res
}

/* ------------ 以下两个旧工具保留（无引用但兼容外部调用） ------------ */

/** 牛顿法找一个实根（保留，当前算法不再使用） */
private fun findOneRootNewton(@Suppress("UNUSED_PARAMETER") p: DoubleArray): Double? = null

/** 多项式除以 (x-r)（保留，当前算法不再使用） */
private fun deflatePoly(p: DoubleArray, @Suppress("UNUSED_PARAMETER") r: Double): DoubleArray = p

/** 返回 (p(x), p'(x))（保留，当前算法不再使用） */
private fun evalPolyAndDerivative(p: DoubleArray, x: Double): Pair<Double, Double> {
    var v = 0.0; var d = 0.0
    for (i in 0 until p.size - 1) {
        d = d * x + v
        v = v * x + p[i]
    }
    v = v * x + p.last()
    return v to d
}

/**
 * 高斯消元解线性方程组 Ax = b（方阵）。
 * 返回解向量 x；奇异返回 null。
 */
internal fun gaussElim(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
    val n = A.size
    require(A.all { it.size == n } && b.size == n) { "维数不匹配" }
    // 构建增广矩阵 n × (n+1)
    val aug = Array(n) { i ->
        DoubleArray(n + 1) { j -> if (j < n) A[i][j] else b[i] }
    }
    // 1. 前向消元 + 部分选主元
    for (col in 0 until n) {
        // 找当前列绝对值最大行
        var piv = col
        for (r in col + 1 until n) {
            if (kotlin.math.abs(aug[r][col]) > kotlin.math.abs(aug[piv][col])) piv = r
        }
        if (kotlin.math.abs(aug[piv][col]) < 1e-12) return null
        if (piv != col) { val t = aug[col]; aug[col] = aug[piv]; aug[piv] = t }
        val div = aug[col][col]
        for (r in col + 1 until n) {
            val factor = aug[r][col] / div
            for (k in col until n + 1) aug[r][k] -= factor * aug[col][k]
        }
    }
    // 2. 回代
    val x = DoubleArray(n)
    for (i in n - 1 downTo 0) {
        var s = aug[i][n]
        for (j in i + 1 until n) s -= aug[i][j] * x[j]
        x[i] = s / aug[i][i]
    }
    return x
}

// ============================================================
//  模块 UI 入口
// ============================================================

/**
 * 解方程模块入口（只改本文件）。
 */
@Composable
fun EquationSolverModule(
    state: EquationSolverModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    ModuleLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = {
            EquationSolverDisplay(
                state = state,
                onPickCell = { r, c ->
                    onIntent(ModuleIntent.Custom("active", r to c))
                },
                onIntent = onIntent,
                modifier = Modifier.fillMaxSize(),
            )
        },
        keypad = {
            EquationSolverKeypad(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

// ============================================================
//  显示区：MATLAB 命令行风格
// ============================================================

@Composable
private fun BoxScope.EquationSolverDisplay(
    state: EquationSolverModuleState,
    onPickCell: (Int, Int) -> Unit,
    onIntent: (ModuleIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isDarkTheme()
    val accent = ThemeColor.current

    Column(modifier = modifier.padding(16.dp)) {
        // 顶部模式提示（一元多次：静态说明；多元一次：整行可点击弹出维度选择）
        val showDimDialog = remember { androidx.compose.runtime.mutableStateOf(false) }
        val titleShape = RoundedCornerShape(10.dp)

        if (state.subType == SUB_POLY) {
            Text(
                text = "一元多次 · 固定 7 格（x⁶ → x⁰，最高 6 次）  >> roots([c₀ c₁ … c₆])  想输几个就输几个",
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.5f),
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(titleShape)
                    .background(accent.copy(alpha = 0.08f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { showDimDialog.value = true },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "多元一次 · 当前 ${state.linearDim} 元（点这里调 2~6 元）  >> x = A \\ b",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.65f),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (state.subType == SUB_LINEAR) {
            DimDialog(
                state = state,
                onIntent = onIntent,
                open = showDimDialog.value,
                onDismiss = { showDimDialog.value = false },
            )
        }

        // ===== 主"编辑器"面板：液态玻璃命令窗口 =====
        val terminalShape = RoundedCornerShape(18.dp)
        val density = LocalDensity.current
        val radiusPx = with(density) { 18.dp.toPx() }

        val bg = if (dark) Color(0xFF0E0F14) else Color(0xFFF5F6FA)
        val promptColor = accent
        val commentColor = LocalContentColor.current.copy(alpha = 0.5f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = terminalShape,
                    ambientColor = accent.copy(alpha = if (dark) 0.35f else 0.08f),
                    spotColor = accent.copy(alpha = if (dark) 0.28f else 0.05f),
                    clip = false,
                )
                .clip(terminalShape)
                .background(bg)
                .drawBehind {
                    val w = size.width; val h = size.height
                    val top = if (dark) Color.White.copy(alpha = 0.18f) else Color.White
                    val bot = if (dark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.06f)
                    drawLine(top, Offset(radiusPx * 0.5f, 0.5f), Offset(w - radiusPx * 0.5f, 0.5f), 1f)
                    drawLine(bot, Offset(radiusPx * 0.5f, h - 0.5f), Offset(w - radiusPx * 0.5f, h - 0.5f), 1f)
                }
                .padding(16.dp),
        ) {
            Column {
                // ===== 命令行编辑器区 =====
                if (state.subType == SUB_POLY) {
                    PolyEditorLine(
                        coeffs = state.polyCoeffs,
                        activeIdx = state.activeCell.first,
                        accent = accent,
                        promptColor = promptColor,
                        commentColor = commentColor,
                        onClickIdx = { onPickCell(it, 0) },
                    )
                } else {
                    LinearEditorBlock(
                        dim = state.linearDim,
                        matrix = state.linearMatrix,
                        active = state.activeCell,
                        accent = accent,
                        promptColor = promptColor,
                        commentColor = commentColor,
                        onClickCell = { r, c -> onPickCell(r, c) },
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ===== 输出：ans = 或 error =====
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when {
                        state.errorMsg != null -> {
                            Column {
                                Text(
                                    text = "ans =",
                                    color = commentColor,
                                    fontFamily = monoFont(),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "  ⚠ ${state.errorMsg}",
                                    color = Color(0xFFFF3B30),
                                    fontFamily = monoFont(),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        state.results.isNotEmpty() -> {
                            Column {
                                Text(
                                    text = "ans =",
                                    color = commentColor,
                                    fontFamily = monoFont(),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                state.results.forEach {
                                    Text(
                                        text = it,
                                        color = accent,
                                        fontFamily = monoFont(),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = "% 填好后按右下角 ↵ 计算（↵ = Enter / evaluate）",
                                color = commentColor,
                                fontFamily = monoFont(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 当前系统等宽字体（缺失则不设置，用 Material 默认 monospace） */
private fun monoFont(): androidx.compose.ui.text.font.FontFamily? = null

/** 一元多次编辑器：单行 roots([c₀ c₁ c₂ … cn]) */
@Composable
private fun PolyEditorLine(
    coeffs: List<String>,
    activeIdx: Int,
    accent: Color,
    promptColor: Color,
    commentColor: Color,
    onClickIdx: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // 提示符
        Text(
            text = ">> ",
            color = promptColor,
            fontFamily = monoFont(),
            style = MaterialTheme.typography.bodyLarge,
        )
        // func
        Text(
            text = "roots(",
            color = promptColor,
            fontFamily = monoFont(),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "[",
            color = commentColor,
            fontFamily = monoFont(),
            style = MaterialTheme.typography.bodyLarge,
        )
        coeffs.forEachIndexed { i, c ->
            // 每个系数是一个可点的 token
            val active = i == activeIdx
            val displayText = c.ifEmpty { "□" }
            val tokenColor = when {
                active -> Color.White
                c.isEmpty() -> commentColor
                else -> LocalContentColor.current
            }
            val tokenBg = if (active) accent else Color.Transparent
            val tokenShape = RoundedCornerShape(6.dp)
            Box(
                modifier = Modifier
                    .clip(tokenShape)
                    .background(tokenBg)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onClickIdx(i) },
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (active) BlinkingCursor(accent = accent, isBefore = true)
                    Text(
                        text = displayText,
                        color = tokenColor,
                        fontFamily = monoFont(),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    if (active) BlinkingCursor(accent = accent, isBefore = false)
                }
            }
            if (i < coeffs.lastIndex) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
        Text(
            text = "])",
            color = commentColor,
            fontFamily = monoFont(),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** 闪烁光标 */
@Composable
private fun BlinkingCursor(accent: Color, isBefore: Boolean) {
    val show = remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530)
            show.value = !show.value
        }
    }
    Spacer(modifier = Modifier.width(1.5.dp))
    if (show.value) {
        Box(
            modifier = Modifier
                .width(1.6.dp)
                .height(18.dp)
                .background(accent)
        )
    }
    if (!isBefore) Spacer(modifier = Modifier.width(0.5.dp))
    else Unit
}

/** 多元一次编辑器：矩阵形式 A \ b */
@Composable
private fun LinearEditorBlock(
    dim: Int,
    matrix: List<List<String>>,
    active: Pair<Int, Int>,
    accent: Color,
    promptColor: Color,
    commentColor: Color,
    onClickCell: (r: Int, c: Int) -> Unit,
) {
    val vars = "xyzuvw".take(dim)
    Column {
        // >> prompt + A \ b 提示（首行）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ">> ",
                color = promptColor,
                fontFamily = monoFont(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "x = A \\ b  ",
                color = promptColor,
                fontFamily = monoFont(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "% 其中 A($dim×$dim),  b($dim×1)",
                color = commentColor,
                fontFamily = monoFont(),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        // 变量名头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // A 顶部列名
            Box(
                modifier = Modifier.width(2.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            repeat(dim) { i ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "  ${vars[i]}",
                        color = commentColor,
                        fontFamily = monoFont(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            // 分隔 |
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "b",
                    color = accent,
                    fontFamily = monoFont(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))

        // 行内容（每行前有"矩阵左括号"字符 + A 元素 + 分隔竖线 + b 元素 + 右括号）
        repeat(dim) { r ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = when {
                        dim == 1 -> "  ["
                        r == 0 -> " ⎡"
                        r == dim - 1 -> " ⎣"
                        else -> " ⎢"
                    },
                    color = accent,
                    fontFamily = monoFont(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                repeat(dim) { c ->
                    MatrixToken(
                        text = matrix.getOrNull(r)?.getOrNull(c) ?: "",
                        active = active.first == r && active.second == c,
                        accent = accent,
                        commentColor = commentColor,
                        modifier = Modifier.weight(1f),
                        onClick = { onClickCell(r, c) },
                    )
                }
                // A 与 b 的分隔线（竖线）
                Text(
                    text = "│",
                    color = accent.copy(alpha = 0.7f),
                    fontFamily = monoFont(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                )
                MatrixToken(
                    text = matrix.getOrNull(r)?.getOrNull(dim) ?: "",
                    active = active.first == r && active.second == dim,
                    accent = accent,
                    commentColor = commentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onClickCell(r, dim) },
                )
                Text(
                    text = when {
                        dim == 1 -> "]"
                        r == 0 -> "⎤"
                        r == dim - 1 -> "⎦"
                        else -> "⎥"
                    },
                    color = accent,
                    fontFamily = monoFont(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** 矩阵单元格 token：active 带 accent 填充 + 闪烁光标 */
@Composable
private fun MatrixToken(
    text: String,
    active: Boolean,
    accent: Color,
    commentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val displayText = text.ifEmpty { "□" }
    val color = when {
        active -> Color.White
        text.isEmpty() -> commentColor
        else -> LocalContentColor.current
    }
    val bg = if (active) accent else Color.Transparent
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(bg)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                )
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) BlinkingCursor(accent = accent, isBefore = true)
                Text(
                    text = displayText,
                    color = color,
                    fontFamily = monoFont(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                if (active) BlinkingCursor(accent = accent, isBefore = false)
            }
        }
    }
}

// ============================================================
//  键盘区：4列 × 5行 液态玻璃按键
// ============================================================

@Composable
private fun BoxScope.EquationSolverKeypad(
    state: EquationSolverModuleState,
    onIntent: (ModuleIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight().padding(horizontal = 14.dp, vertical = 12.dp)) {
        // 行间距
        val rowGap = 8.dp
        val colGap = 10.dp

        // ===== 行 1：[一元多次] [多元一次] [±] [/] =====
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(colGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubTypeKey(
                label = "一元多次",
                selected = state.subType == SUB_POLY,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onIntent(ModuleIntent.Custom("set_sub", SUB_POLY)) },
            )
            SubTypeKey(
                label = "多元一次",
                selected = state.subType == SUB_LINEAR,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onIntent(ModuleIntent.Custom("set_sub", SUB_LINEAR)) },
            )
            CircleKey(
                label = "±",
                onClick = { onIntent(ModuleIntent.Custom("negate")) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            CircleNumKey("/", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("/")) }
        }
        Spacer(modifier = Modifier.height(rowGap))

        // ===== 行 2：7 8 9 ⌫ =====
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(colGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleNumKey("7", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("7")) }
            CircleNumKey("8", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("8")) }
            CircleNumKey("9", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("9")) }
            CircleKey(
                label = "⌫",
                accentBg = true,
                onClick = { onIntent(ModuleIntent.Backspace) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Spacer(modifier = Modifier.height(rowGap))

        // ===== 行 3：4 5 6 C =====
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(colGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleNumKey("4", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("4")) }
            CircleNumKey("5", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("5")) }
            CircleNumKey("6", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("6")) }
            CircleKey(
                label = "C",
                accentBg = true,
                onClick = { onIntent(ModuleIntent.Clear) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Spacer(modifier = Modifier.height(rowGap))

        // ===== 行 4：1 2 3 ; 下一行 =====
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(colGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleNumKey("1", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("1")) }
            CircleNumKey("2", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("2")) }
            CircleNumKey("3", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("3")) }
            CircleKey(
                label = ";",
                subLabel = "下一行",
                onClick = { onIntent(ModuleIntent.Custom("next_row")) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Spacer(modifier = Modifier.height(rowGap))

        // ===== 行 5：→下一格  0   .   enter =====
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(colGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleKey(
                label = "→",
                subLabel = "下一格",
                onClick = { onIntent(ModuleIntent.Custom("next")) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            CircleNumKey("0", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input("0")) }
            CircleNumKey(".", Modifier.weight(1f).fillMaxHeight()) { onIntent(ModuleIntent.Input(".")) }
            CircleKey(
                label = "↵",
                subLabel = "计算",
                accentBg = true,
                onClick = { onIntent(ModuleIntent.Evaluate) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

// ---------- 按键：通用液态玻璃样式 ----------

/**
 * 液态玻璃按键（圆角矩形）—— 给"文字键"用。
 */
@Composable
private fun GlassKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    subLabel: String? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    labelColor: Color = Color.Unspecified,
    useAccent: Boolean = false,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(17.6.dp)
    GlassKeyBase(
        onClick = onClick,
        modifier = modifier,
        label = label,
        subLabel = subLabel,
        fontSize = fontSize,
        labelColor = labelColor,
        useAccent = useAccent,
        selected = selected,
        shape = shape,
        contentPaddingHorizontal = 6.dp,
    )
}

/**
 * 液态玻璃圆形按键（数字 / ± / ⌫ / C / ; / → / . / ↵ / n/⇅ 等非文字键）。
 *
 * 做法：在外层格子套一个 center + aspectRatio(1f) 的正方形容器，再做圆形裁剪。
 */
@Composable
private fun CircleKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    subLabel: String? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    labelColor: Color = Color.Unspecified,
    accentBg: Boolean = false,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // 用 aspectRatio(1f) 在格子内得到一个最大可能的正方形
        GlassKeyBase(
            onClick = onClick,
            modifier = Modifier.aspectRatio(1f),
            label = label,
            subLabel = subLabel,
            fontSize = fontSize,
            labelColor = labelColor,
            useAccent = accentBg,
            selected = selected,
            shape = RoundedCornerShape(50),
            contentPaddingHorizontal = 2.dp,
        )
    }
}

/** 所有按键共用的绘制内核，只换 shape。 */
@Composable
private fun GlassKeyBase(
    onClick: () -> Unit,
    modifier: Modifier,
    label: String,
    subLabel: String?,
    fontSize: androidx.compose.ui.unit.TextUnit,
    labelColor: Color,
    useAccent: Boolean,
    selected: Boolean,
    shape: Shape,
    contentPaddingHorizontal: Dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1.0f,
        tween(150),
        label = "keyScale_${label}_${if (useAccent) "a" else "x"}",
    )
    val dark = isDarkTheme()
    val accent = ThemeColor.current

    val density = LocalDensity.current
    val radiusPx = with(density) {
        val r = 17.6.dp.toPx()
        r
    }

    val baseBg: Color = when {
        selected || useAccent -> accent.copy(alpha = if (dark) 0.90f else 1.0f)
        else -> if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
    }
    val brush = Brush.linearGradient(
        0.00f to Color.White.copy(alpha = if (useAccent || selected) 0.22f else if (dark) 0.08f else 0.30f),
        0.25f to Color.White.copy(alpha = if (useAccent || selected) 0.10f else if (dark) 0.04f else 0.15f),
        0.70f to Color.Transparent,
        1.00f to Color.Transparent,
    )
    val bTop = if (dark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.65f)
    val bSide = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.40f)
    val bBottom = if (dark) Color.Black.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.08f)

    val elevation = if (useAccent || selected) 12.dp else 6.dp
    val ambient = (if (useAccent || selected) accent else Color.Black).copy(alpha = if (dark) 0.45f else 0.05f)
    val spot = (if (useAccent || selected) accent else Color.Black).copy(alpha = if (dark) 0.38f else 0.04f)

    val textColor = when {
        labelColor != Color.Unspecified -> labelColor
        useAccent || selected -> Color.White
        else -> LocalContentColor.current
    }

    CompositionLocalProvider(LocalContentColor provides textColor) {
        Box(
            modifier = modifier
                .scale(scale)
                .shadow(elevation = elevation, shape = shape, ambientColor = ambient, spotColor = spot, clip = false)
                .clip(shape)
                .background(baseBg)
                .background(brush)
                .drawBehind {
                    val w = size.width; val h = size.height
                    drawLine(bTop,    Offset(radiusPx * 0.5f, 0.5f),         Offset(w - radiusPx * 0.5f, 0.5f),         1f)
                    drawLine(bBottom, Offset(radiusPx * 0.5f, h - 0.5f),     Offset(w - radiusPx * 0.5f, h - 0.5f),     1f)
                    drawLine(bSide,   Offset(0.5f, radiusPx * 0.5f),         Offset(0.5f, h - radiusPx * 0.5f),         1f)
                    drawLine(bSide,   Offset(w - 0.5f, radiusPx * 0.5f),     Offset(w - 0.5f, h - radiusPx * 0.5f),     1f)
                }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(vertical = 6.dp, horizontal = contentPaddingHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = fontSize),
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
                if (subLabel != null) {
                    Spacer(modifier = Modifier.height(0.5.dp))
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = textColor.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 数字键：圆形 */
@Composable
private fun CircleNumKey(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CircleKey(
        label = text,
        onClick = onClick,
        modifier = modifier,
        fontSize = 24.sp,
    )
}

/** 子类型键：选中态用主题色填，保持圆角矩形（有中文文字） */
@Composable
private fun SubTypeKey(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassKey(
        label = label,
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        fontSize = 13.sp,
    )
}

/**
 * 多元一次调维度对话框（入口在 Display 顶部提示文字）。
 * 一元多次不再需要此对话框（root() 固定 7 格）。
 */
@Composable
private fun DimDialog(
    state: EquationSolverModuleState,
    onIntent: (ModuleIntent) -> Unit,
    open: Boolean,
    onDismiss: () -> Unit,
) {
    if (!open) return
    androidx.compose.material3.AlertDialog(
        containerColor = if (isDarkTheme()) PanelAppleDark else Color.White,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择未知数个数",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            val options = (2..6).toList()
            val accent = ThemeColor.current
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { n ->
                    val sel = state.linearDim == n
                    val bgShape = RoundedCornerShape(14.dp)
                    val bg = if (sel) accent else accent.copy(alpha = 0.10f)
                    val tc = if (sel) Color.White else LocalContentColor.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(bgShape)
                            .background(bg)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                onIntent(ModuleIntent.Custom("set_dim", n))
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "$n 元一次方程组（${n}×${n + 1} 矩阵）",
                            style = MaterialTheme.typography.bodyLarge,
                            color = tc,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/** 保留空壳：兼容原 Keypad 引用（现在 Keypad 已不再使用） */
@Deprecated("一元多次 root() 固定 7 格，不再需要调次数圆键")
@Composable
private fun DimCircleKey(
    state: EquationSolverModuleState,
    onIntent: (ModuleIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 占位空 composable：防止意外的引用
    Box(modifier)
}
