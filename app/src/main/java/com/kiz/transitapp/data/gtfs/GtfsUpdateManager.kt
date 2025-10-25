package com.kiz.transitapp.data.gtfs

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*
import java.util.concurrent.TimeUnit

class GtfsUpdateManager(private val context: Context) {

    companion object {
        private const val DAILY_UPDATE_WORK = "daily_gtfs_update"
        private const val MANUAL_UPDATE_WORK = "manual_gtfs_update"
    }

    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedule daily GTFS file downloads at 2 AM
     */
    fun scheduleDaily2AMUpdates() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        // Calculate delay until next 2 AM
        val currentTime = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If 2 AM has already passed today, schedule for tomorrow
            if (before(currentTime)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val initialDelay = targetTime.timeInMillis - currentTime.timeInMillis

        val dailyWorkRequest = PeriodicWorkRequestBuilder<GtfsDownloadWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                15000L, // 15 seconds minimum backoff
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            DAILY_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWorkRequest
        )
    }

    /**
     * Manually trigger GTFS file download
     */
    fun triggerManualUpdate(): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val manualWorkRequest = OneTimeWorkRequestBuilder<GtfsDownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                15000L, // 15 seconds minimum backoff
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            MANUAL_UPDATE_WORK,
            ExistingWorkPolicy.REPLACE,
            manualWorkRequest
        )

        return manualWorkRequest.id
    }

    /**
     * Get the status of manual update work
     */
    fun getManualUpdateStatus(): Flow<WorkInfo?> {
        return workManager.getWorkInfosForUniqueWorkFlow(MANUAL_UPDATE_WORK)
            .map { workInfos -> workInfos.firstOrNull() }
    }

    /**
     * Get the status of daily update work
     */
    fun getDailyUpdateStatus(): Flow<WorkInfo?> {
        return workManager.getWorkInfosForUniqueWorkFlow(DAILY_UPDATE_WORK)
            .map { workInfos -> workInfos.firstOrNull() }
    }

    /**
     * Cancel all scheduled updates
     */
    fun cancelAllUpdates() {
        workManager.cancelUniqueWork(DAILY_UPDATE_WORK)
        workManager.cancelUniqueWork(MANUAL_UPDATE_WORK)
    }

    /**
     * Check if updates are currently scheduled
     */
    fun isUpdateScheduled(): Flow<Boolean> {
        return workManager.getWorkInfosForUniqueWorkFlow(DAILY_UPDATE_WORK)
            .map { workInfos ->
                workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            }
    }
}
