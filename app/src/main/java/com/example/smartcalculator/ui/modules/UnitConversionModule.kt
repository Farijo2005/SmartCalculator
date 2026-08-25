package com.example.smartcalculator.ui.modules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.ui.components.GlassCircleButton
import com.example.smartcalculator.ui.components.isDarkTheme
import com.example.smartcalculator.ui.theme.PanelAppleDark
import com.example.smartcalculator.ui.theme.Text200
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// =========================================================================
//  单位换算数据层
// =========================================================================

/** 单位定义：[key] 内部标识，[label] 显示名（含单位缩写），[factor] 相对基准单位的倍率 */
private data class UnitDef(
    val key: String,
    val label: String,
    /** 线性换算：value * factor = 基准值；温度用 [toBase]/[fromBase] 函数计算 */
    val factor: Double = 1.0,
    val toBase: ((Double) -> Double)? = null,
    val fromBase: ((Double) -> Double)? = null,
)

/** 类别：[key] 标识，[name] 显示名，[baseKey] 基准单位 key */
private data class UnitCategory(
    val key: String,
    val name: String,
    val units: List<UnitDef>,
    val baseKey: String,
)

private fun u(k: String, label: String, factor: Double) = UnitDef(k, label, factor)

/** 全部 12 个类别与对应单位 */
private val UC_CATEGORIES: List<UnitCategory> = listOf(
    UnitCategory("length", "长度", listOf(
        u("m", "米 (m)", 1.0),
        u("km", "千米 (km)", 1000.0),
        u("cm", "厘米 (cm)", 0.01),
        u("mm", "毫米 (mm)", 0.001),
        u("um", "微米 (µm)", 1e-6),
        u("nm", "纳米 (nm)", 1e-9),
        u("mile", "英里 (mi)", 1609.344),
        u("yard", "码 (yd)", 0.9144),
        u("ft", "英尺 (ft)", 0.3048),
        u("in", "英寸 (in)", 0.0254),
        u("nmi", "海里 (nmi)", 1852.0),
        u("li", "里", 500.0),
        u("zhang", "丈", 3.3333333),
        u("chi", "尺", 0.3333333),
        u("cun", "寸", 0.03333333),
    ), "m"),
    UnitCategory("mass", "质量", listOf(
        u("kg", "千克 (kg)", 1.0),
        u("g", "克 (g)", 0.001),
        u("mg", "毫克 (mg)", 1e-6),
        u("t", "吨 (t)", 1000.0),
        u("lb", "磅 (lb)", 0.45359237),
        u("oz", "盎司 (oz)", 0.028349523125),
        u("st", "英石 (st)", 6.35029318),
        u("jin", "斤", 0.5),
        u("liang", "两", 0.05),
        u("qian", "钱", 0.005),
    ), "kg"),
    // 温度：非线性
    UnitCategory("temperature", "温度", listOf(
        UnitDef("c", "摄氏度 (°C)", toBase = { it }, fromBase = { it }),
        UnitDef("f", "华氏度 (°F)",
            toBase = { (it - 32.0) * 5.0 / 9.0 },
            fromBase = { it * 9.0 / 5.0 + 32.0 }),
        UnitDef("k", "开尔文 (K)",
            toBase = { it - 273.15 },
            fromBase = { it + 273.15 }),
        UnitDef("r", "兰氏度 (°R)",
            toBase = { (it - 491.67) * 5.0 / 9.0 },
            fromBase = { it * 9.0 / 5.0 + 491.67 }),
    ), "c"),
    UnitCategory("time", "时间", listOf(
        u("s", "秒 (s)", 1.0),
        u("ms", "毫秒 (ms)", 0.001),
        u("us", "微秒 (µs)", 1e-6),
        u("ns", "纳秒 (ns)", 1e-9),
        u("min", "分 (min)", 60.0),
        u("h", "时 (h)", 3600.0),
        u("d", "天 (d)", 86400.0),
        u("wk", "周 (wk)", 604800.0),
        u("mon", "月 (30d)", 2_592_000.0),
        u("yr", "年 (yr)", 31_536_000.0),
    ), "s"),
    UnitCategory("area", "面积", listOf(
        u("m2", "平方米 (m²)", 1.0),
        u("km2", "平方千米 (km²)", 1e6),
        u("cm2", "平方厘米 (cm²)", 1e-4),
        u("mm2", "平方毫米 (mm²)", 1e-6),
        u("ha", "公顷 (ha)", 1e4),
        u("mu", "亩", 666.6667),
        u("ft2", "平方英尺 (ft²)", 0.09290304),
        u("in2", "平方英寸 (in²)", 6.4516e-4),
        u("mi2", "平方英里 (mi²)", 2_589_988.11),
        u("acre", "英亩 (acre)", 4046.8564224),
    ), "m2"),
    UnitCategory("volume", "体积 / 容积", listOf(
        u("m3", "立方米 (m³)", 1.0),
        u("l", "升 (L)", 0.001),
        u("ml", "毫升 (mL)", 1e-6),
        u("cm3", "立方厘米 (cm³)", 1e-6),
        u("mm3", "立方毫米 (mm³)", 1e-9),
        u("km3", "立方千米 (km³)", 1e9),
        u("gal_us", "美制加仑 (gal)", 0.003785411784),
        u("gal_uk", "英制加仑 (gal)", 0.00454609),
        u("qt", "夸脱 (qt)", 0.000946352946),
        u("pt", "品脱 (pt)", 0.000473176473),
        u("cup", "杯 (cup)", 0.0002365882365),
        u("floz_us", "液盎司 (fl oz)", 2.95735295625e-5),
    ), "m3"),
    UnitCategory("speed", "速度", listOf(
        u("mps", "米/秒 (m/s)", 1.0),
        u("kmh", "千米/时 (km/h)", 1.0 / 3.6),
        u("mph", "英里/时 (mph)", 0.44704),
        u("knot", "节 (kn)", 1852.0 / 3600.0),
        u("fps", "英尺/秒 (ft/s)", 0.3048),
        u("mach", "马赫 (Ma, 0°C)", 331.3),
        u("c", "光速 (c)", 299_792_458.0),
    ), "mps"),
    UnitCategory("storage", "数据存储", listOf(
        u("b", "位 (bit)", 1.0),
        u("B", "字节 (B)", 8.0),
        u("KB", "千字节 (KB)", 8.0 * 1024),
        u("MB", "兆字节 (MB)", 8.0 * 1024 * 1024),
        u("GB", "吉字节 (GB)", 8.0 * 1024 * 1024 * 1024),
        u("TB", "太字节 (TB)", 8.0 * Math.pow(1024.0, 4.0)),
        u("PB", "拍字节 (PB)", 8.0 * Math.pow(1024.0, 5.0)),
        u("KIB", "KiB", 8.0 * 1024),
        u("MIB", "MiB", 8.0 * 1024 * 1024),
        u("GIB", "GiB", 8.0 * 1024 * 1024 * 1024),
    ), "b"),
    UnitCategory("energy", "能量", listOf(
        u("j", "焦耳 (J)", 1.0),
        u("kj", "千焦 (kJ)", 1000.0),
        u("cal", "卡路里 (cal)", 4.184),
        u("kcal", "大卡 (kcal)", 4184.0),
        u("wh", "瓦时 (Wh)", 3600.0),
        u("kwh", "千瓦时 (kWh)", 3_600_000.0),
        u("ev", "电子伏 (eV)", 1.602_176_634e-19),
        u("btu", "英热单位 (BTU)", 1055.055_852_62),
        u("ftlb", "英尺磅 (ft·lb)", 1.355_817_948_331_4),
    ), "j"),
    UnitCategory("pressure", "压强", listOf(
        u("pa", "帕斯卡 (Pa)", 1.0),
        u("kpa", "千帕 (kPa)", 1000.0),
        u("mpa", "兆帕 (MPa)", 1_000_000.0),
        u("bar", "巴 (bar)", 100_000.0),
        u("atm", "标准大气压 (atm)", 101_325.0),
        u("psi", "磅/平方英寸 (psi)", 6894.757),
        u("mmhg", "毫米汞柱 (mmHg)", 133.322_387_415),
        u("inhg", "英寸汞柱 (inHg)", 3386.389),
        u("cmh2o", "厘米水柱 (cmH₂O)", 98.0665),
        u("kgcm2", "千克力/厘米² (kgf/cm²)", 98_066.5),
    ), "pa"),
    UnitCategory("power", "功率", listOf(
        u("w", "瓦 (W)", 1.0),
        u("kw", "千瓦 (kW)", 1000.0),
        u("mw", "兆瓦 (MW)", 1_000_000.0),
        u("hp_mech", "机械马力 (hp)", 745.699_871_582_27),
        u("hp_metric", "公制马力 (PS)", 735.498_75),
        u("hp_elec", "电气马力", 746.0),
        u("btuh", "英热单位/时 (BTU/h)", 0.293_071),
        u("kcalh", "千卡/时 (kcal/h)", 1.163),
        u("ton", "冷冻吨 (RT)", 3516.852_84),
    ), "w"),
    UnitCategory("angle", "角度", listOf(
        u("deg", "度 (°)", 1.0),
        u("rad", "弧度 (rad)", 180.0 / Math.PI),
        u("grad", "百分度 (grad)", 0.9),
        u("turn", "周 (turn)", 360.0),
        u("arcmin", "角分 (′)", 1.0 / 60.0),
        u("arcsec", "角秒 (″)", 1.0 / 3600.0),
    ), "deg"),
)

private fun cat(key: String): UnitCategory = UC_CATEGORIES.first { it.key == key }

/**
 * 在 [category] 下执行线性或函数转换。
 *
 * 线性：value * (from.factor / to.factor)
 * 非线性（温度）：value -> toBase -> fromBase
 */
private fun convertValue(category: UnitCategory, fromKey: String, toKey: String, value: Double): Double {
    val from = category.units.firstOrNull { it.key == fromKey }
        ?: category.units.firstOrNull()
        ?: return Double.NaN
    val to = category.units.firstOrNull { it.key == toKey } ?: from
    if (from.key == to.key) return value
    return if (from.toBase != null && from.fromBase != null && to.toBase != null && to.fromBase != null) {
        // 非线性（温度）：两条路线
        val base = from.toBase.invoke(value)
        to.fromBase.invoke(base)
    } else {
        // 线性：统一换算为基准再到目标
        val asBase = if (from.toBase != null) from.toBase.invoke(value) else value * from.factor
        if (to.fromBase != null) to.fromBase.invoke(asBase) else asBase / to.factor
    }
}

/** 数字格式化 —— 对非常大或非常小的值用科学计数，否则用定点并去掉尾随 0 */
private fun formatResult(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "—"
    val abs = kotlin.math.abs(v)
    val fmt = when {
        abs == 0.0 -> "0"
        abs >= 1e10 || abs < 1e-6 -> "%.6g".format(v)
        abs >= 1000.0 -> "%.4f".format(v)
        abs >= 1.0 -> "%.6f".format(v)
        abs >= 0.001 -> "%.8f".format(v)
        else -> "%.10f".format(v)
    }
    return if ('.' in fmt) fmt.trimEnd('0').trimEnd('.') else fmt
}

/** 合法校验：输入字符串能否追加字符（防止两个小数点等） */
private fun isValidAppend(expr: String, tok: String): Boolean {
    if (tok == ".") {
        // 当前段无小数点
        val lastSeg = expr.split(Regex("[+\\-−×÷*/ ]")).lastOrNull() ?: expr
        return !lastSeg.contains('.')
    }
    if (tok == "-" || tok == "−") {
        // 只能在空串或运算符后出现
        return expr.isEmpty() || expr.last().let { it in setOf('+', '−', '×', '÷', '*', '/', '-', 'e', 'E') }
    }
    return true
}

// =========================================================================
//  状态 & Reducer
// =========================================================================

/**
 * 单位换算模块状态。
 *
 * @param category   当前类别 key（默认 length）
 * @param fromUnit   源单位 key
 * @param toUnit     目标单位 key
 * @param expression 数值输入区原始文本（如 "123.45"，无空格）
 * @param resultText 已格式化的结果字符串（预计算显示）
 */
data class UnitConversionModuleState(
    val category: String = "length",
    val fromUnit: String = "m",
    val toUnit: String = "km",
    val expression: String = "0",
    val resultText: String = "0",
) : ModuleState

/** 模块状态 reducer（纯函数）。 */
fun reduceUnitConversion(
    state: UnitConversionModuleState,
    intent: ModuleIntent,
): UnitConversionModuleState = when (intent) {
    is ModuleIntent.Clear -> {
        val c = cat(state.category)
        val defaultFrom = c.baseKey
        val defaultTo = c.units.firstOrNull { it.key != defaultFrom }?.key ?: defaultFrom
        UnitConversionModuleState(
            category = state.category,
            fromUnit = defaultFrom,
            toUnit = defaultTo,
            expression = "0",
            resultText = recompute(state.category, defaultFrom, defaultTo, "0"),
        )
    }
    is ModuleIntent.Backspace -> {
        val next = if (state.expression.length <= 1) "0" else state.expression.dropLast(1)
        state.copy(expression = next, resultText = recompute(state.category, state.fromUnit, state.toUnit, next))
    }
    is ModuleIntent.Input -> {
        val tok = intent.value
        val cur = state.expression
        val next = when (tok) {
            "+", "−", "×", "÷" -> cur  // 单位换算不需要运算，忽略
            "." -> {
                val last = cur.split(Regex("[eE]")).lastOrNull() ?: cur
                if ('.' in last) cur
                else {
                    val new = cur + "."
                    if (new == ".") "0." else new
                }
            }
            "-", "±" -> {
                // ± 翻转符号
                if (cur.isEmpty()) cur
                else if (cur.startsWith('-')) cur.substring(1)
                else "-$cur"
            }
            "00" -> {
                if (cur == "0") "0"
                else {
                    // 判断当前数字段合法性
                    val lastNum = cur.split(Regex("[+\\-×÷*/ ]")).lastOrNull() ?: cur
                    if (lastNum.isNotBlank() && lastNum.toDoubleOrNull() != null) cur + "00" else cur
                }
            }
            else -> {
                if (tok.length == 1 && tok[0].isDigit()) {
                    when {
                        cur == "0" -> tok
                        cur == "-0" -> "-$tok"
                        cur.length >= 20 -> cur  // 防止输入过长
                        else -> cur + tok
                    }
                } else cur
            }
        }
        state.copy(expression = next, resultText = recompute(state.category, state.fromUnit, state.toUnit, next))
    }
    is ModuleIntent.Evaluate -> state  // 单位换算实时求值，= 忽略
    is ModuleIntent.Custom -> when (intent.key) {
        "uc:category" -> {
            val newCatKey = intent.payload as? String
            // 竞态防护：非法类别 key 直接忽略
            val newCat = newCatKey?.let { k -> UC_CATEGORIES.firstOrNull { it.key == k } }
            if (newCat == null) {
                state
            } else {
                val defaultFrom = newCat.baseKey
                val defaultTo = newCat.units.firstOrNull { it.key != defaultFrom }?.key ?: defaultFrom
                state.copy(
                    category = newCat.key,
                    fromUnit = defaultFrom,
                    toUnit = defaultTo,
                    expression = "0",
                    resultText = recompute(newCat.key, defaultFrom, defaultTo, "0"),
                )
            }
        }
        "uc:from" -> {
            val c = cat(state.category)
            val newFrom = intent.payload as? String
            // 竞态防护：滚轮切换类别时可能发出旧列表的单位 key，直接忽略
            if (newFrom == null || c.units.none { it.key == newFrom }) {
                state
            } else {
                // 避免 source == target
                val realTo = if (newFrom == state.toUnit) {
                    c.units.firstOrNull { it.key != newFrom }?.key ?: state.toUnit
                } else state.toUnit
                state.copy(
                    fromUnit = newFrom,
                    toUnit = realTo,
                    resultText = recompute(state.category, newFrom, realTo, state.expression),
                )
            }
        }
        "uc:to" -> {
            val c = cat(state.category)
            val newTo = intent.payload as? String
            // 竞态防护：滚轮切换类别时可能发出旧列表的单位 key，直接忽略
            if (newTo == null || c.units.none { it.key == newTo }) {
                state
            } else {
                val realFrom = if (newTo == state.fromUnit) {
                    c.units.firstOrNull { it.key != newTo }?.key ?: state.fromUnit
                } else state.fromUnit
                state.copy(
                    toUnit = newTo,
                    fromUnit = realFrom,
                    resultText = recompute(state.category, realFrom, newTo, state.expression),
                )
            }
        }
        "uc:swap" -> {
            state.copy(
                fromUnit = state.toUnit,
                toUnit = state.fromUnit,
                resultText = recompute(state.category, state.toUnit, state.fromUnit, state.expression),
            )
        }
        else -> state
    }
}

private fun recompute(catKey: String, fromKey: String, toKey: String, expr: String): String {
    val v = expr.toDoubleOrNull() ?: return "—"
    val c = cat(catKey)
    return formatResult(convertValue(c, fromKey, toKey, v))
}

// =========================================================================
//  入口 Composable
// =========================================================================

@Composable
fun UnitConversionModule(
    state: UnitConversionModuleState,
    onIntent: (ModuleIntent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isLandscape) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.weight(1.1f)) { ControlPanel(state, onIntent) }
            Box(modifier = Modifier.weight(1f)) { KeypadSection(onIntent) }
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1.4f)) { ControlPanel(state, onIntent) }
            Box(modifier = Modifier.fillMaxWidth().weight(1.6f)) { KeypadSection(onIntent) }
        }
    }
}

// =========================================================================
//  控制/显示面板
// =========================================================================

@Composable
private fun ControlPanel(
    state: UnitConversionModuleState,
    onIntent: (ModuleIntent) -> Unit,
) {
    GlassDisplayCard {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceAround,
        ) {
            // Row1: 类别 + 数值
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabeledDropdown(
                    modifier = Modifier.weight(1f),
                    label = "类别",
                    currentLabel = cat(state.category).name,
                    options = UC_CATEGORIES.map { it.key to it.name },
                    onSelect = { onIntent(ModuleIntent.Custom("uc:category", it)) },
                )
                ValueDisplay(
                    modifier = Modifier.weight(1f),
                    expression = state.expression,
                )
            }
            // Row2: 从 + 到 + 交换
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LabeledDropdown(
                    modifier = Modifier.weight(1f),
                    label = "从",
                    currentLabel = unitLabel(state.category, state.fromUnit),
                    options = cat(state.category).units.map { it.key to it.label },
                    onSelect = { onIntent(ModuleIntent.Custom("uc:from", it)) },
                )
                SwapButton { onIntent(ModuleIntent.Custom("uc:swap")) }
                LabeledDropdown(
                    modifier = Modifier.weight(1f),
                    label = "到",
                    currentLabel = unitLabel(state.category, state.toUnit),
                    options = cat(state.category).units.map { it.key to it.label },
                    onSelect = { onIntent(ModuleIntent.Custom("uc:to", it)) },
                )
            }
            // Result row
            ResultCard(
                result = state.resultText,
                unit = unitLabel(state.category, state.toUnit, short = false),
            )
        }
    }
}

private fun unitLabel(catKey: String, unitKey: String, short: Boolean = false): String {
    val c = cat(catKey)
    val u = c.units.firstOrNull { it.key == unitKey } ?: return unitKey
    return if (short) u.key.uppercase() else u.label
}

// =========================================================================
//  控件：带标签的下拉框 / 数值显示 / 交换按钮
// =========================================================================

@Composable
private fun LabeledDropdown(
    label: String,
    currentLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val dark = isDarkTheme()
    val labelColor = if (dark) Text200.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
        )
        GlassInputBox(
            onClick = { expanded = true },
            trailing = { ChevronDown(expanded) },
        ) {
            Text(
                currentLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    // 下拉列表（直接放置 items，避免嵌套滚动导致的崩溃）
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        containerColor = if (dark) Color(0xFF232B36) else Color(0xFFFFFFFF),
    ) {
        val primary = MaterialTheme.colorScheme.primary
        options.forEach { (key, name) ->
            val selected = name == currentLabel
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) primary else
                                (if (dark) Color(0xFFE2E8F0) else Color(0xFF1A1A1A)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 选中标记
                        if (selected) {
                            Text(
                                "✓",
                                color = primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
                onClick = {
                    onSelect(key)
                    expanded = false
                },
                modifier = if (selected) {
                    Modifier.background(primary.copy(alpha = 0.12f))
                } else Modifier,
                colors = MenuDefaults.itemColors(
                    textColor = if (dark) Color(0xFFE2E8F0) else Color(0xFF1A1A1A),
                ),
            )
        }
    }
}

@Composable
private fun ChevronDown(expanded: Boolean) {
    val angle by animateFloatAsState(
        if (expanded) 180f else 0f,
        tween(180),
        label = "chevron",
    )
    Text(
        "▾",
        modifier = Modifier.rotate(angle),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = LocalContentColor.current.copy(alpha = 0.7f),
    )
}

@Composable
private fun ValueDisplay(
    expression: String,
    modifier: Modifier = Modifier,
) {
    val dark = isDarkTheme()
    val primary = MaterialTheme.colorScheme.primary
    val labelColor = if (dark) Text200.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    // 文本 + 光标状态：文本由底部键盘驱动，这里只负责展示；光标始终保持在末尾
    var focused by remember { mutableStateOf(false) }
    var tfv by remember { mutableStateOf(TextFieldValue(expression, TextRange(expression.length))) }
    LaunchedEffect(expression) {
        tfv = TextFieldValue(expression, TextRange(expression.length))
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "数值",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
        )
        // 聚焦时与结果卡同色（主色半透明背景 + 主色边框）
        val shape = RoundedCornerShape(20.dp)
        val bgColor = if (focused) primary.copy(alpha = if (dark) 0.10f else 0.08f)
            else Color.Transparent
        val borderColor = if (focused) primary.copy(alpha = if (dark) 0.35f else 0.45f)
            else Color.Transparent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(shape)
                .let { m ->
                    if (focused) {
                        m.background(bgColor)
                    } else {
                        m.background(
                            Brush.verticalGradient(
                                0.0f to (if (dark) Color(0xFF1F2833) else Color(0xFFFFFFFF)).copy(alpha = 0.92f),
                                1.0f to (if (dark) Color(0xFF28313E) else Color(0xFFF2F2F7)).copy(alpha = 0.92f),
                            )
                        )
                    }
                }
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            BasicTextField(
                value = tfv,
                onValueChange = { tfv = it },
                readOnly = true,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalContentColor.current,
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

// =========================================================================
//  交换按钮
// =========================================================================

@Composable
private fun SwapButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed) 0.9f else 1f
    val dark = isDarkTheme()
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(shape)
            .background(primary.copy(alpha = 0.10f))
            .border(1.dp, primary.copy(alpha = 0.45f), shape)
            .clickable(interaction, null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "⇄",
            color = primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// =========================================================================
//  结果卡
// =========================================================================

@Composable
private fun ResultCard(result: String, unit: String, modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(shape)
            .background(primary.copy(alpha = if (dark) 0.10f else 0.08f))
            .border(
                1.dp,
                primary.copy(alpha = if (dark) 0.35f else 0.45f),
                shape,
            )
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                result,
                color = primary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                unit,
                color = primary.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// =========================================================================
//  玻璃输入框（下拉 & 数值）
// =========================================================================

@Composable
private fun GlassInputBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "scale")
    val dark = isDarkTheme()
    val top = if (dark) Color(0xFF1F2833) else Color(0xFFFFFFFF)
    val bottom = if (dark) Color(0xFF28313E) else Color(0xFFF2F2F7)
    val border = if (dark) Color(0xFFFFFFFF).copy(alpha = 0.10f) else Color(0xFF000000).copy(alpha = 0.08f)
    val shape = RoundedCornerShape(14.dp)
    val gradient = Brush.verticalGradient(0.0f to top.copy(alpha = 0.92f), 1.0f to bottom.copy(alpha = 0.92f))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .scale(scale)
            .clip(shape)
            .background(gradient)
            .border(1.dp, border, shape)
            .let { m ->
                if (onClick != null) {
                    m.clickable(interaction, null) { onClick() }
                } else m
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (trailing != null) {
            trailing()
        }
    }
}

// =========================================================================
//  玻璃显示卡片（同标准模块）
// =========================================================================

@Composable
private fun GlassDisplayCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = isDarkTheme()
    val top: Color = if (dark) PanelAppleDark else Color(0xFFFFFFFF)
    val bottom: Color = if (dark) Color(0xFF2C343E) else Color(0xFFF2F2F7)
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.8.dp))
            .background(gradient),
    ) { content() }
}

// =========================================================================
//  键盘区（简化：无运算符号、无等号）
//  布局：
//    AC  ⌫   ±
//     7   8   9
//     4   5   6
//     1   2   3
//    00   0   .
//  3 列 × 5 行，使用 GlassCircleButton 液态玻璃风格
// =========================================================================

@Composable
private fun KeypadSection(onIntent: (ModuleIntent) -> Unit) {
    GlassKeypadCard {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val rows = listOf(
                listOf(
                    KeySpec("AC", UCKeyVariant.Clear) { onIntent(ModuleIntent.Clear) },
                    KeySpec("⌫", UCKeyVariant.Clear) { onIntent(ModuleIntent.Backspace) },
                    KeySpec("±", UCKeyVariant.Neutral) { onIntent(ModuleIntent.Input("±")) },
                ),
                listOf(
                    KeySpec("7") { onIntent(ModuleIntent.Input("7")) },
                    KeySpec("8") { onIntent(ModuleIntent.Input("8")) },
                    KeySpec("9") { onIntent(ModuleIntent.Input("9")) },
                ),
                listOf(
                    KeySpec("4") { onIntent(ModuleIntent.Input("4")) },
                    KeySpec("5") { onIntent(ModuleIntent.Input("5")) },
                    KeySpec("6") { onIntent(ModuleIntent.Input("6")) },
                ),
                listOf(
                    KeySpec("1") { onIntent(ModuleIntent.Input("1")) },
                    KeySpec("2") { onIntent(ModuleIntent.Input("2")) },
                    KeySpec("3") { onIntent(ModuleIntent.Input("3")) },
                ),
                listOf(
                    KeySpec("00", UCKeyVariant.Neutral) { onIntent(ModuleIntent.Input("00")) },
                    KeySpec("0") { onIntent(ModuleIntent.Input("0")) },
                    KeySpec(".") { onIntent(ModuleIntent.Input(".")) },
                ),
            )
            rows.forEach { cols ->
                KeypadRow(Modifier.weight(1f)) {
                    cols.forEach { GlassKey(it.label, it.variant, it.onClick) }
                }
            }
        }
    }
}

private data class KeySpec(
    val label: String,
    val variant: UCKeyVariant = UCKeyVariant.Default,
    val onClick: () -> Unit,
)

internal enum class UCKeyVariant { Default, Neutral, Clear }

@Composable
private fun GlassKeypadCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isDarkTheme()
    val top: Color = (if (dark) PanelAppleDark else Color(0xFFFFFFFF))
        .copy(alpha = if (dark) 0.92f else 0.85f)
    val bottom: Color = (if (dark) Color(0xFF2C343E) else Color(0xFFF2F2F7))
        .copy(alpha = if (dark) 0.92f else 0.60f)
    val gradient = Brush.verticalGradient(0.0f to top, 1.0f to bottom)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.8.dp))
            .background(gradient),
    ) { content() }
}

@Composable
private fun KeypadRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun RowScope.GlassKey(
    label: String,
    variant: UCKeyVariant = UCKeyVariant.Default,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .aspectRatio(1.4f),
        contentAlignment = Alignment.Center,
    ) {
        // 使用 GlassCircleButton，宽度填充满
        GlassCircleButton(size = 88.dp, onClick = onClick) {
            val color = when (variant) {
                UCKeyVariant.Clear -> if (isDarkTheme()) Color(0xFFFF6B6B) else Color(0xFFE53935)
                UCKeyVariant.Neutral -> LocalContentColor.current
                else -> LocalContentColor.current
            }
            val weight = when {
                variant == UCKeyVariant.Clear -> FontWeight.SemiBold
                label.length >= 2 -> FontWeight.SemiBold
                else -> FontWeight.Medium
            }
            val fontSize = when {
                label.length >= 3 -> 18.sp
                variant == UCKeyVariant.Clear && label.length >= 2 -> 18.sp
                variant == UCKeyVariant.Neutral -> 20.sp
                else -> 24.sp
            }
            CompositionLocalProvider(LocalContentColor provides color) {
                Text(
                    text = label,
                    fontSize = fontSize,
                    fontWeight = weight,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
