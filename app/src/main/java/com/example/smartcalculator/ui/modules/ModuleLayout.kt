package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 模块统一布局 helper。
 *
 * 每个模块只需要提供 **显示区** 和 **按键区** 两段内容，
 * 横竖屏的布局差异由本组件处理——模块自身完全不用关心横竖屏。
 *
 * 用法：
 * ```kotlin
 * ModuleLayout(
 *     isLandscape = isLandscape,
 *     modifier = modifier,
 *     display = { DisplayCard(Modifier.fillMaxSize()) { /* 显示文字 */ } },
 *     keypad   = { KeypadCard(Modifier.fillMaxSize()) { /* 按键 */ } },
 * )
 * ```
 *
 * **共享文件**：所有人引用，但**不要修改**——
 * 如果布局需求不同，请在自己模块文件内自定义布局。
 */
@Composable
fun ModuleLayout(
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    display: @Composable BoxScope.() -> Unit,
    keypad: @Composable BoxScope.() -> Unit,
) {
    if (isLandscape) {
        // 横屏：左右排列，显示区在左、按键区在右，等宽
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.weight(1f), content = display)
            Box(modifier = Modifier.weight(1f), content = keypad)
        }
    } else {
        // 竖屏：上下排列，显示区在上、按键区在下
        Column(modifier = modifier) {
            Box(modifier = Modifier.fillMaxWidth(), content = display)
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth(), content = keypad)
        }
    }
}
