package com.kadhiravan.foodtracker.ui.theme

import androidx.compose.ui.graphics.Color

// Brand, inspired by Apple's Activity Rings (Move/Exercise/Stand): three fixed, fully
// saturated hues carry all the color, everything else in the app stays neutral. Each has a
// deeper "Ink" variant for light-theme text/containers (the raw vivid tone is reserved for
// filled bars and rings) and a brighter "Vivid" variant for dark theme, where it's meant to
// pop the way the real rings do against black.
val MoveRed = Color(0xFFFA114F)
val MoveRedInk = Color(0xFFB3003A)
val MoveRedVivid = Color(0xFFFF3F72)
val ExerciseGreenInk = Color(0xFF5E9A17)
val ExerciseGreenVivid = Color(0xFF92E82A)
val StandCyanInk = Color(0xFF0FA89D)
val StandCyanVivid = Color(0xFF1EE6DA)

// Warning midpoint for the kcal gauge's on-target -> off-target gradient (see
// kcalGaugeColor in DiarySummary.kt), an amber stop between "good" green and "alarm" red.
val AmberWarn = Color(0xFFFFC145)

// Light theme, deliberately neutral (not tinted toward any brand hue) so the three ring
// colors read as the only saturated things on screen, same as Apple's own black/white chrome.
val Paper = Color(0xFFF7F7F8)
val PaperSurface = Color(0xFFEFEFF1)
val CardLight = Color(0xFFFFFFFF)
val DividerLight = Color(0xFFE1E1E5)
val TextPrimaryLight = Color(0xFF17171A)
val TextMutedLight = Color(0xFF79797F)
val ErrorLight = Color(0xFFFF3B30)

// Dark theme, near-true black, echoing Activity Rings' own rule of always sitting on a
// black background, with a divider bright enough against the card to stay visible.
val Ink = Color(0xFF0A0A0C)
val InkSurface = Color(0xFF212124)
val CardDark = Color(0xFF18181B)
val DividerDark = Color(0xFF34343A)
val TextPrimaryDark = Color(0xFFF2F2F4)
val TextMutedDark = Color(0xFF9C9CA3)
val ErrorDark = Color(0xFFFF6259)
