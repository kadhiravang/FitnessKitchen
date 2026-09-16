package com.kadhiravan.foodtracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressPhotoDao {
    @Query("SELECT * FROM progress_photos ORDER BY date DESC")
    fun observeAll(): Flow<List<ProgressPhoto>>

    @Query("SELECT * FROM progress_photos")
    suspend fun getAll(): List<ProgressPhoto>

    @Query("DELETE FROM progress_photos")
    suspend fun deleteAll()

    /** Replaces any existing photo for the same date, one progress photo per day. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: ProgressPhoto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<ProgressPhoto>)

    @Delete
    suspend fun delete(photo: ProgressPhoto)
}
