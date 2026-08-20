package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 标准计算器模块 —— 状态定义。
 *
 * 由你（用户A）负责实现，请只在本文件内修改。
 */
data class StandardModuleState(
    val display: String = "0",
    val expression: String = "",
) : ModuleState

/**
 * 标准计算器模块入口。
 *
 * - 负责人：用户A
 * - 实现要点：
 *   1. 显示区展示当前 [StandardModuleState.display] 与 expression
 *   2. 按键区放数字键 + 四则运算 + 等号 + 清空
 *   3. 按键通过 [onIntent] 发送 [ModuleIntent.Input] / [Evaluate] / [Clear] / [Backspace]
 *   4. 历史记录通过 ViewModel 写入共享 HistoryItem 列表
 *
 * @param state 当前模块状态
 * @param onIntent 用户操作回调
 * @param isLandscape 是否横屏
 * @param modifier 外部位置约束
 */
@Composable
fun StandardModule(
    state: StandardModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    // TODO: 替换为实际显示文字与按键实现
    ModuleLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = {
            DisplayCard(Modifier.fillMaxSize()) {
                // TODO: 显示 state.display / state.expression
            }
        },
        keypad = {
            KeypadCard(Modifier.fillMaxSize()) {
                // TODO: 按键网格，点击调用 onIntent(ModuleIntent.Input("1")) 等
            }
        },
    )
}
