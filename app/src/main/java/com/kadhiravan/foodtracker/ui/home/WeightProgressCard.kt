package com.kadhiravan.foodtracker.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.local.WeightEntry
import com.kadhiravan.foodtracker.util.DateUtils
import kotlin.math.abs
import kotlin.math.roundToInt

/** Progress toward a target body weight, derived from [WeightEntry] history and the
 * target set in Settings, mirrors the "Goal" card pattern from Fitia's Progress tab. */
data class WeightGoalState(
    val label: String,
    val emoji: String,
    val startWeight: Double,
    val currentWeight: Double,
    val targetWeight: Double,
    val changeSoFar: Double,
    val etaWeeksText: String
)

fun computeWeightGoal(history: List<WeightEntry>, targetWeightKg: Float): WeightGoalState? {
    if (targetWeightKg <= 0f || history.isEmpty()) return null
    val target = targetWeightKg.toDouble()
    val start = history.first()
    val current = history.last()
    val totalNeeded = target - start.weightKg
    if (abs(totalNeeded) < 0.1) return null // already at goal from the first entry

    val changeSoFar = current.weightKg - start.weightKg

    val remaining = target - current.weightKg
    val stillMovingTowardGoal = (remaining > 0) == (totalNeeded > 0)
    val daysElapsed = DateUtils.daysBetween(start.date, current.date)
    val etaWeeksText = when {
        abs(remaining) < 0.1 -> "Reached!"
        history.size < 2 || daysElapsed <= 0 || changeSoFar == 0.0 || !stillMovingTowardGoal -> "Not enough data yet"
        else -> {
            val ratePerDay = changeSoFar / daysElapsed
            val weeks = (remaining / ratePerDay / 7).roundToInt()
            if (weeks <= 0) "Reached!" else "$weeks week${if (weeks == 1) "" else "s"}"
        }
    }

    val (label, emoji) = when {
        totalNeeded < 0 -> "Fat Loss" to "🔥"
        totalNeeded > 0 -> "Weight Gain" to "💪"
        else -> "Maintain" to "⚖️"
    }

    return WeightGoalState(
        label = label,
        emoji = emoji,
        startWeight = start.weightKg,
        currentWeight = current.weightKg,
        targetWeight = target,
        changeSoFar = changeSoFar,
        etaWeeksText = etaWeeksText
    )
}

@Composable
fun WeightProgressCard(
    history: List<WeightEntry>,
    targetWeightKg: Float,
    onLogWeight: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val latest = history.lastOrNull()
    val previous = if (history.size >= 2) history[history.size - 2] else null
    val goal = computeWeightGoal(history, targetWeightKg)

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (goal != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goal.emoji, style = MaterialTheme.typography.titleMedium)
                    Text(
                        goal.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatKg(goal.currentWeight)} kg", style = MaterialTheme.typography.titleLarge)
                        // Only one weigh-in so far means "change so far" is trivially zero , 
                        // showing that as a delta reads like a broken range ("68.5 kg → 0 kg")
                        // rather than "no change yet", so it's hidden until there's a second
                        // entry to actually compare against.
                        if (history.size >= 2) {
                            WeightDelta(goal.changeSoFar, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(18.dp)
                        )
                        Text(
                            "${formatKg(goal.targetWeight)} kg",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                ProgressBar(
                    value = abs(goal.changeSoFar),
                    target = abs(goal.targetWeight - goal.startWeight),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Time to goal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(goal.etaWeeksText, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Text("Update Progress")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Body weight", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { showDialog = true }) { Text("Update") }
                }
                if (latest == null) {
                    Text(
                        "No weigh-ins yet, tap Update to log your first one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Text("${formatKg(latest.weightKg)} kg", style = MaterialTheme.typography.headlineMedium)
                        if (previous != null) {
                            WeightDelta(latest.weightKg - previous.weightKg, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                    Text(
                        "Set a target weight in Settings to track progress toward a goal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (history.size >= 2) {
                WeightSparkline(
                    history = history.takeLast(12),
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 14.dp)
                )
            }
        }
    }

    if (showDialog) {
        LogWeightDialog(
            initialValue = latest?.weightKg,
            onConfirm = { kg -> onLogWeight(kg); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

/** Rounded track-and-fill bar, shared across the Diary tab's cards. */
@Composable
fun ProgressBar(
    value: Double,
    target: Double,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    trackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outline,
    height: androidx.compose.ui.unit.Dp = 8.dp
) {
    val fillFraction = if (target > 0.0) (value / target).toFloat().coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .border(1.dp, trackColor, RoundedCornerShape(50))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fillFraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
private fun WeightDelta(deltaKg: Double, modifier: Modifier = Modifier) {
    val (icon, color) = when {
        deltaKg > 0.05 -> Icons.AutoMirrored.Filled.TrendingUp to MaterialTheme.colorScheme.error
        deltaKg < -0.05 -> Icons.AutoMirrored.Filled.TrendingDown to MaterialTheme.colorScheme.primary
        else -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.height(20.dp))
        Text(
            "${if (deltaKg > 0) "+" else ""}${formatKg(deltaKg)} kg",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@Composable
private fun WeightSparkline(history: List<WeightEntry>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val min = history.minOf { it.weightKg }
    val max = history.maxOf { it.weightKg }
    val range = (max - min).coerceAtLeast(0.5)

    Canvas(modifier = modifier) {
        val stepX = if (history.size > 1) size.width / (history.size - 1) else 0f
        val points = history.mapIndexed { index, entry ->
            val x = stepX * index
            val normalized = (entry.weightKg - min) / range
            val y = size.height - (normalized * size.height).toFloat()
            androidx.compose.ui.geometry.Offset(x, y)
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
        points.forEach { point ->
            drawCircle(color = lineColor, radius = 5f, center = point, style = Stroke(width = 4f))
        }
    }
}

@Composable
private fun LogWeightDialog(
    initialValue: Double?,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue?.let { formatKg(it) }.orEmpty()) }
    val parsed = text.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log today's weight") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null && parsed > 0
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatKg(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
