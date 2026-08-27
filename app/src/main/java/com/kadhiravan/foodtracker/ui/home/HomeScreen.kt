package com.kadhiravan.foodtracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.local.MealType
import com.kadhiravan.foodtracker.ui.components.MealSection
import com.kadhiravan.foodtracker.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddViaVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Saapadu") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddViaVoice,
                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                text = { Text("Log by voice") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::goToPreviousDay) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
                }
                Text(
                    DateUtils.isoToDisplay(uiState.date),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = viewModel::goToNextDay) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total: ${uiState.totalCalories} kcal", style = MaterialTheme.typography.headlineSmall)
                    if (uiState.calorieGoal > 0) {
                        Text(
                            "Goal: ${uiState.calorieGoal} kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { (uiState.totalCalories.toFloat() / uiState.calorieGoal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }

            val mealOrder = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
            val nonEmptyMeals = mealOrder.filter { uiState.entriesByMeal[it]?.isNotEmpty() == true }

            if (nonEmptyMeals.isEmpty()) {
                Text(
                    "Nothing logged yet — tap \"Log by voice\" to get started.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(nonEmptyMeals) { meal ->
                        MealSection(
                            mealType = meal,
                            entries = uiState.entriesByMeal[meal].orEmpty(),
                            onDeleteEntry = viewModel::deleteEntry
                        )
                    }
                }
            }
        }
    }
}
