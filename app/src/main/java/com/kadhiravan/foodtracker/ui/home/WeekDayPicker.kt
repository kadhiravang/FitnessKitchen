package com.kadhiravan.foodtracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.local.DailyTotal
import com.kadhiravan.foodtracker.util.DateUtils

/**
 * A week-dot day picker with prev/next paging, each column shows its own weekday letter and
 * day-of-month number (no separate "Sep 13 – Sep 19" range label, which read as a date range
 * to tap rather than a description of the row below it), so the date is unambiguous at a
 * glance. Today gets its own ring around the number, independent of whether it's the day
 * currently selected, so "what day is it" and "what day am I looking at" never get confused.
 * The colored dot below is a data indicator (on-target green fading to red).
 */
@Composable
fun WeekDayPicker(
    days: List<DailyTotal>,
    selectedDate: String,
    calorieGoal: Int,
    calorieBufferKcal: Int,
    onSelectDate: (String) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = DateUtils.today()
    Column(modifier = modifier.fillMaxWidth()) {
        // The week's own first day (always its Sunday) tells us which month to label , 
        // simpler than tracking a separate month state, and right even for a week that
        // starts in one month and ends in the next.
        days.firstOrNull()?.let { firstDay ->
            Text(
                DateUtils.monthYearLabel(firstDay.date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousWeek) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous week",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                days.forEach { day ->
                    DayDot(
                        day = day,
                        isSelected = day.date == selectedDate,
                        isToday = day.date == today,
                        calorieGoal = calorieGoal,
                        calorieBufferKcal = calorieBufferKcal,
                        onClick = { onSelectDate(day.date) }
                    )
                }
            }
            IconButton(onClick = onNextWeek) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next week",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DayDot(
    day: DailyTotal,
    isSelected: Boolean,
    isToday: Boolean,
    calorieGoal: Int,
    calorieBufferKcal: Int,
    onClick: () -> Unit
) {
    val hasData = day.totalCalories > 0
    val dotColor: Color? = when {
        !hasData -> null
        calorieGoal > 0 -> kcalGaugeColor(day.totalCalories, calorieGoal, calorieBufferKcal, MaterialTheme.colorScheme.error)
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                DateUtils.isoToWeekdayLabel(day.date),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            ) {
                Text(
                    DateUtils.dayOfMonthLabel(day.date),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .then(
                        if (dotColor != null) Modifier.background(dotColor)
                        else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
            )
        }
    }
}
