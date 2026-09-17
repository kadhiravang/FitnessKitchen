package com.kadhiravan.foodtracker.data.backup

import android.content.Context
import android.net.Uri
import com.kadhiravan.foodtracker.data.local.ChatMessageDao
import com.kadhiravan.foodtracker.data.local.FoodItemDao
import com.kadhiravan.foodtracker.data.local.LogEntryDao
import com.kadhiravan.foodtracker.data.local.ProgressPhoto
import com.kadhiravan.foodtracker.data.local.ProgressPhotoDao
import com.kadhiravan.foodtracker.data.local.WeightEntryDao
import com.kadhiravan.foodtracker.data.prefs.ActivityLevel
import com.kadhiravan.foodtracker.data.prefs.ChatProvider
import com.kadhiravan.foodtracker.data.prefs.NutritionGoal
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.prefs.Sex
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exports every table plus the user's settings into a single zip the user picks a
 * destination for (Storage Access Framework, no storage permission needed), and restores
 * one back, including onto a fresh install on another device. `ProgressPhoto.filePath` and
 * `SecurePrefs.profilePicPath` are absolute, device-specific paths, so the zip never stores
 * them directly, only a portable filename, resolved back to a real path against whichever
 * device's `filesDir` is doing the restoring.
 */
class BackupManager(
    private val foodItemDao: FoodItemDao,
    private val logEntryDao: LogEntryDao,
    private val chatMessageDao: ChatMessageDao,
    private val weightEntryDao: WeightEntryDao,
    private val progressPhotoDao: ProgressPhotoDao,
    private val securePrefs: SecurePrefs,
    private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createBackup(destinationUri: Uri, includePhotos: Boolean) {
        val progressPhotos = progressPhotoDao.getAll()
        val profilePicFile = securePrefs.profilePicPath
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() }

        val payload = BackupPayload(
            exportedAtEpochMs = System.currentTimeMillis(),
            foodItems = foodItemDao.getAll(),
            logEntries = logEntryDao.getAll(),
            chatMessages = chatMessageDao.getAll(),
            weightEntries = weightEntryDao.getAll(),
            progressPhotos = progressPhotos.map { photo ->
                val file = File(photo.filePath)
                ProgressPhotoBackup(
                    date = photo.date,
                    fileName = if (includePhotos && file.exists()) file.name else null,
                    loggedAt = photo.loggedAt
                )
            },
            prefs = SecurePrefsBackup(
                name = securePrefs.name,
                profilePicFileName = profilePicFile?.name,
                geminiApiKey = securePrefs.geminiApiKey,
                nvidiaApiKey = securePrefs.nvidiaApiKey,
                usdaApiKey = securePrefs.usdaApiKey,
                chatProvider = securePrefs.chatProvider.name,
                geminiModel = securePrefs.geminiModel,
                dailyCalorieGoal = securePrefs.dailyCalorieGoal,
                calorieBufferKcal = securePrefs.calorieBufferKcal,
                targetWeightKg = securePrefs.targetWeightKg,
                age = securePrefs.age,
                heightCm = securePrefs.heightCm,
                sex = securePrefs.sex?.name,
                activityLevel = securePrefs.activityLevel.name,
                nutritionGoal = securePrefs.nutritionGoal.name,
                useCustomMacros = securePrefs.useCustomMacros,
                customProteinG = securePrefs.customProteinG,
                customCarbsG = securePrefs.customCarbsG,
                customFatG = securePrefs.customFatG,
                recognitionLanguage = securePrefs.recognitionLanguage,
                whisperServerUrl = securePrefs.whisperServerUrl,
                useCloudWhisper = securePrefs.useCloudWhisper,
                whisperApiKey = securePrefs.whisperApiKey,
                backupIncludePhotos = includePhotos
            )
        )

        val out = context.contentResolver.openOutputStream(destinationUri)
            ?: error("Could not open $destinationUri for writing")
        out.use {
            ZipOutputStream(it).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                if (includePhotos) {
                    for (photo in progressPhotos) {
                        val file = File(photo.filePath)
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry("photos/progress/${file.name}"))
                            file.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }

                if (profilePicFile != null) {
                    zip.putNextEntry(ZipEntry("photos/profile/${profilePicFile.name}"))
                    profilePicFile.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun restoreBackup(sourceUri: Uri) {
        val progressPhotosDir = File(context.filesDir, "progress_photos").apply { mkdirs() }
        val profilePicsDir = File(context.filesDir, "profile_pics").apply { mkdirs() }

        var payload: BackupPayload? = null
        val restoredProgressPhotoPaths = mutableMapOf<String, String>()
        var restoredProfilePicPath: String? = null

        val input = context.contentResolver.openInputStream(sourceUri)
            ?: error("Could not open $sourceUri for reading")
        input.use {
            ZipInputStream(it).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "backup.json" -> {
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            payload = json.decodeFromString(BackupPayload.serializer(), text)
                        }
                        name.startsWith("photos/progress/") -> {
                            val fileName = name.removePrefix("photos/progress/")
                            val outFile = File(progressPhotosDir, fileName)
                            outFile.outputStream().use { out -> zip.copyTo(out) }
                            restoredProgressPhotoPaths[fileName] = outFile.absolutePath
                        }
                        name.startsWith("photos/profile/") -> {
                            val fileName = name.removePrefix("photos/profile/")
                            val outFile = File(profilePicsDir, fileName)
                            outFile.outputStream().use { out -> zip.copyTo(out) }
                            restoredProfilePicPath = outFile.absolutePath
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val data = payload ?: error("Backup file has no backup.json")

        foodItemDao.deleteAll()
        logEntryDao.deleteAll()
        chatMessageDao.deleteAll()
        weightEntryDao.deleteAll()
        progressPhotoDao.deleteAll()

        foodItemDao.insertAll(data.foodItems)
        logEntryDao.insertAll(data.logEntries)
        chatMessageDao.insertAll(data.chatMessages)
        weightEntryDao.insertAll(data.weightEntries)
        progressPhotoDao.insertAll(
            data.progressPhotos.mapNotNull { backup ->
                val path = backup.fileName?.let { restoredProgressPhotoPaths[it] } ?: return@mapNotNull null
                ProgressPhoto(date = backup.date, filePath = path, loggedAt = backup.loggedAt)
            }
        )

        val p = data.prefs
        securePrefs.name = p.name
        securePrefs.profilePicPath = p.profilePicFileName?.let { restoredProfilePicPath }.orEmpty()
        securePrefs.geminiApiKey = p.geminiApiKey
        securePrefs.nvidiaApiKey = p.nvidiaApiKey
        securePrefs.usdaApiKey = p.usdaApiKey
        securePrefs.chatProvider = ChatProvider.entries.find { it.name == p.chatProvider } ?: ChatProvider.GOOGLE
        securePrefs.geminiModel = p.geminiModel
        securePrefs.dailyCalorieGoal = p.dailyCalorieGoal
        securePrefs.calorieBufferKcal = p.calorieBufferKcal
        securePrefs.targetWeightKg = p.targetWeightKg
        securePrefs.age = p.age
        securePrefs.heightCm = p.heightCm
        securePrefs.sex = p.sex?.let { name -> Sex.entries.find { it.name == name } }
        securePrefs.activityLevel = ActivityLevel.entries.find { it.name == p.activityLevel } ?: ActivityLevel.MODERATE
        securePrefs.nutritionGoal = NutritionGoal.entries.find { it.name == p.nutritionGoal } ?: NutritionGoal.MAINTAIN
        securePrefs.useCustomMacros = p.useCustomMacros
        securePrefs.customProteinG = p.customProteinG
        securePrefs.customCarbsG = p.customCarbsG
        securePrefs.customFatG = p.customFatG
        securePrefs.recognitionLanguage = p.recognitionLanguage
        securePrefs.whisperServerUrl = p.whisperServerUrl
        securePrefs.useCloudWhisper = p.useCloudWhisper
        securePrefs.whisperApiKey = p.whisperApiKey
        securePrefs.backupIncludePhotos = p.backupIncludePhotos
        // A restored install should never re-run the first-launch wizard.
        securePrefs.onboardingComplete = true
    }
}
