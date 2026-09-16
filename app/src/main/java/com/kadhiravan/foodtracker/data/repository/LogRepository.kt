package com.kadhiravan.foodtracker.data.repository

import com.kadhiravan.foodtracker.data.local.DailyTotal
import com.kadhiravan.foodtracker.data.local.LogEntry
import com.kadhiravan.foodtracker.data.local.LogEntryDao
import kotlinx.coroutines.flow.Flow

class LogRepository(private val logEntryDao: LogEntryDao) {

    fun observeForDate(date: String): Flow<List<LogEntry>> = logEntryDao.observeForDate(date)

    fun observeLoggedDates(): Flow<List<String>> = logEntryDao.observeLoggedDates()

    fun observeDailyTotalsSince(sinceDate: String): Flow<List<DailyTotal>> =
        logEntryDao.observeDailyTotalsSince(sinceDate)

    fun observeDailyTotalsBetween(startDate: String, endDate: String): Flow<List<DailyTotal>> =
        logEntryDao.observeDailyTotalsBetween(startDate, endDate)

    suspend fun getForDate(date: String): List<LogEntry> = logEntryDao.getForDate(date)

    suspend fun addEntries(entries: List<LogEntry>) = logEntryDao.insertAll(entries)

    suspend fun update(entry: LogEntry) = logEntryDao.update(entry)

    suspend fun delete(entry: LogEntry) = logEntryDao.delete(entry)
}
