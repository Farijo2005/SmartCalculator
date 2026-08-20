package com.example.smartcalculator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.calc.CalcMode
import com.example.smartcalculator.calc.HistoryItem
import com.example.smartcalculator.calc.displayName
import com.example.smartcalculator.calc.hasSubMenu
import com.example.smartcalculator.ui.theme.ThemeMode
import com.example.smartcalculator.ui.theme.Text200
import com.example.smartcalculator.ui.theme.ThemeColorPresets
import com.example.smartcalculator.ui.theme.ThemeColorPreset
import com.example.smartcalculator.ui.components.AddIcon
import com.example.smartcalculator.ui.components.ChevronRightIcon

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
    // MATLAB 集二级子菜单展开状态（独立记忆，不影响其他模块）
    var matlabExpanded by rememberSaveable { mutableStateOf(false) }

    PopOverContainer(
        visible = visible,
        modifier = modifier.width(224.dp),   // w-56 = 14rem = 224dp
        cornerRadius = 19.2.dp,              // --apple-radius-md = 1.2rem = 19.2dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {  // p-2 = 8dp
            SectionLabel(text = "模式")
            menuOrder.forEach { mode ->
                // —— 普通模块：点击切换模式 ——
                // —— MATLAB 集：点击仅展开/收起二级子菜单（互不干扰） ——
                val isSubMenuMode = mode.hasSubMenu
                val selected = !isSubMenuMode && mode == currentMode
                val chevronRotation by animateFloatAsState(
                    if (matlabExpanded && isSubMenuMode) 90f else 0f,
                    tween(durationMillis = 220, easing = PopOverEasing),
                    label = "matlabChevron",
                )

                GlassItemButton(
                    modifier = Modifier.fillMaxWidth(),
                    selected = selected,
                    onClick = {
                        if (isSubMenuMode) {
                            matlabExpanded = !matlabExpanded
                        } else {
                            onPickMode(mode)
                        }
                    },
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
                            modifier = Modifier.weight(1f),
                        )
                        if (isSubMenuMode) {
                            // 二级子菜单指示箭头（旋转 90° 表示展开）
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(chevronRotation),
                                contentAlignment = Alignment.Center,
                            ) {
                                ChevronRightIcon(
                                    size = 18.dp,
                                    tint = LocalContentColor.current.copy(alpha = 0.60f),
                                )
                            }
                        }
                    }
                }

                // —— MATLAB 集二级子菜单（内容占位，后续逐步接入） ——
                if (isSubMenuMode) {
                    AnimatedVisibility(
                        visible = matlabExpanded,
                        enter = expandVertically(
                            animationSpec = tween(220, easing = PopOverEasing),
                        ) + fadeIn(tween(180)),
                        exit = shrinkVertically(
                            animationSpec = tween(180, easing = PopOverEasing),
                        ) + fadeOut(tween(140)),
                    ) {
                        MatlabSetSubMenu()
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
 * MATLAB 集二级子菜单。
 *
 * - 设计原则：每个子模块视为独立程序，但与所有其他模块共用同一份历史记录
 *   （由 [CalculatorViewModel] 单一来源维护），切换不丢失上下文。
 * - 多人协同：子模块之间状态隔离，后续可在 ViewModel 层接入远端同步通道，
 *   UI 层无需改动即可支持多人同时在线编程。
 * - 内容占位：当前仅展示容器结构，具体子模块按需逐步接入。
 */
@Composable
private fun MatlabSetSubMenu() {
    val dark = isDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
    ) {
        // 子模块逐步接入时的占位提示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "·",
                    color = if (dark) Text200.copy(alpha = 0.50f)
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    fontSize = 12.sp,
                )
            }
            Text(
                text = "子模块逐步接入中",
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) Text200.copy(alpha = 0.55f)
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
            )
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
private fun SectionLabel(
    text: String,
    style: TextStyle? = null,
) {
    val dark = isDarkTheme()
    Text(
        text = text,
        style = style ?: MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.text.intl.Locale.current.let { 0.08.sp },
        ),
        color = if (dark) Text200 else MaterialTheme.colorScheme.onSurfaceVariant,
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
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    themeColor: Color,
    onSetThemeColor: (Color) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomColorDialog by rememberSaveable { mutableStateOf(false) }

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
            SectionLabel(
                text = "外观",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )

            // 三选一分段控件（液态玻璃风格）
            ThemeModeSelector(
                current = themeMode,
                onPick = onSetThemeMode,
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(
                text = "主题色",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )

            // 预设色 + 自定义按钮
            ThemeColorSelector(
                current = themeColor,
                onPick = onSetThemeColor,
                onCustomClick = { showCustomColorDialog = true },
            )
        }
    }

    // 自定义颜色弹窗（液态玻璃风格）
    if (showCustomColorDialog) {
        CustomColorDialog(
            onConfirm = { color ->
                onSetThemeColor(color)
                showCustomColorDialog = false
            },
            onDismiss = { showCustomColorDialog = false },
        )
    }
}

/**
 * 主题模式分段选择器：浅色 / 深色 / 跟随系统
 */
@Composable
private fun ThemeModeSelector(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
) {
    val dark = isDarkTheme()
    val options = listOf(
        ThemeMode.Light to "浅色",
        ThemeMode.Dark to "深色",
        ThemeMode.Auto to "跟随系统",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.4.dp))
            .background(
                if (dark) Color(0xFFFFFFFF).copy(alpha = 0.06f)
                else Color(0xFF000000).copy(alpha = 0.05f),
            ),
    ) {
        options.forEach { (mode, label) ->
            val selected = mode == current
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                if (pressed) 0.97f else 1.0f,
                tween(120),
                label = "themeOptScale",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(11.2.dp))
                    .background(
                        when {
                            selected && dark -> Color(0xFFFFFFFF).copy(alpha = 0.15f)
                            selected -> Color(0xFF000000).copy(alpha = 0.08f)
                            else -> Color.Transparent
                        },
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onPick(mode) },
                    )
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = when {
                        selected -> MaterialTheme.colorScheme.onBackground
                        dark -> Text200
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

// ===== 模式图标 / 关于图标 =====
@Composable
fun ModeIcon(mode: CalcMode) {
    when (mode) {
        CalcMode.Standard       -> Text("=",  fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        CalcMode.Scientific     -> Text("ƒ",  fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        CalcMode.Programmer     -> Text("<>", fontWeight = FontWeight.Bold,    fontSize = 12.sp)
        CalcMode.MatlabSet      -> Text("M",  fontWeight = FontWeight.Bold,    fontSize = 14.sp)
        CalcMode.Statistics     -> Text("Σ",  fontWeight = FontWeight.Bold,    fontSize = 16.sp)
        CalcMode.UnitConversion -> Text("⇄",  fontWeight = FontWeight.Bold,    fontSize = 16.sp)
        CalcMode.EquationSolver -> Text("x",  fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        CalcMode.Plotting       -> Text("∿",  fontWeight = FontWeight.Bold,    fontSize = 16.sp)
    }
}

@Composable
fun AboutIcon() {
    Text("i", fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

// ============================================================
//  主题色选择器
// ============================================================

/**
 * 主题色选择器：预设色圆点 + "自定义"按钮。
 */
@Composable
private fun ThemeColorSelector(
    current: Color,
    onPick: (Color) -> Unit,
    onCustomClick: () -> Unit,
) {
    val dark = isDarkTheme()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ThemeColorPresets.all.forEach { preset ->
            val selected = preset.color.toArgb() == current.toArgb()
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                if (pressed) 0.90f else 1.0f,
                tween(120),
                label = "colorScale_${preset.id}",
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(preset.color)
                    .then(
                        if (selected) {
                            Modifier.border(
                                width = 3.dp,
                                color = Color.White,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (selected) {
                            Modifier.border(
                                width = 1.dp,
                                color = preset.color,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onPick(preset.color) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text(
                        text = "\u2713",  // ✓
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // 自定义颜色按钮 —— 圆形 + 号图标
        val customInteraction = remember { MutableInteractionSource() }
        val customPressed by customInteraction.collectIsPressedAsState()
        val customScale by animateFloatAsState(
            if (customPressed) 0.90f else 1.0f,
            tween(120),
            label = "customColorBtnScale",
        )

        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(customScale)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (dark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.30f),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = customInteraction,
                    indication = null,
                    onClick = onCustomClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AddIcon(
                size = 18.dp,
                tint = if (dark) Text200 else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============================================================
//  自定义颜色弹窗（液态玻璃风格）
// ============================================================

/**
 * 自定义颜色弹窗：
 *   - 苹果风格高斯模糊 + 液态玻璃容器
 *   - Hex 输入框（#RRGGBB）
 *   - 实时预览
 *   - 液态玻璃风格的确认 / 取消按钮
 *   - 安卓返回键 = 取消
 */
@Composable
private fun CustomColorDialog(
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = isDarkTheme()

    var hexInput by rememberSaveable { mutableStateOf("#") }

    val parsedColor: Color? = remember(hexInput) {
        val clean = hexInput.removePrefix("#").trim()
        if (clean.length == 6) {
            runCatching { Color(0xFF000000L or clean.toLong(16)) }.getOrNull()
        } else if (clean.length == 8) {
            runCatching { Color(clean.toLong(16)) }.getOrNull()
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.40f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BackHandler(enabled = true) { onDismiss() }

        PopOverContainer(
            visible = true,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .width(300.dp),
            cornerRadius = 19.2.dp,
        ) {
            val dialogBg = if (dark) Color(0xFF38414D).copy(alpha = 0.82f) else Color(0xFFFFFFFF).copy(alpha = 0.88f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(dialogBg),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "自定义主题色",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = if (dark) Text200 else MaterialTheme.colorScheme.onBackground,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Hex 色码",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dark) Text200 else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { value ->
                            val filtered = value.filter { it == '#' || it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                            hexInput = if (filtered.isEmpty() || filtered == "#") "#" else filtered.take(7)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.4.dp)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (dark) Text200 else MaterialTheme.colorScheme.onSurface,
                        ),
                        isError = parsedColor == null && hexInput.length > 1,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (dark) Color(0xFF444E59) else Color(0xFFF7F7FA),
                            unfocusedContainerColor = if (dark) Color(0xFF3E4752) else Color(0xFFF2F2F7),
                            errorContainerColor = if (dark) Color(0xFF4A3838) else Color(0xFFFFECEA),
                            focusedBorderColor = if (dark) Color.White.copy(alpha = 0.50f) else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (dark) Color.White.copy(alpha = 0.25f) else Color(0xFFD1D1D6),
                            errorBorderColor = Color(0xFFFF453A),
                            cursorColor = if (dark) Color(0xFF66ABFF) else MaterialTheme.colorScheme.primary,
                        ),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "预览",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dark) Text200 else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(parsedColor ?: Color.Gray.copy(alpha = 0.3f))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.3f),
                                    RoundedCornerShape(10.dp),
                                ),
                        )
                        if (parsedColor != null) {
                            Text(
                                text = "RGB(${
                                    (parsedColor.toArgb() shr 16) and 0xFF
                                }, ${(parsedColor.toArgb() shr 8) and 0xFF}, ${parsedColor.toArgb() and 0xFF})",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (dark) Text200 else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassActionButton(
                            text = "取消",
                            enabled = true,
                            isAccent = false,
                            onClick = onDismiss,
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        GlassActionButton(
                            text = "确认",
                            enabled = parsedColor != null,
                            isAccent = true,
                            accentColor = parsedColor ?: ThemeColorPresets.Blue.color,
                            onClick = { parsedColor?.let { onConfirm(it) } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 液态玻璃风格操作按钮 —— 用于弹窗的确认/取消按钮。
 * 对齐 HTML .liquid-glass 结构，支持按压缩放反馈。
 */
@Composable
private fun GlassActionButton(
    text: String,
    enabled: Boolean,
    isAccent: Boolean,
    accentColor: Color = Color.Blue,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1.0f,
        tween(durationMillis = 150),
        label = "actionBtnScale",
    )
    val dark = isDarkTheme()

    val bgAlpha = when {
        !enabled -> if (dark) 0.18f else 0.25f
        pressed -> if (dark) 0.35f else 0.52f
        else -> if (dark) 0.22f else 0.35f
    }
    val bg = if (isAccent && enabled) accentColor.copy(alpha = if (pressed) 0.85f else 0.72f)
             else Color(0xFFFFFFFF).copy(alpha = bgAlpha)

    val borderAlpha = if (isAccent && enabled) 0.55f else if (dark) 0.22f else 0.50f
    val borderColor = if (isAccent && enabled) Color.White.copy(alpha = borderAlpha)
                      else Color(0xFFFFFFFF).copy(alpha = borderAlpha)

    val textColor = when {
        !enabled -> if (dark) Text200.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isAccent -> Color.White
        else -> if (dark) Text200 else MaterialTheme.colorScheme.onSurfaceVariant
    }

    val elevation = if (pressed) 2.dp else 4.dp

    Box(
        modifier = Modifier
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(12.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.40f else 0.08f),
                spotColor = Color.Black.copy(alpha = if (dark) 0.30f else 0.05f),
            )
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .drawBehind {
                val inset = 8f
                drawLine(
                    color = Color.White.copy(alpha = if (dark) 0.18f else 0.55f),
                    start = Offset(inset, 0.5f),
                    end = Offset(size.width - inset, 0.5f),
                    strokeWidth = 1f,
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isAccent) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = textColor,
        )
    }
}
