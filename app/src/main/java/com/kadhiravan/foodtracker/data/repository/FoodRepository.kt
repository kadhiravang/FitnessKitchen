package com.kadhiravan.foodtracker.data.repository

import com.kadhiravan.foodtracker.data.local.FoodItem
import com.kadhiravan.foodtracker.data.local.FoodItemDao
import kotlinx.coroutines.flow.Flow

class FoodRepository(private val foodItemDao: FoodItemDao) {

    fun observeAll(): Flow<List<FoodItem>> = foodItemDao.observeAll()

    suspend fun getAll(): List<FoodItem> = foodItemDao.getAll()

    suspend fun findByName(name: String): FoodItem? = foodItemDao.findByName(name)

    suspend fun save(item: FoodItem) {
        if (item.id == 0L) foodItemDao.insert(item) else foodItemDao.update(item)
    }

    suspend fun delete(item: FoodItem) = foodItemDao.delete(item)
}
