package com.kadhiravan.foodtracker.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted on-device storage for chat API keys and small user settings.
 * Keys are never hardcoded, logged, or included in backups (see backup_rules.xml).
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

    /** True once the first-launch onboarding flow has been completed — gates whether
     * [com.kadhiravan.foodtracker.ui.navigation.AppNavHost] starts on onboarding or the
     * main app. */
    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    var name: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    /** Absolute path to the profile picture file in app-private storage — empty means unset.
     * This is always the original, un-cropped file the user picked or captured — cropping
     * (see [profilePicCropLeft]) never replaces or edits it, only records which region of it
     * to show, so re-cropping later always has the full original to work from. */
    var profilePicPath: String
        get() = prefs.getString(KEY_PROFILE_PIC_PATH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROFILE_PIC_PATH, value).apply()

    /** Normalized (0..1) top-left X of the square region of [profilePicPath] to display —
     * -1 means no crop has been set, so the full image is shown center-cropped by default. */
    var profilePicCropLeft: Float
        get() = prefs.getFloat(KEY_PROFILE_PIC_CROP_LEFT, -1f)
        set(value) = prefs.edit().putFloat(KEY_PROFILE_PIC_CROP_LEFT, value).apply()

    /** Normalized (0..1) top-left Y of the crop region — see [profilePicCropLeft]. */
    var profilePicCropTop: Float
        get() = prefs.getFloat(KEY_PROFILE_PIC_CROP_TOP, 0f)
        set(value) = prefs.edit().putFloat(KEY_PROFILE_PIC_CROP_TOP, value).apply()

    /** Normalized (0..1, relative to the image's shorter side) size of the square crop
     * region — see [profilePicCropLeft]. */
    var profilePicCropSize: Float
        get() = prefs.getFloat(KEY_PROFILE_PIC_CROP_SIZE, 1f)
        set(value) = prefs.edit().putFloat(KEY_PROFILE_PIC_CROP_SIZE, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    var nvidiaApiKey: String
        get() = prefs.getString(KEY_NVIDIA_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NVIDIA_API_KEY, value).apply()

    /** USDA FoodData Central key — lets the Gemini chat model call a real nutrition
     * lookup tool for unfamiliar foods instead of only estimating from memory. Free at
     * fdc.nal.usda.gov/api-key-signup. Blank just disables the tool, not the chat. */
    var usdaApiKey: String
        get() = prefs.getString(KEY_USDA_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USDA_API_KEY, value).apply()

    /** Which chat provider backs the Chat tab — see [ChatProvider]. */
    var chatProvider: ChatProvider
        get() = ChatProvider.entries.find { it.name == prefs.getString(KEY_CHAT_PROVIDER, null) } ?: ChatProvider.GOOGLE
        set(value) = prefs.edit().putString(KEY_CHAT_PROVIDER, value.name).apply()

    var dailyCalorieGoal: Int
        get() = prefs.getInt(KEY_DAILY_GOAL, 0)
        set(value) = prefs.edit().putInt(KEY_DAILY_GOAL, value).apply()

    /** ± kcal around the calorie goal that counts as "on target" — shown as two marker
     * lines on the Diary gauge instead of one, and drives the gauge's green/amber/red
     * grading (see kcalGaugeColor in DiarySummary.kt). Defaults to 100 kcal. */
    var calorieBufferKcal: Int
        get() = prefs.getInt(KEY_CALORIE_BUFFER, 100)
        set(value) = prefs.edit().putInt(KEY_CALORIE_BUFFER, value).apply()

    /** Target body weight in kg for the Diary tab's goal-progress card — 0 means unset. */
    var targetWeightKg: Float
        get() = prefs.getFloat(KEY_TARGET_WEIGHT, 0f)
        set(value) = prefs.edit().putFloat(KEY_TARGET_WEIGHT, value).apply()

    /** Age in years for the calorie/macro calculator — 0 means unset. */
    var age: Int
        get() = prefs.getInt(KEY_AGE, 0)
        set(value) = prefs.edit().putInt(KEY_AGE, value).apply()

    /** Height in cm for the calorie/macro calculator — 0 means unset. */
    var heightCm: Float
        get() = prefs.getFloat(KEY_HEIGHT_CM, 0f)
        set(value) = prefs.edit().putFloat(KEY_HEIGHT_CM, value).apply()

    var sex: Sex?
        get() = prefs.getString(KEY_SEX, null)?.let { name -> Sex.entries.find { it.name == name } }
        set(value) = prefs.edit().putString(KEY_SEX, value?.name).apply()

    var activityLevel: ActivityLevel
        get() = ActivityLevel.entries.find { it.name == prefs.getString(KEY_ACTIVITY_LEVEL, null) } ?: ActivityLevel.MODERATE
        set(value) = prefs.edit().putString(KEY_ACTIVITY_LEVEL, value.name).apply()

    var nutritionGoal: NutritionGoal
        get() = NutritionGoal.entries.find { it.name == prefs.getString(KEY_NUTRITION_GOAL, null) } ?: NutritionGoal.MAINTAIN
        set(value) = prefs.edit().putString(KEY_NUTRITION_GOAL, value.name).apply()

    /** True once enough profile fields are set to run the calorie/macro calculator —
     * current weight (from the weight tracker) is checked separately, since it lives in
     * WeightRepository rather than here. */
    fun hasProfileBasics(): Boolean = age > 0 && heightCm > 0f && sex != null

    /** When true, [customProteinG]/[customCarbsG]/[customFatG] override whatever the
     * calculator (or flat split) would have produced — the calorie goal itself is untouched,
     * these just replace how it's divided. Only ever saved when they fit within that goal. */
    var useCustomMacros: Boolean
        get() = prefs.getBoolean(KEY_USE_CUSTOM_MACROS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_CUSTOM_MACROS, value).apply()

    var customProteinG: Int
        get() = prefs.getInt(KEY_CUSTOM_PROTEIN, 0)
        set(value) = prefs.edit().putInt(KEY_CUSTOM_PROTEIN, value).apply()

    var customCarbsG: Int
        get() = prefs.getInt(KEY_CUSTOM_CARBS, 0)
        set(value) = prefs.edit().putInt(KEY_CUSTOM_CARBS, value).apply()

    var customFatG: Int
        get() = prefs.getInt(KEY_CUSTOM_FAT, 0)
        set(value) = prefs.edit().putInt(KEY_CUSTOM_FAT, value).apply()

    /** BCP-47 tag for voice recognition, e.g. "en-IN" or "ta-IN" — see [SpeechLanguage]. */
    var recognitionLanguage: String
        get() = prefs.getString(KEY_RECOGNITION_LANGUAGE, SpeechLanguage.DEFAULT) ?: SpeechLanguage.DEFAULT
        set(value) = prefs.edit().putString(KEY_RECOGNITION_LANGUAGE, value).apply()

    /** Base URL of the local Whisper transcription server (e.g. "http://10.0.0.250:8765"),
     * reachable only on the same Wi-Fi with the laptop's server running — the app falls
     * back to the on-device recognizer automatically when it can't be reached. */
    var whisperServerUrl: String
        get() = prefs.getString(KEY_WHISPER_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WHISPER_SERVER_URL, value).apply()

    /** When true, voice input is transcribed via OpenAI's hosted Whisper API using
     * [whisperApiKey] instead of probing [whisperServerUrl] for a self-hosted server —
     * for users who don't want to run whisper-server/ on their own machine. */
    var useCloudWhisper: Boolean
        get() = prefs.getBoolean(KEY_USE_CLOUD_WHISPER, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_CLOUD_WHISPER, value).apply()

    var whisperApiKey: String
        get() = prefs.getString(KEY_WHISPER_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WHISPER_API_KEY, value).apply()

    /** Whether a backup zip (see data/backup/BackupManager.kt) includes progress-photo
     * image files — off just skips the photos, the rest of the backup is unaffected. */
    var backupIncludePhotos: Boolean
        get() = prefs.getBoolean(KEY_BACKUP_INCLUDE_PHOTOS, true)
        set(value) = prefs.edit().putBoolean(KEY_BACKUP_INCLUDE_PHOTOS, value).apply()

    companion object {
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_NAME = "name"
        private const val KEY_PROFILE_PIC_PATH = "profile_pic_path"
        private const val KEY_PROFILE_PIC_CROP_LEFT = "profile_pic_crop_left"
        private const val KEY_PROFILE_PIC_CROP_TOP = "profile_pic_crop_top"
        private const val KEY_PROFILE_PIC_CROP_SIZE = "profile_pic_crop_size"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_NVIDIA_API_KEY = "nvidia_api_key"
        private const val KEY_USDA_API_KEY = "usda_api_key"
        private const val KEY_CHAT_PROVIDER = "chat_provider"
        private const val KEY_DAILY_GOAL = "daily_calorie_goal"
        private const val KEY_CALORIE_BUFFER = "calorie_buffer_kcal"
        private const val KEY_TARGET_WEIGHT = "target_weight_kg"
        private const val KEY_AGE = "age"
        private const val KEY_HEIGHT_CM = "height_cm"
        private const val KEY_SEX = "sex"
        private const val KEY_ACTIVITY_LEVEL = "activity_level"
        private const val KEY_NUTRITION_GOAL = "nutrition_goal"
        private const val KEY_USE_CUSTOM_MACROS = "use_custom_macros"
        private const val KEY_CUSTOM_PROTEIN = "custom_protein_g"
        private const val KEY_CUSTOM_CARBS = "custom_carbs_g"
        private const val KEY_CUSTOM_FAT = "custom_fat_g"
        private const val KEY_RECOGNITION_LANGUAGE = "recognition_language"
        private const val KEY_WHISPER_SERVER_URL = "whisper_server_url"
        private const val KEY_USE_CLOUD_WHISPER = "use_cloud_whisper"
        private const val KEY_WHISPER_API_KEY = "whisper_api_key"
        private const val KEY_BACKUP_INCLUDE_PHOTOS = "backup_include_photos"
    }
}

enum class ChatProvider(val displayName: String) {
    GOOGLE("Google Gemini"),
    NVIDIA("NVIDIA (deepseek)")
}

enum class Sex(val displayName: String) {
    MALE("Male"),
    FEMALE("Female")
}

/** Standard activity-level multipliers applied to BMR to estimate TDEE. */
enum class ActivityLevel(val multiplier: Double, val displayName: String) {
    SEDENTARY(1.2, "Sedentary"),
    LIGHT(1.375, "Lightly active"),
    MODERATE(1.55, "Moderately active"),
    ACTIVE(1.725, "Very active"),
    EXTRA(1.9, "Extra active")
}

enum class NutritionGoal(val displayName: String) {
    LOSE("Lose weight"),
    MAINTAIN("Maintain weight"),
    GAIN("Gain weight")
}

object SpeechLanguage {
    const val ENGLISH_INDIA = "en-IN"
    const val TAMIL = "ta-IN"
    const val DEFAULT = ENGLISH_INDIA

    val options = listOf(ENGLISH_INDIA to "English (India)", TAMIL to "Tamil")

    /** Languages the on-device recognizer is allowed to auto-switch between mid-utterance. */
    val SWITCH_LANGUAGES = listOf(ENGLISH_INDIA, TAMIL)
}
