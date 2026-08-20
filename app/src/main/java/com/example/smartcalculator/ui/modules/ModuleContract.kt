package com.example.smartcalculator.ui.modules

import com.example.smartcalculator.calc.CalcMode

/**
 * 模块化契约层。
 *
 * 设计原则：
 * 1. **物理隔离**：每个模块一个独立 .kt 文件，互不依赖。
 * 2. **状态隔离**：每个模块自定义 [ModuleState] 子类，互不读取。
 * 3. **意图统一**：所有模块通过 [ModuleIntent] 向 ViewModel 发请求。
 * 4. **历史共享**：所有模块写入同一份 [com.example.smartcalculator.calc.HistoryItem] 列表。
 *
 * 多人协作：每人只改自己负责的 [XxxModule].kt 文件，共享文件（本契约 +
 * ModuleLayout + ModulePlaceholders + ModuleRouter）仅在追加新模块时改一行。
 */

/**
 * 所有模块状态的标记接口。
 *
 * 每个模块自定义 data class 并实现此接口，例如：
 * ```kotlin
 * data class StandardModuleState(
 *     val display: String = "0",
 *     val expression: String = "",
 * ) : ModuleState
 * ```
 */
interface ModuleState

/**
 * 模块统一意图。模块向 ViewModel 发出的所有请求都用这个 sealed interface。
 *
 * 通用意图（输入 / 求值 / 清空 / 退格）已预定义；模块特定操作走 [Custom]。
 */
sealed interface ModuleIntent {
    /** 输入一个 token（数字、运算符、函数名等） */
    data class Input(val value: String) : ModuleIntent

    /** 请求求值 */
    object Evaluate : ModuleIntent

    /** 清空当前输入 */
    object Clear : ModuleIntent

    /** 删除最后一个字符 */
    object Backspace : ModuleIntent

    /** 模块自定义意图，避免频繁扩展 sealed 类。 */
    data class Custom(val key: String, val payload: Any? = null) : ModuleIntent
}

/**
 * 模块入口契约描述（仅作文档说明，不强制实现）。
 *
 * 每个模块在自己的文件里实现同名 @Composable，签名约定如下：
 * ```
 * @Composable
 * fun XxxModule(
 *     state: XxxModuleState,              // 模块自己的 state
 *     onIntent: (ModuleIntent) -> Unit,  // 向外发请求
 *     isLandscape: Boolean,              // 横竖屏
 *     modifier: Modifier = Modifier,     // 外部位置约束
 * )
 * ```
 *
 * 在 [ModuleRouter] 中通过 `when (mode)` 调用对应入口。
 *
 * @see ModuleRouter
 */
object ModuleContract

/**
 * 从 [moduleStates] Map 中取出指定模式的状态，类型不匹配时回退到默认值。
 *
 * 用于 [ModuleRouter] 中安全地把 [ModuleState] 转回具体子类型。
 */
inline fun <reified T : ModuleState> Map<CalcMode, ModuleState>.stateFor(
    mode: CalcMode,
    default: T,
): T = (this[mode] as? T) ?: default
