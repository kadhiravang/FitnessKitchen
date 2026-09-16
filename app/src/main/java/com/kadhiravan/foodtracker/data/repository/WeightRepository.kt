package com.kadhiravan.foodtracker.data.repository

import com.kadhiravan.foodtracker.data.local.WeightEntry
import com.kadhiravan.foodtracker.data.local.WeightEntryDao
import com.kadhiravan.foodtracker.util.DateUtils
import kotlinx.coroutines.flow.Flow

class WeightRepository(private val weightEntryDao: WeightEntryDao) {

    fun observeAll(): Flow<List<WeightEntry>> = weightEntryDao.observeAll()

    suspend fun getLatest(): WeightEntry? = weightEntryDao.getLatest()

    suspend fun logWeight(weightKg: Double, date: String = DateUtils.today()) {
        weightEntryDao.upsert(WeightEntry(date = date, weightKg = weightKg))
    }
}
