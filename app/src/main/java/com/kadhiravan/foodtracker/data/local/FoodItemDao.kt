package com.kadhiravan.foodtracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Query("SELECT * FROM food_items ORDER BY name ASC")
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items ORDER BY name ASC")
    suspend fun getAll(): List<FoodItem>

    @Query("SELECT * FROM food_items WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): FoodItem?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: FoodItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<FoodItem>)

    @Update
    suspend fun update(item: FoodItem)

    @Delete
    suspend fun delete(item: FoodItem)

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun count(): Int

    @Query("DELETE FROM food_items")
    suspend fun deleteAll()
}
