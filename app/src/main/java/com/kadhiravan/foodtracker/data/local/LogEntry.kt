package com.kadhiravan.foodtracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class MealType {
    BREAKFAST, LUNCH, DINNER, SNACK
}

@Serializable
@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodName: String,
    val quantity: Double,
    val unit: String,
    val calories: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val mealType: MealType,
    /** Local calendar date this entry belongs to, formatted yyyy-MM-dd. */
    val logDate: String,
    val loggedAt: Long = System.currentTimeMillis(),
    val sourceFoodItemId: Long? = null
)
