package com.kadhiravan.foodtracker.data.backup

import com.kadhiravan.foodtracker.data.local.ChatMessage
import com.kadhiravan.foodtracker.data.local.FoodItem
import com.kadhiravan.foodtracker.data.local.LogEntry
import com.kadhiravan.foodtracker.data.local.WeightEntry
import kotlinx.serialization.Serializable

/** Mirrors [com.kadhiravan.foodtracker.data.local.ProgressPhoto], but with the
 * device-specific absolute [com.kadhiravan.foodtracker.data.local.ProgressPhoto.filePath]
 * replaced by a portable filename (the actual bytes live alongside `backup.json` in the
 * zip, under `photos/progress/`), `fileName` is null when photos weren't included in
 * this backup. */
@Serializable
data class ProgressPhotoBackup(
    val date: String,
    val fileName: String?,
    val loggedAt: Long
)

/** Every [com.kadhiravan.foodtracker.data.prefs.SecurePrefs] field worth restoring.
 * Enum fields are stored as their [Enum.name], matching how SecurePrefs itself persists
 * them, so no separate enum serialization support is needed. [profilePicFileName] is the
 * same portable-filename trick as [ProgressPhotoBackup.fileName], the actual bytes live
 * under `photos/profile/` in the zip. */
@Serializable
data class SecurePrefsBackup(
    val name: String,
    val profilePicFileName: String?,
    val geminiApiKey: String,
    val nvidiaApiKey: String,
    val usdaApiKey: String,
    val chatProvider: String,
    val geminiModel: String,
    val ollamaServerUrl: String,
    val ollamaModel: String,
    val claudeApiKey: String,
    val openaiApiKey: String,
    val openaiModel: String,
    val dailyCalorieGoal: Int,
    val calorieBufferKcal: Int,
    val targetWeightKg: Float,
    val age: Int,
    val heightCm: Float,
    val sex: String?,
    val activityLevel: String,
    val nutritionGoal: String,
    val useCustomMacros: Boolean,
    val customProteinG: Int,
    val customCarbsG: Int,
    val customFatG: Int,
    val recognitionLanguage: String,
    val whisperServerUrl: String,
    val useCloudWhisper: Boolean,
    val whisperApiKey: String,
    val backupIncludePhotos: Boolean
)

/** The whole exported state, serialized to `backup.json` at the root of the backup zip , 
 * see BackupManager for how this is built and consumed. */
@Serializable
data class BackupPayload(
    val formatVersion: Int = 1,
    val exportedAtEpochMs: Long,
    val foodItems: List<FoodItem>,
    val logEntries: List<LogEntry>,
    val chatMessages: List<ChatMessage>,
    val weightEntries: List<WeightEntry>,
    val progressPhotos: List<ProgressPhotoBackup>,
    val prefs: SecurePrefsBackup
)
