package com.example.smartcalculator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.calc.CalcMode
import com.example.smartcalculator.calc.HistoryItem
import com.example.smartcalculator.calc.displayName

/**
 * HTML `cubic-bezier(0.16, 1, 0.3, 1)` 的精确等效。
 * 用于 .transition-popover 的进场/退场：初期极慢 → 中后段猛烈加速 → 末尾柔化。
 */
private val PopOverEasing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * PopOver 抽屉容器：液态玻璃 + 缩放/淡入动画。
 *
 * 严格对齐 HTML：
 *   .transition-popover {
 *     transition: transform 220ms cubic-bezier(0.16, 1, 0.3, 1),
 *                 opacity   180ms cubic-bezier(0.16, 1, 0.3, 1);
 *   }
 *   .popover.closed { transform: scale(0.92); opacity: 0; }
 *   .popover.open   { transform: scale(1);    opacity: 1; }
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
        enter = scaleIn(
            animationSpec = tween(220, easing = PopOverEasing),
            initialScale = 0.92f,
        ) + fadeIn(tween(180, easing = PopOverEasing)),
        exit = scaleOut(
            animationSpec = tween(180, easing = PopOverEasing),
            targetScale = 0.92f,
        ) + fadeOut(tween(140, easing = PopOverEasing)),
        modifier = modifier,
    ) {
        GlassCard(
            cornerRadius = cornerRadius,
            blurRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                content()
            }
        }
    }
}

/**
 * 背景遮罩：抽屉打开时，半透明黑覆盖层（点击空白处关闭）。
 *
 * HTML：
 *   #drawer-backdrop.open {
 *     background-color: rgba(0,0,0, 0.18);
 *     pointer-events: auto !important;
 *     transition: background-color 300ms;
 *   }
 */
@Composable
fun DrawerBackdrop(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, easing = PopOverEasing)),
        exit = fadeOut(tween(300, easing = PopOverEasing)),
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

// ============================================================
//  模式抽屉（菜单）
//  HTML：id=mode-drawer  left-3 top-[72px]  w-56  p-2  rounded-md
// ============================================================
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
        modifier = modifier.width(224.dp),   // w-56 = 14rem = 224dp
        cornerRadius = 19.2.dp,              // --apple-radius-md = 1.2rem = 19.2dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {  // p-2 = 8dp
            SectionLabel(text = "模式")
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
                        Box(
                            modifier = Modifier.size(18.dp),
                            contentAlignment = Alignment.Center,
                        ) { ModeIcon(mode) }
                        Spacer(modifier = Modifier.width(12.dp))  // gap-3 = 12dp
                        Text(
                            text = mode.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),   // my-2 h-px
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                thickness = 1.dp,
            )

            GlassItemButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAbout,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center,
                    ) { AboutIcon() }
                    Spacer(modifier = Modifier.width(12.dp))   // gap-3 = 12dp
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

/**
 * 模式 / 历史 / 设置 的分组标签。
 *
 * HTML：
 *   mb-1 px-3 py-2 text-xs font-semibold uppercase tracking-wide
 *   color: var(--apple-muted-foreground)
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.text.intl.Locale.current.let { 0.08.sp },
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 8.dp, // HTML: py-2
        ),
    )
}

// ============================================================
//  历史记录抽屉
//  HTML：id=history-drawer  right-3 top-[72px]  w-56  p-2  rounded-md
// ============================================================
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
        modifier = modifier.width(224.dp),  // w-56 = 224dp
        cornerRadius = 19.2.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            SectionLabel(text = "历史记录")

            if (history.isEmpty()) {
                Text(
                    text = "暂无历史记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp), // gap-1 = 4dp
                ) {
                    items(history) { item ->
                        GlassItemButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onPick(item) },
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = item.expression,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(alpha = 0.70f),
                                )
                                Text(
                                    text = "= ${item.result}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                thickness = 1.dp,
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "清空历史",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.12.sp,
                        ),
                    )
                }
            }
        }
    }
}

// ============================================================
//  设置抽屉（极简版：仅标题 + 关闭按钮 + 空态文案）
//  HTML：id=settings-drawer  inset-x-5 bottom-5 top-[84px]
//        rounded-lg  p-4
// ============================================================
@Composable
fun SettingsDrawer(
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PopOverContainer(
        visible = visible,
        modifier = modifier,
        cornerRadius = 28.8.dp,   // --apple-radius-lg = 1.8rem = 28.8dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {  // p-4 = 16dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                // HTML: .drawer-item h-9 w-9 rounded-full（36dp圆形，带液态交互）
                val closeInteraction = remember { MutableInteractionSource() }
                val closePressed by closeInteraction.collectIsPressedAsState()
                val closeScale by animateFloatAsState(
                    if (closePressed) 0.985f else 1.0f,
                    tween(durationMillis = 150),
                    label = "closeBtnScale",
                )
                val darkClose = isDarkTheme()
                val closeBg = if (closePressed) {
                    Color.White.copy(alpha = if (darkClose) 0.30f else 0.45f)
                } else {
                    Color.Unspecified
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .scale(closeScale)
                        .clip(RoundedCornerShape(50))
                        .background(closeBg)
                        .clickable(
                            interactionSource = closeInteraction,
                            indication = null,
                            onClick = onClose,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CloseIcon(size = 18.dp, tint = MaterialTheme.colorScheme.onBackground)
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

// ===== 模式图标 / 关于图标 =====
@Composable
fun ModeIcon(mode: CalcMode) {
    when (mode) {
        CalcMode.Standard   -> Text("=", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        CalcMode.Scientific -> Text("ƒ", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        CalcMode.Programmer -> Text("<>", fontWeight = FontWeight.Bold,   fontSize = 12.sp)
        CalcMode.Statistics -> Text("Σ", fontWeight = FontWeight.Bold,   fontSize = 16.sp)
    }
}

@Composable
fun AboutIcon() {
    Text("i", fontWeight = FontWeight.Bold, fontSize = 16.sp)
}
