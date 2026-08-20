package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 统计模块 —— 状态定义。
 *
 * 负责人：待分配。可参考其他模块的实现风格。
 */
data class StatisticsModuleState(
    val display: String = "0",
    /** 已输入的数据集 */
    val dataset: List<Double> = emptyList(),
    /** 当前选中的统计量：mean / median / stddev / variance 等 */
    val selectedStat: String = "mean",
) : ModuleState

/**
 * 统计模块入口。
 *
 * - 负责人：待分配
 * - 实现要点：
 *   1. 数据输入（追加 / 删除单个值 / 清空）
 *   2. 统计量计算（均值 / 中位数 / 标准差 / 方差 / 求和 / 极值）
 *   3. 显示当前选中统计量的结果
 *
 * @param state 当前模块状态
 * @param onIntent 用户操作回调
 * @param isLandscape 是否横屏
 * @param modifier 外部位置约束
 */
@Composable
fun StatisticsModule(
    state: StatisticsModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    // TODO: 实现数据输入与统计量计算
    ModuleLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = { DisplayCard(Modifier.fillMaxSize()) {} },
        keypad = { KeypadCard(Modifier.fillMaxSize()) {} },
    )
}
