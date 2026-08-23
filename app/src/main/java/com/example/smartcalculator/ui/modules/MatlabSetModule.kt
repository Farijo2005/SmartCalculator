package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ============================================================
//  子模块标识 + 清单
// ============================================================

/** 子模块：解微分方程 */
internal const val SUB_ODE = "ODE"

/** MATLAB 集子模块清单（顺序 = 子菜单显示顺序），后续新增只需追加一项 */
internal data class MatlabSubModule(val id: String, val label: String)

internal val MATLAB_SUB_MODULES: List<MatlabSubModule> = listOf(
    MatlabSubModule(SUB_ODE, "解微分方程"),
)

/**
 * MATLAB 集模块 —— 状态定义。
 *
 * 主入口 [MatlabSetModule] 根据 [MatlabSetModuleState.activeSubModule]
 * 渲染对应子模块；二级子菜单在 [MatlabSetSubMenu.kt] 中独立维护。
 */
data class MatlabSetModuleState(
    val display: String = "",
    /** 当前激活的子模块（对应 [MATLAB_SUB_MODULES] 里的 id；空串 = 尚未选择） */
    val activeSubModule: String = "",
    /** ODE 子模块状态 */
    val odeState: OdeSubModuleState = OdeSubModuleState(),
) : ModuleState

/**
 * MATLAB 集 reducer：处理子模块切换等意图。
 *
 * - `Custom("subModule", subId)`：切到指定子模块（由子菜单点击触发）
 */
internal fun reduceMatlabSet(
    state: MatlabSetModuleState,
    intent: ModuleIntent,
): MatlabSetModuleState = when (intent) {
    is ModuleIntent.Custom -> when (intent.key) {
        "subModule" -> state.copy(
            activeSubModule = intent.payload as? String ?: "",
        )
        else -> state.copy(odeState = reduceOde(state.odeState, intent))
    }
    else -> state.copy(odeState = reduceOde(state.odeState, intent))
}

/**
 * MATLAB 集模块入口。
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
    when (state.activeSubModule) {
        SUB_ODE -> OdeSubModule(state.odeState, onIntent, isLandscape, modifier)
        else -> ModuleLayout(
            isLandscape = isLandscape,
            modifier = modifier,
            display = {
                DisplayCard(Modifier.fillMaxSize()) { SubModuleEmptyHint() }
            },
            keypad = {
                KeypadCard(Modifier.fillMaxSize()) { SubModuleKeypadHint() }
            },
        )
    }
}

/** 尚未选择子模块时的空态提示 */
@Composable
private fun SubModuleEmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "请从菜单 → MATLAB 集中选择子模块",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 按键区占位提示 */
@Composable
private fun SubModuleKeypadHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "交互区建设中",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
