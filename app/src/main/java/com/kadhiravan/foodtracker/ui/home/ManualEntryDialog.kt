package com.kadhiravan.foodtracker.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.local.MealType
import com.kadhiravan.foodtracker.ui.components.displayName
import com.kadhiravan.foodtracker.util.DateUtils

/** Quick-add a single food entry directly, no AI involved, for when the chat model's
 * rate limit is exhausted or you just want to type a number in yourself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        quantity: Double,
        unit: String,
        calories: Int,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        mealType: MealType
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("serving") }
    var calories by remember { mutableStateOf("") }
    var proteinG by remember { mutableStateOf("") }
    var carbsG by remember { mutableStateOf("") }
    var fatG by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(DateUtils.defaultMealTypeForNow()) }
    var mealMenuExpanded by remember { mutableStateOf(false) }

    val quantityValue = quantity.toDoubleOrNull()
    val caloriesValue = calories.toIntOrNull()
    val canSave = name.isNotBlank() && quantityValue != null && quantityValue > 0 && caloriesValue != null && caloriesValue >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add food manually") },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = mealMenuExpanded,
                    onExpandedChange = { mealMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = mealType.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Meal") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = mealMenuExpanded,
                        onDismissRequest = { mealMenuExpanded = false }
                    ) {
                        MealType.entries.forEach { meal ->
                            DropdownMenuItem(
                                text = { Text(meal.displayName()) },
                                onClick = { mealType = meal; mealMenuExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Food name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Qty") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
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
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Macros (optional, grams)", style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = proteinG,
                        onValueChange = { proteinG = it },
                        label = { Text("Protein") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = carbsG,
                        onValueChange = { carbsG = it },
                        label = { Text("Carbs") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = fatG,
                        onValueChange = { fatG = it },
                        label = { Text("Fat") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        quantityValue ?: 1.0,
                        unit.trim().ifBlank { "serving" },
                        caloriesValue ?: 0,
                        proteinG.toDoubleOrNull() ?: 0.0,
                        carbsG.toDoubleOrNull() ?: 0.0,
                        fatG.toDoubleOrNull() ?: 0.0,
                        mealType
                    )
                },
                enabled = canSave
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
