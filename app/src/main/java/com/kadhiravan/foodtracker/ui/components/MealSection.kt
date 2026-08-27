package com.kadhiravan.foodtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.local.LogEntry
import com.kadhiravan.foodtracker.data.local.MealType

fun MealType.displayName(): String = when (this) {
    MealType.BREAKFAST -> "Breakfast"
    MealType.LUNCH -> "Lunch"
    MealType.DINNER -> "Dinner"
    MealType.SNACK -> "Snacks"
}

@Composable
fun MealSection(
    mealType: MealType,
    entries: List<LogEntry>,
    onDeleteEntry: (LogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    mealType.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${entries.sumOf { it.calories }} kcal",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            entries.forEach { entry ->
                LogEntryRow(entry = entry, onDelete = { onDeleteEntry(entry) })
            }
        }
    }
}
