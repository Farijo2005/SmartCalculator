package com.example.smartcalculator.calc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.graphics.Color
import com.example.smartcalculator.ui.theme.ThemeMode
import com.example.smartcalculator.ui.theme.ThemeColorPresets
import com.example.smartcalculator.ui.theme.parseThemeColor
import com.example.smartcalculator.ui.theme.serializeThemeColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 精简版 ViewModel：只保留模式切换、历史记录、菜单顺序。
 * （计算器计算功能将在后续步骤中逐步添加）
 */
class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, 0)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    // ===== 模式切换 =====
    fun setMode(mode: CalcMode) {
        _uiState.update { it.copy(mode = mode) }
        persist()
    }

    // ===== 主题模式 =====
    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        persist()
    }

    // ===== 主题色 =====
    fun setThemeColor(color: Color) {
        _uiState.update { it.copy(themeColor = color) }
        persist()
    }

    // ===== 历史记录 =====
    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
        persist()
    }

    // ===== 菜单排序 =====
    fun moveMenuMode(from: Int, to: Int) {
        _uiState.update { s ->
            val order = s.menuOrder.toMutableList()
            if (from !in order.indices || to !in order.indices) return@update s
            val item = order.removeAt(from)
            order.add(to, item)
            s.copy(menuOrder = order)
        }
        persist()
    }

    // ===== 持久化 =====
    private fun persist() {
        val s = _uiState.value
        with(prefs.edit()) {
            putString("mode", s.mode.name)
            putString("menu_order", s.menuOrder.joinToString(",") { it.name })
            putString("theme_mode", s.themeMode.name)
            putString("theme_color", serializeThemeColor(s.themeColor))
            apply()
        }
    }

    private fun loadInitialState(): CalculatorUiState {
        val mode = prefs.getString("mode", null)
            ?.let { runCatching { CalcMode.valueOf(it) }.getOrNull() }
            ?: CalcMode.Standard
        val menuOrder = prefs.getString("menu_order", null)
            ?.split(",")
            ?.mapNotNull { runCatching { CalcMode.valueOf(it) }.getOrNull() }
            // 旧数据可能缺少新增模块，过滤无效项后用默认顺序补全
            ?.let { saved ->
                val full = CalcMode.defaultOrder
                val merged = saved.toMutableList()
                full.forEach { mode -> if (merged.none { it == mode }) merged.add(mode) }
                merged.filter { it in CalcMode.defaultOrder }
            }
            ?.takeIf { it.isNotEmpty() }
            ?: CalcMode.defaultOrder
        val themeMode = prefs.getString("theme_mode", null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.Auto
        val themeColor = parseThemeColor(prefs.getString("theme_color", null))
        return CalculatorUiState(
            mode = mode,
            menuOrder = menuOrder,
            history = emptyList(),
            themeMode = themeMode,
            themeColor = themeColor,
        )
    }

    companion object {
        private const val PREFS_NAME = "smartcalc_prefs"

        class Factory(private val app: Application) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CalculatorViewModel(app) as T
            }
        }
    }
}

/**
 * 计算器模式。
 *
 * 每个模式视为一个独立模块（独立 UI / 独立逻辑），但共用同一份历史记录，
 * 模块之间可以无缝切换 / 整合。后续如需多人协同，可在 ViewModel 层接入
 * 远端同步通道，UI 层无需改动。
 *
 * - [Standard]      标准
 * - [Scientific]     科学
 * - [Programmer]     程序员
 * - [MatlabSet]      MATLAB 集（带二级子菜单，子模块逐步接入）
 * - [Statistics]     统计
 * - [UnitConversion] 单位换算
 * - [EquationSolver] 解方程
 * - [Plotting]       绘图
 */
enum class CalcMode {
    Standard, Scientific, Programmer, MatlabSet,
    Statistics, UnitConversion, EquationSolver, Plotting;

    /** 默认菜单顺序，[ModeDrawer] 与持久化层共用。 */
    companion object {
        val defaultOrder: List<CalcMode> = listOf(
            Standard, Scientific, Programmer, MatlabSet,
            Statistics, UnitConversion, EquationSolver, Plotting,
        )
    }
}

/** 模式 → 中文显示名（菜单抽屉 & Header 共用） */
fun CalcMode.displayName(): String = when (this) {
    CalcMode.Standard       -> "标准"
    CalcMode.Scientific     -> "科学"
    CalcMode.Programmer     -> "程序员"
    CalcMode.MatlabSet      -> "MATLAB 集"
    CalcMode.Statistics     -> "统计"
    CalcMode.UnitConversion -> "单位换算"
    CalcMode.EquationSolver -> "解方程"
    CalcMode.Plotting       -> "绘图"
}

/** 该模式是否带二级子菜单。目前只有 MATLAB 集。 */
val CalcMode.hasSubMenu: Boolean
    get() = this == CalcMode.MatlabSet

/**
 * 历史记录项（暂时只用展示，后续计算功能完善后再写入）
 */
data class HistoryItem(
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * UI 状态（精简版：仅模式 / 历史 / 菜单顺序 / 主题）
 */
data class CalculatorUiState(
    val mode: CalcMode = CalcMode.Standard,
    val history: List<HistoryItem> = emptyList(),
    val menuOrder: List<CalcMode> = CalcMode.defaultOrder,
    val themeMode: ThemeMode = ThemeMode.Auto,
    val themeColor: Color = ThemeColorPresets.Blue.color,
    /**
     * 各模块私有状态（按 [CalcMode] 索引）。
     *
     * - 模块之间互不读取对方的状态，避免耦合
     * - 多人协同时每人只改自己模块的 State 类与 reducer，
     *   本字段是单一 Map 容器，**新增模块不需修改本字段定义**
     */
    val moduleStates: Map<CalcMode, com.example.smartcalculator.ui.modules.ModuleState> = emptyMap(),
)
