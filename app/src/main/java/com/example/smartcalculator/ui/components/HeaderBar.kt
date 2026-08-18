package com.example.smartcalculator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 顶部头部栏：左侧菜单触发器（含应用标题），右侧历史 + 设置。
 * 对应 HTML 设计稿：
 *   <header class="flex items-start justify-between p-3">
 *     <div class="menu-trigger liquid-glass rounded-[2rem] p-1.5">  <button.glass-btn h-11 w-11 /> + 标题 </div>
 *     <div class="liquid-glass rounded-[2rem] p-1.5">  <button.glass-btn h-11 w-11 /> × 2 </div>
 *   </header>
 *
 * 自动适配状态栏 / 动态岛安全区域。
 */
@Composable
fun HeaderBar(
    title: String,
    onMenuClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = statusBarTop + 12.dp,
                start = 12.dp,
                end = 12.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        // 左：菜单触发器（液态玻璃胶囊）
        GlassCard(
            cornerRadius = 32.dp,
            blurRadius = 20.dp,
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(
                    size = 44.dp,
                    onClick = onMenuClick,
                ) {
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        MenuIcon(
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }

        // 右：历史 + 设置（液态玻璃胶囊）
        GlassCard(
            cornerRadius = 32.dp,
            blurRadius = 20.dp,
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(
                    size = 44.dp,
                    onClick = onHistoryClick,
                ) {
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        HistoryIcon(
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(2.dp))
                GlassCircleButton(
                    size = 44.dp,
                    onClick = onSettingsClick,
                ) {
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        SettingsIcon(
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}
