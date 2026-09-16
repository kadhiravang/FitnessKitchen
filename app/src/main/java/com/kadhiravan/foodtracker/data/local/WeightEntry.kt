package com.kadhiravan.foodtracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "weight_entries", indices = [Index(value = ["date"], unique = true)])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Local calendar date this weigh-in belongs to, formatted yyyy-MM-dd. */
    val date: String,
    val weightKg: Double,
    val loggedAt: Long = System.currentTimeMillis()
)
