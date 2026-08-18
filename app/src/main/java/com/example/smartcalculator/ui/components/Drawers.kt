package com.example.smartcalculator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.calc.CalcMode
import com.example.smartcalculator.calc.HistoryItem

/**
 * PopOver 抽屉容器：液态玻璃 + 缩放/淡入动画。
 */
@Composable
fun PopOverContainer(
    visible: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 19.2.dp,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = tween(220), initialScale = 0.92f) + fadeIn(tween(180)),
        exit = scaleOut(animationSpec = tween(180), targetScale = 0.92f) + fadeOut(tween(140)),
        modifier = modifier,
    ) {
        GlassCard(
            cornerRadius = cornerRadius,
            blurRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                content()
            }
        }
    }
}

/**
 * 背景遮罩：抽屉打开时点击空白处关闭。
 */
@Composable
fun DrawerBackdrop(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220)),
        modifier = modifier.fillMaxSize(),
    ) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
}

// ===== 模式抽屉 =====
@Composable
fun ModeDrawer(
    visible: Boolean,
    menuOrder: List<CalcMode>,
    currentMode: CalcMode,
    onPickMode: (CalcMode) -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PopOverContainer(
        visible = visible,
        modifier = modifier.width(220.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            SectionLabel(text = "模式")
            Spacer(modifier = Modifier.height(4.dp))
            menuOrder.forEach { mode ->
                GlassItemButton(
                    modifier = Modifier.fillMaxWidth(),
                    selected = mode == currentMode,
                    onClick = { onPickMode(mode) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            ModeIcon(mode)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = mode.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (mode == currentMode) {
                            Text(
                                "✓",
                                color = LocalContentColor.current.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
            GlassItemButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAbout,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        AboutIcon()
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "关于",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

// ===== 历史记录抽屉 =====
@Composable
fun HistoryDrawer(
    visible: Boolean,
    history: List<HistoryItem>,
    onPick: (HistoryItem) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PopOverContainer(
        visible = visible,
        modifier = modifier.width(260.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            SectionLabel(text = "历史记录")
            if (history.isEmpty()) {
                Text(
                    text = "暂无历史记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(history) { item ->
                        GlassItemButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onPick(item) },
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            ) {
                                Text(
                                    text = item.expression,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = "= ${item.result}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LocalContentColor.current,
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
            GlassItemButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClear,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeleteIcon(size = 14.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "清空历史",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

// ===== 设置抽屉（极简版：仅标题 + 关闭按钮） =====
@Composable
fun SettingsDrawer(
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PopOverContainer(
        visible = visible,
        modifier = modifier.padding(20.dp),
        cornerRadius = 28.8.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                GlassCircleButton(size = 36.dp, onClick = onClose) {
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        CloseIcon(size = 18.dp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "暂无可配置项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ===== 模式图标 =====
@Composable
fun ModeIcon(mode: CalcMode) {
    when (mode) {
        CalcMode.Standard -> Text("=", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        CalcMode.Scientific -> Text("ƒ", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        CalcMode.Programmer -> Text("</>", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        CalcMode.Statistics -> Text("Σ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun AboutIcon() {
    Text("i", fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

fun CalcMode.displayName(): String = when (this) {
    CalcMode.Standard -> "标准"
    CalcMode.Scientific -> "科学"
    CalcMode.Programmer -> "程序员"
    CalcMode.Statistics -> "统计"
}
