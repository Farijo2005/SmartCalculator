package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 程序员计算器模块 —— 状态定义。
 *
 * 由你（用户A）负责实现，请只在本文件内修改。
 */
data class ProgrammerModuleState(
    val display: String = "0",
    /** 当前进制：2 / 8 / 10 / 16 */
    val radix: Int = 10,
    /** 当前输入的二进制位掩码（按位运算用） */
    val bits: Long = 0L,
) : ModuleState

/**
 * 程序员计算器模块入口。
 *
 * - 负责人：用户A
 * - 实现要点：
 *   1. 进制切换（BIN / OCT / DEC / HEX）
 *   2. 位运算（AND / OR / NOT / XOR / << / >>）
 *   3. 显示当前进制的字符串表示
 *   4. 通过 [onIntent] 上报操作，[ModuleIntent.Custom] 可携带 key="radix" 等
 *
 * @param state 当前模块状态
 * @param onIntent 用户操作回调
 * @param isLandscape 是否横屏
 * @param modifier 外部位置约束
 */
@Composable
fun ProgrammerModule(
    state: ProgrammerModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    // TODO: 实现进制切换与位运算
    ModuleLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = {
            DisplayCard(Modifier.fillMaxSize()) {
                // TODO: 显示当前进制下的 state.display
            }
        },
        keypad = {
            KeypadCard(Modifier.fillMaxSize()) {
                // TODO: 进制切换按钮 + 数字键 + 位运算键
            }
        },
    )
}
