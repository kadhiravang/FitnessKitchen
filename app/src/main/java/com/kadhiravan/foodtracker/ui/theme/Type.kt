package com.kadhiravan.foodtracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kadhiravan.foodtracker.R

/** Rounded, warm display face, headings and standalone numbers (the calorie ring, totals). */
val Baloo2 = FontFamily(
    Font(R.font.baloo2_semibold, FontWeight.SemiBold),
    Font(R.font.baloo2_bold, FontWeight.Bold),
    Font(R.font.baloo2_extrabold, FontWeight.ExtraBold)
)

/** Clean, readable body face, everything else. */
val NunitoSans = FontFamily(
    Font(R.font.nunitosans_regular, FontWeight.Normal),
    Font(R.font.nunitosans_semibold, FontWeight.SemiBold),
    Font(R.font.nunitosans_bold, FontWeight.Bold),
    Font(R.font.nunitosans_extrabold, FontWeight.ExtraBold)
)

private val baseline = Typography()

val AppTypography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold),
    displayMedium = baseline.displayMedium.copy(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold),
    displaySmall = baseline.displaySmall.copy(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = Baloo2, fontWeight = FontWeight.Bold),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = Baloo2, fontWeight = FontWeight.Bold),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = Baloo2, fontWeight = FontWeight.Bold),
    titleLarge = baseline.titleLarge.copy(fontFamily = Baloo2, fontWeight = FontWeight.Bold),
    titleMedium = baseline.titleMedium.copy(fontFamily = Baloo2, fontWeight = FontWeight.Bold),
    titleSmall = baseline.titleSmall.copy(fontFamily = Baloo2, fontWeight = FontWeight.SemiBold),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = NunitoSans, fontWeight = FontWeight.SemiBold),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = NunitoSans, fontWeight = FontWeight.SemiBold),
    bodySmall = baseline.bodySmall.copy(fontFamily = NunitoSans, fontWeight = FontWeight.Normal),
    labelLarge = baseline.labelLarge.copy(fontFamily = NunitoSans, fontWeight = FontWeight.Bold),
    labelMedium = baseline.labelMedium.copy(fontFamily = NunitoSans, fontWeight = FontWeight.Bold),
    labelSmall = baseline.labelSmall.copy(fontFamily = NunitoSans, fontWeight = FontWeight.Bold)
)
