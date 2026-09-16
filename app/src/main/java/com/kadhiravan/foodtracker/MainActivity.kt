package com.kadhiravan.foodtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kadhiravan.foodtracker.ui.navigation.AppNavHost
import com.kadhiravan.foodtracker.ui.theme.FoodTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FoodTrackerApp

        setContent {
            FoodTrackerTheme {
                AppNavHost(
                    foodRepository = app.foodRepository,
                    logRepository = app.logRepository,
                    chatRepository = app.chatRepository,
                    weightRepository = app.weightRepository,
                    progressPhotoRepository = app.progressPhotoRepository,
                    securePrefs = app.securePrefs,
                    backupManager = app.backupManager
                )
            }
        }
    }
}
