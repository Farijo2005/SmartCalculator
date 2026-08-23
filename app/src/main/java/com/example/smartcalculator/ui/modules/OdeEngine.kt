package com.example.smartcalculator.ui.modules

import kotlin.math.*

// ============================================================
//  数学表达式解析器（递归下降）
//  支持：变量 x/y，常数 pi/e，运算 + - * / ^，
//  函数 sin/cos/tan/asin/acos/atan/sinh/cosh/tanh/ln/log/exp/sqrt/abs
// ============================================================

internal object MathExprEvaluator {

    fun eval(expr: String, x: Double = 0.0, y: Double = 0.0): Double {
        if (expr.isBlank()) throw IllegalArgumentException("表达式为空")
        val tokens = tokenize(expr)
        if (tokens.isEmpty()) throw IllegalArgumentException("表达式为空")
        val parser = Parser(tokens, x, y)
        val result = parser.parseExpr()
        if (parser.pos != tokens.size) throw IllegalArgumentException("表达式不完整")
        if (result.isNaN() || result.isInfinite()) throw IllegalArgumentException("计算结果为 NaN/Inf")
        return result
    }

    // ---- Tokenizer ----
    private sealed class Tok {
        data class Num(val v: Double) : Tok()
        data class Id(val name: String) : Tok()
        object Plus : Tok(); object Minus : Tok(); object Star : Tok()
        object Slash : Tok(); object Caret : Tok()
        object LP : Tok(); object RP : Tok()
    }

    private fun tokenize(s: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val j = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    out.add(Tok.Num(s.substring(j, i).toDouble()))
                }
                c.isLetter() -> {
                    val j = i
                    while (i < s.length && s[i].isLetter()) i++
                    out.add(Tok.Id(s.substring(j, i)))
                }
                c == '+' -> { out.add(Tok.Plus); i++ }
                c == '-' -> { out.add(Tok.Minus); i++ }
                c == '*' -> { out.add(Tok.Star); i++ }
                c == '/' -> { out.add(Tok.Slash); i++ }
                c == '^' -> { out.add(Tok.Caret); i++ }
                c == '(' -> { out.add(Tok.LP); i++ }
                c == ')' -> { out.add(Tok.RP); i++ }
                else -> throw IllegalArgumentException("无法识别的字符: $c")
            }
        }
        return out
    }

    // ---- 递归下降 Parser ----
    private class Parser(val toks: List<Tok>, val x: Double, val y: Double) {
        var pos = 0

        fun parseExpr(): Double {
            var r = parseTerm()
            while (pos < toks.size) when (toks[pos]) {
                Tok.Plus -> { pos++; r += parseTerm() }
                Tok.Minus -> { pos++; r -= parseTerm() }
                else -> break
            }
            return r
        }

        private fun parseTerm(): Double {
            var r = parseFactor()
            while (pos < toks.size) when (toks[pos]) {
                Tok.Star -> { pos++; r *= parseFactor() }
                Tok.Slash -> { pos++; r /= parseFactor() }
                else -> break
            }
            return r
        }

        private fun parseFactor(): Double {
            val base = parseBase()
            if (pos < toks.size && toks[pos] == Tok.Caret) {
                pos++
                return base.pow(parseFactor())  // 右结合
            }
            return base
        }

        private fun parseBase(): Double {
            // 一元正负号
            if (pos < toks.size && toks[pos] == Tok.Minus) { pos++; return -parseBase() }
            if (pos < toks.size && toks[pos] == Tok.Plus) { pos++; return parseBase() }

            val t = toks.getOrNull(pos) ?: throw IllegalArgumentException("表达式不完整")
            return when (t) {
                is Tok.Num -> { pos++; t.v }
                is Tok.Id -> {
                    pos++
                    when (t.name.lowercase()) {
                        "x" -> x
                        "y" -> y
                        "pi" -> PI
                        "e" -> E
                        else -> {
                            // 函数调用：必须跟 (
                            if (pos < toks.size && toks[pos] == Tok.LP) {
                                pos++
                                val arg = parseExpr()
                                if (pos >= toks.size || toks[pos] != Tok.RP)
                                    throw IllegalArgumentException("函数 ${t.name} 缺少右括号")
                                pos++
                                applyFunc(t.name.lowercase(), arg)
                            } else throw IllegalArgumentException("未知标识符: ${t.name}")
                        }
                    }
                }
                Tok.LP -> {
                    pos++
                    val r = parseExpr()
                    if (pos >= toks.size || toks[pos] != Tok.RP)
                        throw IllegalArgumentException("缺少右括号")
                    pos++
                    r
                }
                else -> throw IllegalArgumentException("意外的 token: $t")
            }
        }

        private fun applyFunc(name: String, a: Double): Double = when (name) {
            "sin" -> sin(a); "cos" -> cos(a); "tan" -> tan(a)
            "asin" -> asin(a); "acos" -> acos(a); "atan" -> atan(a)
            "sinh" -> sinh(a); "cosh" -> cosh(a); "tanh" -> tanh(a)
            "ln" -> ln(a); "log" -> log10(a); "exp" -> exp(a)
            "sqrt" -> sqrt(a); "abs" -> abs(a)
            else -> throw IllegalArgumentException("未知函数: $name")
        }
    }
}

// ============================================================
//  ODE 数值求解器
//  一阶：y' = f(x, y) → RK4 / Euler
//  二阶：y'' + a(x)y' + b(x)y = f(x) → 化为一阶方程组后 RK4 / Euler
// ============================================================

enum class OdeMethod { RK4, Euler }

internal object OdeSolver {

    /** 一阶：y' = f(x, y)，返回 y(xEnd) */
    fun solve1st(
        f: (Double, Double) -> Double,
        x0: Double, y0: Double, xEnd: Double, h: Double, method: OdeMethod,
    ): Double {
        val n = ((xEnd - x0) / h).toInt().coerceAtLeast(1)
        val hh = (xEnd - x0) / n
        var x = x0; var y = y0
        repeat(n) {
            when (method) {
                OdeMethod.Euler -> {
                    y += hh * f(x, y); x += hh
                }
                OdeMethod.RK4 -> {
                    val k1 = hh * f(x, y)
                    val k2 = hh * f(x + hh / 2, y + k1 / 2)
                    val k3 = hh * f(x + hh / 2, y + k2 / 2)
                    val k4 = hh * f(x + hh, y + k3)
                    y += (k1 + 2 * k2 + 2 * k3 + k4) / 6; x += hh
                }
            }
        }
        return y
    }

    /** 二阶线性：y'' + a(x)y' + b(x)y = f(x)，返回 (y(xEnd), y'(xEnd)) */
    fun solve2nd(
        a: (Double) -> Double,
        b: (Double) -> Double,
        f: (Double) -> Double,
        x0: Double, y0: Double, dy0: Double, xEnd: Double, h: Double, method: OdeMethod,
    ): Pair<Double, Double> {
        val n = ((xEnd - x0) / h).toInt().coerceAtLeast(1)
        val hh = (xEnd - x0) / n
        var x = x0; var y1 = y0; var y2 = dy0

        fun deriv(x: Double, s1: Double, s2: Double): Pair<Double, Double> =
            s2 to (f(x) - a(x) * s2 - b(x) * s1)

        repeat(n) {
            when (method) {
                OdeMethod.Euler -> {
                    val (d1, d2) = deriv(x, y1, y2)
                    y1 += hh * d1; y2 += hh * d2; x += hh
                }
                OdeMethod.RK4 -> {
                    val (k1a, k1b) = deriv(x, y1, y2)
                    val (k2a, k2b) = deriv(x + hh / 2, y1 + hh / 2 * k1a, y2 + hh / 2 * k1b)
                    val (k3a, k3b) = deriv(x + hh / 2, y1 + hh / 2 * k2a, y2 + hh / 2 * k2b)
                    val (k4a, k4b) = deriv(x + hh, y1 + hh * k3a, y2 + hh * k3b)
                    y1 += hh * (k1a + 2 * k2a + 2 * k3a + k4a) / 6
                    y2 += hh * (k1b + 2 * k2b + 2 * k3b + k4b) / 6
                    x += hh
                }
            }
        }
        return y1 to y2
    }
}
