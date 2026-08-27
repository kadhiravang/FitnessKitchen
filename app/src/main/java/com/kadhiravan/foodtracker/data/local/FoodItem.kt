package com.kadhiravan.foodtracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val servingUnit: String,
    val caloriesPerServing: Int,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val isCustom: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
