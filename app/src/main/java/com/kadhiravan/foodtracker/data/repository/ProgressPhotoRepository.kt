package com.kadhiravan.foodtracker.data.repository

import com.kadhiravan.foodtracker.data.local.ProgressPhoto
import com.kadhiravan.foodtracker.data.local.ProgressPhotoDao
import com.kadhiravan.foodtracker.util.DateUtils
import kotlinx.coroutines.flow.Flow
import java.io.File

class ProgressPhotoRepository(private val progressPhotoDao: ProgressPhotoDao) {

    fun observeAll(): Flow<List<ProgressPhoto>> = progressPhotoDao.observeAll()

    suspend fun save(filePath: String, date: String = DateUtils.today()) {
        progressPhotoDao.upsert(ProgressPhoto(date = date, filePath = filePath))
    }

    suspend fun delete(photo: ProgressPhoto) {
        progressPhotoDao.delete(photo)
        File(photo.filePath).delete()
    }
}
