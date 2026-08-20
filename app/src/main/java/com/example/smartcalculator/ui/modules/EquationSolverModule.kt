package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 解方程模块 —— 状态定义。
 *
 * 由兄弟（用户B）负责实现，请只在本文件内修改。
 */
data class EquationSolverModuleState(
    val display: String = "",
    /** 方程字符串，例如 "2x + 3 = 7" */
    val equation: String = "",
    /** 解列表（多解方程） */
    val solutions: List<String> = emptyList(),
    /** 方程类型：linear / quadratic / polynomial / system 等 */
    val equationType: String = "linear",
) : ModuleState

/**
 * 解方程模块入口。
 *
 * - 负责人：用户B（兄弟）
 * - 实现要点：
 *   1. 输入方程（一元一次 / 一元二次 / 多项式 / 方程组）
 *   2. 自动识别方程类型
 *   3. 求解并展示所有解（含复数解）
 *   4. 历史记录写入共享 [com.example.smartcalculator.calc.HistoryItem]
 *
 * @param state 当前模块状态
 * @param onIntent 用户操作回调
 * @param isLandscape 是否横屏
 * @param modifier 外部位置约束
 */
@Composable
fun EquationSolverModule(
    state: EquationSolverModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    // TODO: 实现方程输入与求解
    ModuleLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = {
            DisplayCard(Modifier.fillMaxSize()) {
                // TODO: 显示方程输入与解
            }
        },
        keypad = {
            KeypadCard(Modifier.fillMaxSize()) {
                // TODO: 输入数字 + 变量 x + 运算符 + 等号
            }
        },
    )
}
