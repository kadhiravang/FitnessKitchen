package com.kadhiravan.foodtracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries WHERE logDate = :date ORDER BY loggedAt ASC")
    fun observeForDate(date: String): Flow<List<LogEntry>>

    @Query("SELECT DISTINCT logDate FROM log_entries ORDER BY logDate DESC")
    fun observeLoggedDates(): Flow<List<String>>

    @Query("SELECT * FROM log_entries WHERE logDate = :date ORDER BY loggedAt ASC")
    suspend fun getForDate(date: String): List<LogEntry>

    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Insert
    suspend fun insertAll(entries: List<LogEntry>)

    @Update
    suspend fun update(entry: LogEntry)

    @Delete
    suspend fun delete(entry: LogEntry)
}
