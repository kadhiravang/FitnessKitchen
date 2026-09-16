package com.kadhiravan.foodtracker.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import com.kadhiravan.foodtracker.data.local.LogEntry
import com.kadhiravan.foodtracker.ui.components.formatQuantity
import kotlin.math.roundToInt

/** Edits an already-logged entry. Changing quantity rescales calories and macros
 * automatically, anchored to the entry's original per-unit rate (calories/quantity, etc.)
 * — not to whatever the fields currently show — so repeated quantity edits stay consistent
 * even if calories were manually overridden in between. */
@Composable
fun EditLogEntryDialog(
    entry: LogEntry,
    onDismiss: () -> Unit,
    onSave: (LogEntry) -> Unit
) {
    val baseQuantity = entry.quantity.takeIf { it > 0.0 } ?: 1.0
    val caloriesPerUnit = entry.calories / baseQuantity
    val proteinPerUnit = entry.proteinG / baseQuantity
    val carbsPerUnit = entry.carbsG / baseQuantity
    val fatPerUnit = entry.fatG / baseQuantity

    var name by remember { mutableStateOf(entry.foodName) }
    var quantity by remember { mutableStateOf(formatQuantity(entry.quantity)) }
    var unit by remember { mutableStateOf(entry.unit) }
    var calories by remember { mutableStateOf(entry.calories.toString()) }
    var proteinG by remember { mutableStateOf(formatQuantity(entry.proteinG)) }
    var carbsG by remember { mutableStateOf(formatQuantity(entry.carbsG)) }
    var fatG by remember { mutableStateOf(formatQuantity(entry.fatG)) }

    fun onQuantityChange(newValue: String) {
        quantity = newValue
        val q = newValue.toDoubleOrNull()
        if (q != null && q > 0) {
            calories = (caloriesPerUnit * q).roundToInt().toString()
            proteinG = formatQuantity(proteinPerUnit * q)
            carbsG = formatQuantity(carbsPerUnit * q)
            fatG = formatQuantity(fatPerUnit * q)
        }
    }

    val quantityValue = quantity.toDoubleOrNull()
    val caloriesValue = calories.toIntOrNull()
    val canSave = name.isNotBlank() && quantityValue != null && quantityValue > 0 &&
        caloriesValue != null && caloriesValue >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column {
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
                        onValueChange = ::onQuantityChange,
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
                Text(
                    "Calories and macros scale with quantity — adjust below if needed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        entry.copy(
                            foodName = name.trim(),
                            quantity = quantityValue ?: entry.quantity,
                            unit = unit.trim().ifBlank { entry.unit },
                            calories = caloriesValue ?: entry.calories,
                            proteinG = proteinG.toDoubleOrNull() ?: entry.proteinG,
                            carbsG = carbsG.toDoubleOrNull() ?: entry.carbsG,
                            fatG = fatG.toDoubleOrNull() ?: entry.fatG
                        )
                    )
                },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
