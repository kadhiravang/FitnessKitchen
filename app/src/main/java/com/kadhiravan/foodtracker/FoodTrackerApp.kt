package com.kadhiravan.foodtracker

import android.app.Application
import com.kadhiravan.foodtracker.data.local.AppDatabase
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.remote.NvidiaApiClient
import com.kadhiravan.foodtracker.data.repository.FoodRepository
import com.kadhiravan.foodtracker.data.repository.LogRepository
import com.kadhiravan.foodtracker.data.repository.VoiceParsingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Minimal hand-rolled DI container — no framework needed for an app this size. */
class FoodTrackerApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())

    private val database by lazy { AppDatabase.getInstance(this, applicationScope) }

    val securePrefs by lazy { SecurePrefs(this) }
    val foodRepository by lazy { FoodRepository(database.foodItemDao()) }
    val logRepository by lazy { LogRepository(database.logEntryDao()) }
    private val nvidiaApiClient by lazy { NvidiaApiClient() }
    val voiceParsingRepository by lazy {
        VoiceParsingRepository(nvidiaApiClient, foodRepository, securePrefs)
    }
}
