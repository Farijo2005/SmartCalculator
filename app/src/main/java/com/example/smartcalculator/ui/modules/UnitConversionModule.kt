package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 单位换算模块 —— 状态定义。
 *
 * 负责人：待分配。
 */
data class UnitConversionModuleState(
    val inputValue: String = "1",
    val fromUnit: String = "m",
    val toUnit: String = "km",
    /** 当前换算类别：length / mass / temperature / time / area / volume / speed 等 */
    val category: String = "length",
    val result: String = "0.001",
) : ModuleState

/**
 * 单位换算模块入口。
 *
 * - 负责人：待分配
 * - 实现要点：
 *   1. 类别切换（长度 / 质量 / 温度 / 时间 / 面积 / 体积 / 速度 等）
 *   2. 源单位与目标单位选择
 *   3. 输入数值实时换算
 *
 * @param state 当前模块状态
 * @param onIntent 用户操作回调
 * @param isLandscape 是否横屏
 * @param modifier 外部位置约束
 */
@Composable
fun UnitConversionModule(
    state: UnitConversionModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    // TODO: 实现单位换算
    ModuleLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = { DisplayCard(Modifier.fillMaxSize()) {} },
        keypad = { KeypadCard(Modifier.fillMaxSize()) {} },
    )
}
