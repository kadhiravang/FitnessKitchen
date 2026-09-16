package com.kadhiravan.foodtracker.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kadhiravan.foodtracker.MainActivity
import com.kadhiravan.foodtracker.R
import com.kadhiravan.foodtracker.data.repository.WeightRepository
import com.kadhiravan.foodtracker.util.DateUtils

/**
 * Runs roughly weekly (scheduled by [com.kadhiravan.foodtracker.FoodTrackerApp]) and nudges
 * the user if they haven't logged a weigh-in in the last 7 days — skips silently if they're
 * already up to date, so it never nags on a week they've already checked in.
 */
class WeightReminderWorker(
    context: Context,
    params: WorkerParameters,
    private val weightRepository: WeightRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val latest = weightRepository.getLatest()
        val cutoff = DateUtils.offsetFromToday(-7)
        val upToDate = latest != null && latest.date >= cutoff
        if (!upToDate) {
            showReminder()
        }
        return Result.success()
    }

    private fun showReminder() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to check in!")
            .setContentText("It's been a week — update your weight in FitnessKitchen.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "weight_reminders"
        private const val NOTIFICATION_ID = 1001
    }
}
