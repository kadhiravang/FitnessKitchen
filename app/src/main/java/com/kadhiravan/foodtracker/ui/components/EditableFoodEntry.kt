package com.kadhiravan.foodtracker.ui.components

import com.kadhiravan.foodtracker.data.remote.ParsedFoodItem
import java.util.UUID

/**
 * Text-field-backed mirror of [ParsedFoodItem] for in-progress editing before it's saved.
 * Macros ride along from the AI's estimate but aren't individually editable here — only
 * name/quantity/unit/calories are, to keep the quick-confirm card from getting cluttered.
 */
data class EditableFoodEntry(
    val localId: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: String,
    val unit: String,
    val calories: String,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val matchedKnownFood: Boolean
)

fun ParsedFoodItem.toEditable() = EditableFoodEntry(
    name = name,
    quantity = formatQuantityInput(quantity),
    unit = unit,
    calories = calories.toString(),
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    matchedKnownFood = matchedKnownFood
)

fun EditableFoodEntry.toParsedOrNull(): ParsedFoodItem? {
    val q = quantity.toDoubleOrNull() ?: return null
    val cal = calories.toIntOrNull() ?: return null
    if (name.isBlank()) return null
    return ParsedFoodItem(
        name = name.trim(),
        quantity = q,
        unit = unit.trim().ifBlank { "serving" },
        calories = cal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        matchedKnownFood = matchedKnownFood
    )
}

private fun formatQuantityInput(quantity: Double): String =
    if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString() else quantity.toString()
