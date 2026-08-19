package com.example.smartcalculator.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 主题色 —— 全局可"变量化"引用的强调色。
 *
 * 设计意图：
 *   - 预设色（苹果风格）：Blue / Orange / Green / Purple / Pink
 *   - 自定义色：用户通过 RGB/Hex 输入
 *   - 持久化为字符串（预设 id 或 #RRGGBB）
 *   - 通过 [LocalThemeColor] CompositionLocal 像变量一样在任意 Composable 中使用：
 *
 *       val accent = LocalThemeColor.current   // 即取即用
 *       Box(modifier = Modifier.background(accent))
 *
 *   - 预设清单在 [ThemeColorPresets] 中集中维护，扩展时只需加一行。
 */

// ──────────────────────────────────────────────────────
//  数据模型
// ──────────────────────────────────────────────────────

/**
 * 单个预设主题色。
 *
 * @param id       持久化标识（稳定字符串，改名不影响已存储的偏好）
 * @param name     展示名
 * @param color    Compose Color 值
 */
data class ThemeColorPreset(
    val id: String,
    val name: String,
    val color: Color,
)

/**
 * 预设主题色清单 —— 扩展时只需在此追加一行。
 */
object ThemeColorPresets {
    val Blue   = ThemeColorPreset("blue",   "蓝色",   Color(0xFF007AFF))
    val Orange = ThemeColorPreset("orange", "橙色",   Color(0xFFFF9500))
    val Green  = ThemeColorPreset("green",  "绿色",   Color(0xFF34C759))
    val Purple = ThemeColorPreset("purple", "紫色",   Color(0xFFAF52DE))
    val Pink   = ThemeColorPreset("pink",   "粉色",   Color(0xFFFF2D55))

    /** 全部预设色（顺序即展示顺序） */
    val all: List<ThemeColorPreset> = listOf(Blue, Orange, Green, Purple, Pink)

    /** 按 id 查找预设色，找不到返回 null */
    fun findById(id: String): ThemeColorPreset? = all.firstOrNull { it.id == id }
}

// ──────────────────────────────────────────────────────
//  序列化辅助
// ──────────────────────────────────────────────────────

/**
 * 将持久化字符串解析为 Color。
 *
 * 规则：
 *   - 以 "#" 开头 → 自定义 Hex（#RRGGBB 或 #AARRGGBB）
 *   - 其他 → 预设 id
 *   - 解析失败 → 回退到默认（蓝色）
 */
fun parseThemeColor(stored: String?): Color {
    if (stored == null) return ThemeColorPresets.Blue.color
    if (stored.startsWith("#")) {
        return runCatching { Color(stored.removePrefix("#").toLong(16)) }
            .getOrDefault(ThemeColorPresets.Blue.color)
    }
    return ThemeColorPresets.findById(stored)?.color ?: ThemeColorPresets.Blue.color
}

/**
 * 将 Color 序列化为可持久化字符串。
 *
 * 自定义色序列化为 "#RRGGBB"（不含 alpha，因为主题色默认全不透明）。
 * 若颜色恰好等于某预设色，则序列化为预设 id（更紧凑、可读）。
 */
fun serializeThemeColor(color: Color): String {
    // 优先匹配预设 id
    val preset = ThemeColorPresets.all.firstOrNull { it.color.toArgb() == color.toArgb() }
    if (preset != null) return preset.id
    // 自定义：输出 #RRGGBB
    val argb = color.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "#%02X%02X%02X".format(r, g, b)
}

// ──────────────────────────────────────────────────────
//  CompositionLocal —— 主题色变量
// ──────────────────────────────────────────────────────

/**
 * 当前主题色 —— 在 UI 树中像变量一样直接使用：
 *
 * ```
 * val accent = LocalThemeColor.current
 * Box(modifier = Modifier.background(accent))
 * ```
 *
 * 由 [MainActivity] 在顶层 provide，所有子组件均可读取。
 */
val LocalThemeColor = compositionLocalOf { ThemeColorPresets.Blue.color }

/**
 * 便捷访问器 —— 等价于 `LocalThemeColor.current`，
 * 但更像"变量"调用，可读性更好。
 */
object ThemeColor {
    val current: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeColor.current
}
