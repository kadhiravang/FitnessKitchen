package com.kadhiravan.foodtracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.local.FoodItem
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.ui.fooddb.FoodDatabaseViewModel
import com.kadhiravan.foodtracker.ui.fooddb.FoodEditDialog
import com.kadhiravan.foodtracker.ui.fooddb.FoodRow
import com.kadhiravan.foodtracker.ui.fooddb.FoodSearchBar
import com.kadhiravan.foodtracker.ui.home.kcalGaugeColor
import com.kadhiravan.foodtracker.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyViewModel: HistoryViewModel,
    foodDbViewModel: FoodDatabaseViewModel,
    securePrefs: SecurePrefs,
    onDaySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthLabel by historyViewModel.monthLabel.collectAsState()
    val leadingBlanks by historyViewModel.leadingBlanks.collectAsState()
    val monthDays by historyViewModel.monthDays.collectAsState()
    val calorieGoal = remember { securePrefs.dailyCalorieGoal }
    val calorieBufferKcal = remember { securePrefs.calorieBufferKcal }

    val foods by foodDbViewModel.foods.collectAsState()
    val query by foodDbViewModel.query.collectAsState()
    var editingFood by remember { mutableStateOf<FoodItem?>(null) }
    var showNewFoodDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewFoodDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add food")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CalendarCard(
                    monthLabel = monthLabel,
                    leadingBlanks = leadingBlanks,
                    days = monthDays,
                    calorieGoal = calorieGoal,
                    calorieBufferKcal = calorieBufferKcal,
                    onPreviousMonth = historyViewModel::goToPreviousMonth,
                    onNextMonth = historyViewModel::goToNextMonth,
                    onDaySelected = onDaySelected
                )
            }

            item {
                Text(
                    "Foods",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                FoodSearchBar(query = query, onQueryChange = foodDbViewModel::onQueryChange)
            }

            items(foods, key = { it.id }) { food ->
                FoodRow(
                    food = food,
                    onEdit = { editingFood = food },
                    onDelete = { foodDbViewModel.delete(food) }
                )
            }
        }
    }

    if (showNewFoodDialog) {
        FoodEditDialog(
            initial = null,
            onDismiss = { showNewFoodDialog = false },
            onSave = {
                foodDbViewModel.save(it)
                showNewFoodDialog = false
            }
        )
    }

    editingFood?.let { food ->
        FoodEditDialog(
            initial = food,
            onDismiss = { editingFood = null },
            onSave = {
                foodDbViewModel.save(it)
                editingFood = null
            }
        )
    }
}

@Composable
private fun CalendarCard(
    monthLabel: String,
    leadingBlanks: Int,
    days: List<CalendarDay>,
    calorieGoal: Int,
    calorieBufferKcal: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(monthLabel, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onNextMonth) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val today = DateUtils.today()
            val cells: List<CalendarDay?> = buildList {
                repeat(leadingBlanks) { add(null) }
                addAll(days)
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                val isToday = day.date == today
                                // Same color scale as the Diary gauge — green/amber/red by how
                                // close the day landed to the calorie goal — so the calendar
                                // reads at a glance as a consistency tracker, not just an
                                // "did I log anything" checklist.
                                val dayColor = if (day.hasData) {
                                    kcalGaugeColor(day.totalCalories, calorieGoal, calorieBufferKcal, MaterialTheme.colorScheme.error)
                                } else null
                                val onDayColor = Color.Black.copy(alpha = 0.8f)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .then(if (dayColor != null) Modifier.background(dayColor) else Modifier)
                                        .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                        .clickable { onDaySelected(day.date) }
                                ) {
                                    Text(
                                        "${day.dayOfMonth}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (dayColor != null) onDayColor else Color.Unspecified,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (day.hasData) {
                                        Text(
                                            "${day.totalCalories}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = onDayColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}
