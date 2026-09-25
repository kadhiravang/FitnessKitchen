package com.kadhiravan.foodtracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.kadhiravan.foodtracker.data.backup.BackupManager
import com.kadhiravan.foodtracker.data.local.AppDatabase
import com.kadhiravan.foodtracker.data.prefs.SecurePrefs
import com.kadhiravan.foodtracker.data.remote.GoogleApiClient
import com.kadhiravan.foodtracker.data.remote.NvidiaApiClient
import com.kadhiravan.foodtracker.data.remote.ClaudeApiClient
import com.kadhiravan.foodtracker.data.remote.OllamaApiClient
import com.kadhiravan.foodtracker.data.remote.OnDeviceWhisper
import com.kadhiravan.foodtracker.data.remote.OllamaCloudApiClient
import com.kadhiravan.foodtracker.data.remote.OpenAiApiClient
import com.kadhiravan.foodtracker.data.remote.UsdaNutritionClient
import com.kadhiravan.foodtracker.data.repository.ChatRepository
import com.kadhiravan.foodtracker.data.repository.FoodRepository
import com.kadhiravan.foodtracker.data.repository.LogRepository
import com.kadhiravan.foodtracker.data.repository.ProgressPhotoRepository
import com.kadhiravan.foodtracker.data.repository.WeightRepository
import com.kadhiravan.foodtracker.worker.WeightReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

/** Minimal hand-rolled DI container, no framework needed for an app this size. */
class FoodTrackerApp : Application(), Configuration.Provider {

    val applicationScope = CoroutineScope(SupervisorJob())

    private val database by lazy { AppDatabase.getInstance(this, applicationScope) }

    val securePrefs by lazy { SecurePrefs(this) }
    val foodRepository by lazy { FoodRepository(database.foodItemDao()) }
    val logRepository by lazy { LogRepository(database.logEntryDao()) }
    val weightRepository by lazy { WeightRepository(database.weightEntryDao()) }
    val progressPhotoRepository by lazy { ProgressPhotoRepository(database.progressPhotoDao()) }
    val onDeviceWhisper by lazy { OnDeviceWhisper(this) }
    private val usdaNutritionClient by lazy { UsdaNutritionClient() }
    private val googleApiClient by lazy { GoogleApiClient(usdaNutritionClient) }
    private val nvidiaApiClient by lazy { NvidiaApiClient() }
    private val ollamaApiClient by lazy { OllamaApiClient() }
    private val ollamaCloudApiClient by lazy { OllamaCloudApiClient() }
    private val claudeApiClient by lazy { ClaudeApiClient() }
    private val openaiApiClient by lazy { OpenAiApiClient() }
    val chatRepository by lazy {
        ChatRepository(
            database.chatMessageDao(),
            googleApiClient,
            nvidiaApiClient,
            ollamaApiClient,
            ollamaCloudApiClient,
            claudeApiClient,
            openaiApiClient,
            foodRepository,
            logRepository,
            weightRepository,
            securePrefs
        )
    }
    val backupManager by lazy {
        BackupManager(
            database.foodItemDao(),
            database.logEntryDao(),
            database.chatMessageDao(),
            database.weightEntryDao(),
            database.progressPhotoDao(),
            securePrefs,
            this
        )
    }

    // WorkManager builds Workers via reflection by default, which can't supply
    // WeightReminderWorker's repository dependency, this factory constructs it by hand instead.
    private val weightReminderWorkerFactory by lazy {
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = when (workerClassName) {
                WeightReminderWorker::class.java.name -> WeightReminderWorker(appContext, workerParameters, weightRepository)
                else -> null
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(weightReminderWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
        createWeightReminderChannel()
        scheduleWeightReminder()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) registerWhisperBenchReceiver()
    }

    /** Debug builds only: `adb shell am broadcast -a com.kadhiravan.foodtracker.WHISPER_BENCH
     * --es model turbo --es lang "" --ei threads 4` replays every WAV saved in
     * `whisper-samples/` against a model and logs text + timings under tag WhisperBench. */
    private fun registerWhisperBenchReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val model = intent.getStringExtra("model") ?: "turbo"
                val lang = intent.getStringExtra("lang").orEmpty()
                val threads = intent.getIntExtra("threads", OnDeviceWhisper.DEFAULT_THREADS)
                applicationScope.launch(Dispatchers.Default) {
                    val dir = onDeviceWhisper.samplesDir()
                    val files = dir.listFiles().orEmpty().filter { it.extension == "wav" }.sortedBy { it.name }
                    Log.d("WhisperBench", "start model=$model lang='$lang' threads=$threads files=${files.size}")
                    for (f in files) {
                        try {
                            val r = onDeviceWhisper.transcribe(f.readBytes(), model, lang, threads)
                            Log.d(
                                "WhisperBench",
                                "model=$model lang='$lang' file=${f.name} audio=${"%.1f".format(r.audioSeconds)}s " +
                                    "load=${r.loadMs}ms decode=${r.decodeMs}ms " +
                                    "rtf=${"%.2f".format(r.decodeMs / 1000f / r.audioSeconds)} text=${r.text}"
                            )
                        } catch (e: Throwable) {
                            Log.e("WhisperBench", "file=${f.name} failed", e)
                        }
                    }
                    Log.d("WhisperBench", "done model=$model")
                }
            }
        }
        registerReceiver(receiver, IntentFilter("com.kadhiravan.foodtracker.WHISPER_BENCH"), Context.RECEIVER_EXPORTED)
    }

    private fun createWeightReminderChannel() {
        val channel = NotificationChannel(
            WeightReminderWorker.CHANNEL_ID,
            "Weight reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Weekly reminder to log your body weight" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun scheduleWeightReminder() {
        val request = PeriodicWorkRequestBuilder<WeightReminderWorker>(7, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weight_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
