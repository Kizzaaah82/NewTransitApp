package com.kiz.transitapp

import android.app.Application
import androidx.work.*
import com.kiz.transitapp.workers.GtfsDownloadWorker
import com.kiz.transitapp.workers.DatabasePreloadWorker
import java.util.concurrent.TimeUnit
import java.util.Calendar

class TransitApplication : Application() {
    val workManagerConfiguration: Configuration = Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.INFO)
        .build()

    override fun onCreate() {
        super.onCreate()
        scheduleGtfsDownload()
        // PERFORMANCE: Schedule database preloading for faster app startup
        // DatabasePreloadWorker.schedulePreload(this) // DISABLED: DatabasePreloadWorker disabled
    }

    private fun scheduleGtfsDownload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        if (now.after(target)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = target.timeInMillis - now.timeInMillis

        val downloadWorkRequest = PeriodicWorkRequestBuilder<GtfsDownloadWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "GtfsDownloadWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            downloadWorkRequest
        )
    }
}
