package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 绘图模块 —— 状态定义。
 *
 * 负责人：待分配。
 */
data class PlottingModuleState(
    /** 函数表达式，例如 "sin(x)" 或 "x^2 - 4" */
    val expression: String = "",
    /** X 轴范围 [min, max] */
    val xMin: Float = -10f,
    val xMax: Float = 10f,
    val yMin: Float = -10f,
    val yMax: Float = 10f,
    /** 采样点数 */
    val samples: Int = 200,
) : ModuleState

/**
 * 绘图模块入口。
 *
 * - 负责人：待分配
 * - 实现要点：
 *   1. 函数表达式输入
 *   2. 坐标系绘制（Canvas / Compose Path）
 *   3. 范围调整 / 缩放 / 平移
 *
 * @param state 当前模块状态
 * @param onIntent 用户操作回调
 * @param isLandscape 是否横屏
 * @param modifier 外部位置约束
 */
@Composable
fun PlottingModule(
    state: PlottingModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    // TODO: 实现函数绘图
    ModuleLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = { DisplayCard(Modifier.fillMaxSize()) {} },
        keypad = { KeypadCard(Modifier.fillMaxSize()) {} },
    )
}
