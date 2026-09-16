package com.kadhiravan.foodtracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.ui.theme.AmberWarn
import com.kadhiravan.foodtracker.ui.theme.ExerciseGreenVivid
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DiarySummaryCard(
    totalCalories: Int,
    calorieGoal: Int,
    calorieBufferKcal: Int,
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    proteinGoalG: Int,
    carbsGoalG: Int,
    fatGoalG: Int,
    modifier: Modifier = Modifier
) {
    val hasGoal = calorieGoal > 0

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            KcalGauge(
                consumed = totalCalories,
                goal = calorieGoal,
                buffer = calorieBufferKcal,
                modifier = Modifier.fillMaxWidth()
            )

            if (proteinG > 0 || carbsG > 0 || fatG > 0 || hasGoal) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroColumn("Protein", proteinG, proteinGoalG.toDouble(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MacroColumn("Carbs", carbsG, carbsGoalG.toDouble(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    MacroColumn("Fat", fatG, fatGoalG.toDouble(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Green within [buffer] kcal of the target, easing through amber and then red the further
 * off-target the count drifts — on-target eating should read as calm, not alarming, while a
 * big miss should visibly stand out. */
fun kcalGaugeColor(consumed: Int, goal: Int, buffer: Int, errorColor: Color): Color {
    if (goal <= 0) return ExerciseGreenVivid
    val diff = abs(consumed - goal)
    if (diff <= buffer) return ExerciseGreenVivid
    val maxDeviation = (goal * 0.25).coerceIn(150.0, 400.0)
    val t = ((diff - buffer) / maxDeviation).toFloat().coerceIn(0f, 1f)
    return if (t <= 0.5f) lerp(ExerciseGreenVivid, AmberWarn, t / 0.5f) else lerp(AmberWarn, errorColor, (t - 0.5f) / 0.5f)
}

/** A horizontal track-and-fill bar with a status badge riding at the current position and
 * two tick marks bracketing the optimal range, styled after Fitia's calorie gauge — a flat
 * bar reads at a glance in a way a dome-shaped arc never quite did. */
@Composable
private fun KcalGauge(consumed: Int, goal: Int, buffer: Int, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val progressColor = kcalGaugeColor(consumed, goal, buffer, MaterialTheme.colorScheme.error)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Scaled against a fixed reference ceiling — comfortably above nearly any real calorie
    // goal — rather than a multiple of goal itself, so the badge's position genuinely
    // reflects the goal's value instead of always landing at the same fraction of the bar.
    val scale = maxOf(consumed.toFloat(), goal.toFloat(), 3000f) * 1.1f
    val fillFraction = if (goal > 0) (consumed / scale).coerceIn(0f, 1f) else 0f
    // Two ticks — the optimal-eating range around the goal — instead of one, so hitting
    // anywhere between them reads as "on target" rather than implying one exact number. A
    // small buffer puts the true goal±buffer positions too close together to read as two
    // distinct ticks, so there's a minimum gap between them; the numbers shown under each
    // tick always match wherever it's actually drawn, so the two never disagree.
    val goalFraction = if (goal > 0) (goal / scale).coerceIn(0f, 1f) else 0f
    val minGap = 0.12f
    val rawGap = (2 * buffer / scale).coerceAtLeast(0f)
    val halfGap = maxOf(rawGap, minGap) / 2f
    val lowerFraction = (goalFraction - halfGap).coerceIn(0f, 1f)
    val upperFraction = (goalFraction + halfGap).coerceIn(0f, 1f)
    val lowerValue = (lowerFraction * scale).roundToInt()
    val upperValue = (upperFraction * scale).roundToInt()

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "%,d".format(consumed),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                if (goal > 0) " / %,d".format(goal) else "",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "kcal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(trackColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fillFraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(progressColor)
            )
            if (goal > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = maxWidth * lowerFraction - 1.5.dp)
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(tickColor)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = maxWidth * upperFraction - 1.5.dp)
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(tickColor)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = maxWidth * fillFraction - 12.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(progressColor)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Today's intake",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        if (goal > 0) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    "%,d".format(lowerValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(x = (maxWidth * lowerFraction - 18.dp).coerceAtLeast(0.dp))
                )
                Text(
                    "%,d".format(upperValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(x = (maxWidth * upperFraction - 18.dp).coerceAtMost(maxWidth - 36.dp))
                )
            }
        }
    }
}

@Composable
private fun MacroColumn(label: String, consumedG: Double, goalG: Double, color: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text("${consumedG.roundToInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (goalG > 0) " / ${goalG.roundToInt()}g" else "g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (goalG > 0) {
            ProgressBar(
                value = consumedG,
                target = goalG,
                color = color,
                height = 5.dp,
                modifier = Modifier.width(56.dp).padding(top = 6.dp)
            )
        }
    }
}
