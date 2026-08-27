package com.kadhiravan.foodtracker.ui.fooddb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.local.FoodItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDatabaseScreen(viewModel: FoodDatabaseViewModel, modifier: Modifier = Modifier) {
    val foods by viewModel.foods.collectAsState()
    val query by viewModel.query.collectAsState()
    var editingFood by remember { mutableStateOf<FoodItem?>(null) }
    var showNewFoodDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Food Database") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewFoodDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add food")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search foods") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                items(foods, key = { it.id }) { food ->
                    FoodRow(
                        food = food,
                        onEdit = { editingFood = food },
                        onDelete = { viewModel.delete(food) }
                    )
                }
            }
        }
    }

    if (showNewFoodDialog) {
        FoodEditDialog(
            initial = null,
            onDismiss = { showNewFoodDialog = false },
            onSave = {
                viewModel.save(it)
                showNewFoodDialog = false
            }
        )
    }

    editingFood?.let { food ->
        FoodEditDialog(
            initial = food,
            onDismiss = { editingFood = null },
            onSave = {
                viewModel.save(it)
                editingFood = null
            }
        )
    }
}

@Composable
private fun FoodRow(food: FoodItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${food.caloriesPerServing} kcal / ${food.servingUnit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${food.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${food.name}")
            }
        }
    }
}

@Composable
private fun FoodEditDialog(
    initial: FoodItem?,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var servingUnit by remember { mutableStateOf(initial?.servingUnit ?: "1 serving") }
    var calories by remember { mutableStateOf(initial?.caloriesPerServing?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add food" else "Edit food") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = servingUnit,
                        onValueChange = { servingUnit = it },
                        label = { Text("Serving unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it },
                        label = { Text("kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(90.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val caloriesInt = calories.toIntOrNull() ?: return@TextButton
                    if (name.isBlank()) return@TextButton
                    onSave(
                        (initial ?: FoodItem(name = "", servingUnit = "", caloriesPerServing = 0)).copy(
                            name = name.trim(),
                            servingUnit = servingUnit.trim().ifBlank { "1 serving" },
                            caloriesPerServing = caloriesInt,
                            isCustom = true
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
