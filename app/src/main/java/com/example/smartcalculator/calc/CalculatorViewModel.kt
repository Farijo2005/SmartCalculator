package com.example.smartcalculator.calc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(CalcMode.Standard, CalcMode.Scientific, CalcMode.Programmer, CalcMode.Statistics)
        return CalculatorUiState(
            mode = mode,
            menuOrder = menuOrder,
            history = emptyList(),
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
 * 计算器模式
 */
enum class CalcMode { Standard, Scientific, Programmer, Statistics }

/** 模式 → 中文显示名（菜单抽屉 & Header 共用） */
fun CalcMode.displayName(): String = when (this) {
    CalcMode.Standard   -> "标准"
    CalcMode.Scientific -> "科学"
    CalcMode.Programmer -> "程序员"
    CalcMode.Statistics -> "统计"
}

/**
 * 历史记录项（暂时只用展示，后续计算功能完善后再写入）
 */
data class HistoryItem(
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * UI 状态（精简版：仅模式 / 历史 / 菜单顺序）
 */
data class CalculatorUiState(
    val mode: CalcMode = CalcMode.Standard,
    val history: List<HistoryItem> = emptyList(),
    val menuOrder: List<CalcMode> = listOf(
        CalcMode.Standard,
        CalcMode.Scientific,
        CalcMode.Programmer,
        CalcMode.Statistics,
    ),
)
