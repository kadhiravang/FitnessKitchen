package com.kadhiravan.foodtracker.data.repository

import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.remote.NvidiaApiClient
import com.kadhiravan.foodtracker.data.remote.ParsedFoodItem

class VoiceParsingRepository(
    private val apiClient: NvidiaApiClient,
    private val foodRepository: FoodRepository,
    private val securePrefs: SecurePrefs
) {
    suspend fun parseTranscript(transcript: String): Result<List<ParsedFoodItem>> {
        return try {
            val knownFoods = foodRepository.getAll()
            val result = apiClient.parseTranscript(
                transcript = transcript,
                apiKey = securePrefs.nvidiaApiKey,
                knownFoods = knownFoods
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
