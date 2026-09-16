package com.kadhiravan.foodtracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "progress_photos", indices = [Index(value = ["date"], unique = true)])
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Local calendar date this photo belongs to, formatted yyyy-MM-dd. */
    val date: String,
    /** Absolute path to the JPEG file in the app's private storage. */
    val filePath: String,
    val loggedAt: Long = System.currentTimeMillis()
)
