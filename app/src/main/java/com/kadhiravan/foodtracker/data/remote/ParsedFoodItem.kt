package com.kadhiravan.foodtracker.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ParsedFoodItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val calories: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val matchedKnownFood: Boolean = false
)
