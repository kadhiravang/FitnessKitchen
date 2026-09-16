package com.kadhiravan.foodtracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DailyTotal(
    val date: String,
    val totalCalories: Int,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double
)

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries WHERE logDate = :date ORDER BY loggedAt ASC")
    fun observeForDate(date: String): Flow<List<LogEntry>>

    @Query("SELECT DISTINCT logDate FROM log_entries ORDER BY logDate DESC")
    fun observeLoggedDates(): Flow<List<String>>

    @Query(
        """
        SELECT logDate as date,
               SUM(calories) as totalCalories,
               SUM(proteinG) as totalProtein,
               SUM(carbsG) as totalCarbs,
               SUM(fatG) as totalFat
        FROM log_entries
        WHERE logDate >= :sinceDate
        GROUP BY logDate
        """
    )
    fun observeDailyTotalsSince(sinceDate: String): Flow<List<DailyTotal>>

    @Query(
        """
        SELECT logDate as date,
               SUM(calories) as totalCalories,
               SUM(proteinG) as totalProtein,
               SUM(carbsG) as totalCarbs,
               SUM(fatG) as totalFat
        FROM log_entries
        WHERE logDate >= :startDate AND logDate <= :endDate
        GROUP BY logDate
        """
    )
    fun observeDailyTotalsBetween(startDate: String, endDate: String): Flow<List<DailyTotal>>

    @Query("SELECT * FROM log_entries WHERE logDate = :date ORDER BY loggedAt ASC")
    suspend fun getForDate(date: String): List<LogEntry>

    @Query("SELECT * FROM log_entries")
    suspend fun getAll(): List<LogEntry>

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Insert
    suspend fun insertAll(entries: List<LogEntry>)

    @Update
    suspend fun update(entry: LogEntry)

    @Delete
    suspend fun delete(entry: LogEntry)
}
