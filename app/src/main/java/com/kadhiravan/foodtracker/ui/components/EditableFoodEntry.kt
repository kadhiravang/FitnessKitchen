package com.kadhiravan.foodtracker.ui.components

import com.kadhiravan.foodtracker.data.remote.ParsedFoodItem
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Text-field-backed mirror of [ParsedFoodItem] for in-progress editing before it's saved.
 * Macros ride along from the AI's estimate but aren't individually editable here, only
 * name/quantity/unit/calories are, to keep the quick-confirm card from getting cluttered.
 *
 * [caloriesPerUnit]/[proteinPerUnit]/etc. are fixed at creation time (see [toEditable]) and
 * never touched by [withQuantity] itself, so repeated quantity edits rescale from the
 * original AI estimate rather than compounding off whatever's currently displayed.
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
    val matchedKnownFood: Boolean,
    val caloriesPerUnit: Double = 0.0,
    val proteinPerUnit: Double = 0.0,
    val carbsPerUnit: Double = 0.0,
    val fatPerUnit: Double = 0.0
)

fun ParsedFoodItem.toEditable(): EditableFoodEntry {
    val baseQuantity = quantity.takeIf { it > 0.0 } ?: 1.0
    return EditableFoodEntry(
        name = name,
        quantity = formatQuantityInput(quantity),
        unit = unit,
        calories = calories.toString(),
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        matchedKnownFood = matchedKnownFood,
        caloriesPerUnit = calories / baseQuantity,
        proteinPerUnit = proteinG / baseQuantity,
        carbsPerUnit = carbsG / baseQuantity,
        fatPerUnit = fatG / baseQuantity
    )
}

/** Applies a new quantity string, rescaling calories/macros from the fixed per-unit rate
 * when it parses to a positive number; otherwise just updates the raw text so partial input
 * (e.g. "1.") doesn't get clobbered mid-typing. */
fun EditableFoodEntry.withQuantity(newQuantity: String): EditableFoodEntry {
    val q = newQuantity.toDoubleOrNull()
    return if (q != null && q > 0) {
        copy(
            quantity = newQuantity,
            calories = (caloriesPerUnit * q).roundToInt().toString(),
            proteinG = proteinPerUnit * q,
            carbsG = carbsPerUnit * q,
            fatG = fatPerUnit * q
        )
    } else {
        copy(quantity = newQuantity)
    }
}

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
