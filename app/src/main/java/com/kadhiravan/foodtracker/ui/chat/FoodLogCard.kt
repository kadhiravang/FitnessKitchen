package com.kadhiravan.foodtracker.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kadhiravan.foodtracker.data.local.CardStatus
import com.kadhiravan.foodtracker.data.local.MealType
import com.kadhiravan.foodtracker.data.remote.LogCard
import com.kadhiravan.foodtracker.ui.components.EditableFoodEntry
import com.kadhiravan.foodtracker.ui.components.EditableFoodRow
import com.kadhiravan.foodtracker.ui.components.displayName
import com.kadhiravan.foodtracker.ui.components.toEditable
import java.util.UUID

/** One editable meal group within a [LogCard] — a card may cover several meals at
 * once (e.g. logging a whole day in one message), each independently editable. */
data class EditableMealGroup(
    val localId: String = UUID.randomUUID().toString(),
    val mealType: MealType,
    val items: List<EditableFoodEntry>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLogCard(
    card: LogCard,
    status: String?,
    onConfirm: (List<EditableMealGroup>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (status) {
        CardStatus.CONFIRMED -> {
            val totalKcal = card.meals.sumOf { meal -> meal.items.sumOf { it.calories } }
            val mealNames = card.meals.joinToString(", ") { it.mealType.displayName() }
            ResolvedCard(
                icon = Icons.Default.Check,
                label = "Logged — $totalKcal kcal to $mealNames",
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier
            )
        }
        CardStatus.DISMISSED -> ResolvedCard(
            icon = null,
            label = "Discarded",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        else -> PendingCard(card = card, onConfirm = onConfirm, onDismiss = onDismiss, modifier = modifier)
    }
}

@Composable
private fun ResolvedCard(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = tint)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingCard(
    card: LogCard,
    onConfirm: (List<EditableMealGroup>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mealGroups by remember {
        mutableStateOf(
            card.meals.map { meal ->
                EditableMealGroup(mealType = meal.mealType, items = meal.items.map { it.toEditable() })
            }
        )
    }

    fun updateGroup(groupId: String, transform: (EditableMealGroup) -> EditableMealGroup) {
        mealGroups = mealGroups.map { if (it.localId == groupId) transform(it) else it }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            val grandTotal = mealGroups.sumOf { group -> group.items.sumOf { it.calories.toIntOrNull() ?: 0 } }
            Text(
                "Total: $grandTotal kcal",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            mealGroups.forEach { group ->
                MealGroupSection(
                    group = group,
                    onChangeMealType = { meal -> updateGroup(group.localId) { it.copy(mealType = meal) } },
                    onChangeItems = { items -> updateGroup(group.localId) { it.copy(items = items) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Discard") }
                Button(
                    onClick = { onConfirm(mealGroups) },
                    modifier = Modifier.weight(1f),
                    enabled = mealGroups.any { it.items.isNotEmpty() }
                ) { Text("Confirm") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealGroupSection(
    group: EditableMealGroup,
    onChangeMealType: (MealType) -> Unit,
    onChangeItems: (List<EditableFoodEntry>) -> Unit,
    modifier: Modifier = Modifier
) {
    var mealMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(top = 10.dp)) {
        ExposedDropdownMenuBox(
            expanded = mealMenuExpanded,
            onExpandedChange = { mealMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = group.mealType.displayName(),
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
                        onClick = {
                            onChangeMealType(meal)
                            mealMenuExpanded = false
                        }
                    )
                }
            }
        }

        group.items.forEach { item ->
            EditableFoodRow(
                entry = item,
                onChange = { updated -> onChangeItems(group.items.map { if (it.localId == updated.localId) updated else it }) },
                onRemove = { onChangeItems(group.items.filterNot { it.localId == item.localId }) }
            )
        }
        TextButton(onClick = {
            onChangeItems(group.items + EditableFoodEntry(name = "", quantity = "1", unit = "serving", calories = "0", matchedKnownFood = false))
        }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add item", modifier = Modifier.padding(start = 4.dp))
        }
    }
}
