package com.kadhiravan.foodtracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MealType {
    BREAKFAST, LUNCH, DINNER, SNACK
}

@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodName: String,
    val quantity: Double,
    val unit: String,
    val calories: Int,
    val mealType: MealType,
    /** Local calendar date this entry belongs to, formatted yyyy-MM-dd. */
    val logDate: String,
    val loggedAt: Long = System.currentTimeMillis(),
    val sourceFoodItemId: Long? = null
)
