package com.kadhiravan.foodtracker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted on-device storage for the NVIDIA API key and small user settings.
 * The key is never hardcoded, logged, or included in backups (see backup_rules.xml).
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var nvidiaApiKey: String
        get() = prefs.getString(KEY_NVIDIA_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NVIDIA_API_KEY, value).apply()

    var dailyCalorieGoal: Int
        get() = prefs.getInt(KEY_DAILY_GOAL, 0)
        set(value) = prefs.edit().putInt(KEY_DAILY_GOAL, value).apply()

    companion object {
        private const val KEY_NVIDIA_API_KEY = "nvidia_api_key"
        private const val KEY_DAILY_GOAL = "daily_calorie_goal"
    }
}
