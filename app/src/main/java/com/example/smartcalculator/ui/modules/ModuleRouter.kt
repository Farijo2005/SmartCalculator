package com.example.smartcalculator.ui.modules

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.smartcalculator.calc.CalcMode

/**
 * 模块路由 —— 唯一的 `when (mode)` 集中点。
 *
 * 这是**共享文件**：每加入一个新模块只需追加一个 `when` 分支。
 * 不同分支调用各自的 `XxxModule(...)` 入口，互不依赖。
 *
 * 分工提示：
 * - 你（用户A）改 [Standard] / [Programmer] 分支对应的模块文件即可，
 *   本文件**仅在新增模块时改一行**。
 * - 兄弟（用户B）改 [MatlabSet] / [EquationSolver] 分支对应的模块文件即可。
 *
 * 合并冲突时：每个分支不同行号，Git 自动合并几乎零冲突；
 * 若冲突，保留双方所有分支即可。
 *
 * @param mode 当前模式
 * @param moduleStates 各模式私有状态的 Map
 * @param onIntent 模块发出的意图
 * @param isLandscape 是否横屏
 * @param modifier 外部位置约束
 */
@Composable
fun ModuleRouter(
    mode: CalcMode,
    moduleStates: Map<CalcMode, ModuleState>,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        CalcMode.Standard -> StandardModule(
            state = moduleStates.stateFor(CalcMode.Standard, StandardModuleState()),
            onIntent = onIntent,
            isLandscape = isLandscape,
            modifier = modifier,
        )

        CalcMode.Scientific -> ScientificModule(
            state = moduleStates.stateFor(CalcMode.Scientific, ScientificModuleState()),
            onIntent = onIntent,
            isLandscape = isLandscape,
            modifier = modifier,
        )

        CalcMode.Programmer -> ProgrammerModule(
            state = moduleStates.stateFor(CalcMode.Programmer, ProgrammerModuleState()),
            onIntent = onIntent,
            isLandscape = isLandscape,
            modifier = modifier,
        )

        CalcMode.MatlabSet -> MatlabSetModule(
            state = moduleStates.stateFor(CalcMode.MatlabSet, MatlabSetModuleState()),
            onIntent = onIntent,
            isLandscape = isLandscape,
            modifier = modifier,
        )

        CalcMode.Statistics -> StatisticsModule(
            state = moduleStates.stateFor(CalcMode.Statistics, StatisticsModuleState()),
            onIntent = onIntent,
            isLandscape = isLandscape,
            modifier = modifier,
        )

        CalcMode.UnitConversion -> UnitConversionModule(
            state = moduleStates.stateFor(CalcMode.UnitConversion, UnitConversionModuleState()),
            onIntent = onIntent,
            isLandscape = isLandscape,
            modifier = modifier,
        )

        CalcMode.EquationSolver -> EquationSolverModule(
            state = moduleStates.stateFor(CalcMode.EquationSolver, EquationSolverModuleState()),
            onIntent = onIntent,
            isLandscape = isLandscape,
            modifier = modifier,
        )

        CalcMode.Plotting -> PlottingModule(
            state = moduleStates.stateFor(CalcMode.Plotting, PlottingModuleState()),
            onIntent = onIntent,
            isLandscape = isLandscape,
            modifier = modifier,
        )
    }
}
