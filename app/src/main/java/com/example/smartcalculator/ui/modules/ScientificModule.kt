package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 科学计算器模块 —— 状态定义。
 *
 * 负责人：待分配。可参考 [StandardModule] 的实现风格。
 */
data class ScientificModuleState(
    val display: String = "0",
    val expression: String = "",
    /** 角度 / 弧度切换，true=弧度 */
    val radianMode: Boolean = true,
) : ModuleState

/**
 * 科学计算器模块入口。
 *
 * - 负责人：待分配
 * - 实现要点：三角函数 / 对数 / 指数 / 阶乘 / π / e 等
 * - 与 [StandardModule] 共享 [ModuleLayout] 与占位组件
 *
 * @param state 当前模块状态
 * @param onIntent 用户操作回调
 * @param isLandscape 是否横屏
 * @param modifier 外部位置约束
 */
@Composable
fun ScientificModule(
    state: ScientificModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    // TODO: 实现科学计算器按键与显示
    ModuleLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = { DisplayCard(Modifier.fillMaxSize()) {} },
        keypad = { KeypadCard(Modifier.fillMaxSize()) {} },
    )
}
