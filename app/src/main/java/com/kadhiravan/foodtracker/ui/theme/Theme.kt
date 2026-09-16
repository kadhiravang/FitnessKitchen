package com.kadhiravan.foodtracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = ExerciseGreenInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F5CC),
    onPrimaryContainer = Color(0xFF3D6B0F),
    secondary = MoveRed,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = MoveRedInk,
    tertiary = StandCyanInk,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC9F5F0),
    onTertiaryContainer = Color(0xFF076158),
    background = Paper,
    onBackground = TextPrimaryLight,
    surface = CardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = PaperSurface,
    onSurfaceVariant = TextMutedLight,
    outline = DividerLight,
    outlineVariant = DividerLight,
    error = ErrorLight,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = ExerciseGreenVivid,
    onPrimary = Color(0xFF142600),
    primaryContainer = Color(0xFF2C4A0A),
    onPrimaryContainer = Color(0xFFC4F57A),
    secondary = MoveRedVivid,
    onSecondary = Color(0xFF2B000D),
    secondaryContainer = Color(0xFF4A0819),
    onSecondaryContainer = Color(0xFFFF9DB6),
    tertiary = StandCyanVivid,
    onTertiary = Color(0xFF00302C),
    tertiaryContainer = Color(0xFF0B4A44),
    onTertiaryContainer = Color(0xFF7FF5EC),
    background = Ink,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = InkSurface,
    onSurfaceVariant = TextMutedDark,
    outline = DividerDark,
    outlineVariant = DividerDark,
    error = ErrorDark,
    onError = Color(0xFF400400)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Dynamic (wallpaper-derived) color is opt-in, not the default — FitnessKitchen has its own
 * designed identity (Move-red/Exercise-green/Stand-cyan on a neutral ground, styled after
 * Apple's Activity Rings) that Material You would otherwise wash out on Android 12+.
 */
@Composable
fun FoodTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
