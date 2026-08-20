package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * MATLAB 集模块 —— 状态定义。
 *
 * 由兄弟（用户B）负责实现，请只在本文件内修改。
 *
 * 注意：[MatlabSetModule] 是 MATLAB 集的"主入口"，
 * 二级子菜单在 [MatlabSetSubMenu.kt] 中独立维护。
 */
data class MatlabSetModuleState(
    val display: String = "",
    /** 当前激活的子模块（具体子模块标识，由你定义） */
    val activeSubModule: String = "",
) : ModuleState

/**
 * MATLAB 集模块入口。
 *
 * - 负责人：用户B（兄弟）
 * - 实现要点：
 *   1. 本入口是 MATLAB 集的"主页"，显示当前子模块内容
 *   2. 子模块切换由 [MatlabSetSubMenu] 中的子菜单项触发
 *   3. 各子模块尽量独立成子文件，避免主入口过大
 *   4. 多人协同时每个子模块可单独成文件，互不干扰
 *
 * @param state 当前模块状态
 * @param onIntent 用户操作回调（包括子模块切换）
 * @param isLandscape 是否横屏
 * @param modifier 外部位置约束
 */
@Composable
fun MatlabSetModule(
    state: MatlabSetModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    // TODO: 实现当前子模块的 UI
    ModuleLayout(
        isLandscape = isLandscape,
        modifier = modifier,
        display = {
            DisplayCard(Modifier.fillMaxSize()) {
                // TODO: 显示当前子模块内容
            }
        },
        keypad = {
            KeypadCard(Modifier.fillMaxSize()) {
                // TODO: 当前子模块的交互区
            }
        },
    )
}
