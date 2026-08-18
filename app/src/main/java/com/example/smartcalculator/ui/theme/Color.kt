package com.example.smartcalculator.ui.theme

import androidx.compose.ui.graphics.Color

// ===== Primitive palette (Apple-inspired, 10-step scales) =====

// brand
val Brand50 = Color(0xFFE8F2FF)
val Brand100 = Color(0xFFCFE5FF)
val Brand200 = Color(0xFF9FCBFF)
val Brand300 = Color(0xFF66ABFF)
val Brand400 = Color(0xFF2E8DFF)
val Brand500 = Color(0xFF007AFF) // @primary
val Brand600 = Color(0xFF0064D6)
val Brand700 = Color(0xFF004FAD)
val Brand800 = Color(0xFF003B82)
val Brand900 = Color(0xFF00275A)

// background
val Background50 = Color(0xFFFFFFFF)
val Background100 = Color(0xFFF7F7FA)
val Background200 = Color(0xFFF2F2F7)
val Background300 = Color(0xFFE5E5EA)
val Background400 = Color(0xFFD1D1D6)
val Background500 = Color(0xFFAEAEB2)
val Background600 = Color(0xFF8E8E93)
val Background700 = Color(0xFF3A3A3C)
val Background800 = Color(0xFF1C1C1E)
val Background900 = Color(0xFF000000)

// text
val Text50 = Color(0xFFF5F5F7)
val Text100 = Color(0xFFE3E3E8)
val Text200 = Color(0xFFC7C7CC)
val Text300 = Color(0xFFAEAEB2)
val Text400 = Color(0xFF8E8E93)
val Text500 = Color(0xFF6E6E73)
val Text600 = Color(0xFF48484A)
val Text700 = Color(0xFF3C3C43)
val Text800 = Color(0xFF1D1D1F)
val Text900 = Color(0xFF000000)

// state
val StateSuccess = Color(0xFF34C759)
val StateSuccessDark = Color(0xFF30D158)
val StateError = Color(0xFFFF3B30)
val StateErrorDark = Color(0xFFFF453A)

// chart colors
val Chart1 = StateSuccess
val Chart2 = Brand500
val Chart3 = Color(0xFFFF9500) // orange
val Chart4 = Color(0xFF5856D6) // indigo
val Chart5 = Color(0xFFAF52DE) // purple

// Accent presets (for settings)
val AccentBlue = Brand500
val AccentOrange = Color(0xFFFF9500)
val AccentGreen = StateSuccess
val AccentPurple = Color(0xFFAF52DE)
val AccentPink = Color(0xFFFF2D55)

// Background presets
val BgWhite = Background50
val BgGray100 = Background200
val BgGray200 = Background300
val BgBlack = Color(0xFF1C1C1E)
val BgMidnight = Color(0xFF0A0A0C)

// ===== Semantic defaults =====
val PrimaryLight = Brand500
val PrimaryForegroundLight = Background50
val SecondaryLight = Background200
val SecondaryForegroundLight = Text800
val MutedLight = Background200
val MutedForegroundLight = Text400
val AccentLight = Background100
val AccentForegroundLight = Text800
val DestructiveLight = StateError
val DestructiveForegroundLight = Color.White
val BackgroundLight = Background50
val ForegroundLight = Text800
val CardLight = Background50
val CardForegroundLight = Text800
val BorderLight = Background300
val InputLight = Background400
val RingLight = Brand500
val IconLight = Color(0xFF1D1D1F)

val Icon500Placeholder = Color(0xFF8E8E93)
val IconMutedLight = Icon500Placeholder

val PrimaryDark = Brand400
val PrimaryForegroundDark = Background900
val SecondaryDark = Background800
val SecondaryForegroundDark = Text50
val MutedDark = Background800
val MutedForegroundDark = Text400
val AccentDark = Background700
val AccentForegroundDark = Text50
val DestructiveDark = StateErrorDark
val DestructiveForegroundDark = Color.White
val BackgroundDark = Background900
val ForegroundDark = Text50
val CardDark = Background800
val CardForegroundDark = Text50
val BorderDark = Background700
val InputDark = Background700
val RingDark = Brand400
val IconDark = Color(0xFFF5F5F7)
val IconMutedDark = Text400

// Calculator button group colors (light)
val NumKeyLight = Background200
val NumKeyTextLight = Text900
val OpKeyLight = Background300
val OpKeyTextLight = Text900
val FuncKeyLight = Background100
val FuncKeyTextLight = Text800
val AccentKeyLight = Brand500      // = / AC primary
val AccentKeyTextLight = Background50
val WarnKeyLight = StateError      // delete (×)
val WarnKeyTextLight = Color.White
val ScientificKeyLight = Background300
val ScientificKeyTextLight = Text800

// Calculator button group colors (dark)
val NumKeyDark = Background800
val NumKeyTextDark = Text50
val OpKeyDark = Background700
val OpKeyTextDark = Text50
val FuncKeyDark = Background700
val FuncKeyTextDark = Text50
val AccentKeyDark = Brand400
val AccentKeyTextDark = Background900
val WarnKeyDark = StateErrorDark
val WarnKeyTextDark = Color.White
val ScientificKeyDark = Background700
val ScientificKeyTextDark = Text50
