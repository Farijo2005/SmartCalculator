package com.example.smartcalculator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.os.Build

/**
 * 强调色 / 背景色 / 主题模式的运行时覆盖。
 * 由设置抽屉驱动，让用户动态切换主题色与背景色。
 */
data class AccentOverride(
    val primary: Color,
    val ring: Color,
)

data class BackgroundOverride(
    val background: Color,
    val card: Color,
    val secondary: Color,
)

val LocalAccentOverride = staticCompositionLocalOf<AccentOverride?> { null }
val LocalBackgroundOverride = staticCompositionLocalOf<BackgroundOverride?> { null }

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = PrimaryForegroundLight,
    primaryContainer = Brand50,
    onPrimaryContainer = Brand900,
    secondary = SecondaryLight,
    onSecondary = SecondaryForegroundLight,
    secondaryContainer = Background300,
    onSecondaryContainer = Text800,
    tertiary = Chart5,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E8FF),
    onTertiaryContainer = Color(0xFF3A1A57),
    background = BackgroundLight,
    onBackground = ForegroundLight,
    surface = CardLight,
    onSurface = CardForegroundLight,
    surfaceVariant = Background200,
    onSurfaceVariant = Text500,
    surfaceTint = PrimaryLight,
    inverseSurface = Background800,
    inverseOnSurface = Text50,
    error = DestructiveLight,
    onError = DestructiveForegroundLight,
    errorContainer = Color(0xFFFFECEA),
    onErrorContainer = Color(0xFF7A0A00),
    outline = BorderLight,
    outlineVariant = Background300,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = PrimaryForegroundDark,
    primaryContainer = Brand900,
    onPrimaryContainer = Brand50,
    secondary = SecondaryDark,
    onSecondary = SecondaryForegroundDark,
    secondaryContainer = Background700,
    onSecondaryContainer = Text50,
    tertiary = Chart5,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF4A1B6E),
    onTertiaryContainer = Color(0xFFEEDCFF),
    background = BackgroundDark,
    onBackground = ForegroundDark,
    surface = CardDark,
    onSurface = CardForegroundDark,
    surfaceVariant = Background700,
    onSurfaceVariant = Text200,
    surfaceTint = PrimaryDark,
    inverseSurface = Background200,
    inverseOnSurface = Text900,
    error = DestructiveDark,
    onError = DestructiveForegroundDark,
    errorContainer = Color(0xFF6B0E08),
    onErrorContainer = Color(0xFFFFD6D2),
    outline = BorderDark,
    outlineVariant = Background700,
    scrim = Color.Black,
)

enum class ThemeMode { Light, Dark, Auto }

@Composable
fun SmartCalculatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.Auto,
    dynamicColor: Boolean = false,
    accentOverride: AccentOverride? = null,
    backgroundOverride: BackgroundOverride? = null,
    content: @Composable () -> Unit,
) {
    val resolvedDark = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Auto -> darkTheme
    }
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (resolvedDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        resolvedDark -> DarkColors
        else -> LightColors
    }

    val overridden = baseScheme.copy(
        primary = accentOverride?.primary ?: baseScheme.primary,
        secondary = accentOverride?.primary ?: baseScheme.secondary,
        tertiary = accentOverride?.primary ?: baseScheme.tertiary,
        surfaceTint = accentOverride?.primary ?: baseScheme.surfaceTint,
    ).let { scheme ->
        if (backgroundOverride != null) {
            scheme.copy(
                background = backgroundOverride.background,
                surface = backgroundOverride.card,
                surfaceVariant = backgroundOverride.secondary,
            )
        } else scheme
    }

    val effectiveAccent = accentOverride ?: AccentOverride(baseScheme.primary, baseScheme.primary)
    val effectiveBackground = backgroundOverride ?: BackgroundOverride(
        background = overridden.background,
        card = overridden.surface,
        secondary = overridden.surfaceVariant,
    )

    CompositionLocalProvider(
        LocalAccentOverride provides effectiveAccent,
        LocalBackgroundOverride provides effectiveBackground,
    ) {
        MaterialTheme(
            colorScheme = overridden,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

private val Shapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(9.6.dp),  // radius-sm 0.6rem
    small = androidx.compose.foundation.shape.RoundedCornerShape(9.6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(19.2.dp),     // radius-md 1.2rem
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.8.dp),      // radius-lg 1.8rem
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(38.4.dp),
)
