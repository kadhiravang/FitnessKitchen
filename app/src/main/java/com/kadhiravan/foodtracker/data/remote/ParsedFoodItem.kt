package com.kadhiravan.foodtracker.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ParsedFoodItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val calories: Int,
    val matchedKnownFood: Boolean = false
)
