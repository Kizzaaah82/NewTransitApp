package com.kiz.transitapp.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.kiz.transitapp.data.database.GTFSDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ClassNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = GTFSDatabase.getDatabase(context)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val className = inputData.getString("class_name") ?: return@withContext Result.failure()
            val classTime = inputData.getString("class_time") ?: return@withContext Result.failure()
            val busStopId = inputData.getString("bus_stop_id") ?: return@withContext Result.failure()

            // Calculate optimal departure time
            val optimalDepartureTime = calculateOptimalDepartureTime(classTime, busStopId)
            val currentTime = Calendar.getInstance()

            if (optimalDepartureTime != null) {
                val timeDiff = (optimalDepartureTime.timeInMillis - currentTime.timeInMillis) / (1000 * 60) // minutes

                when {
                    timeDiff <= 0 -> {
                        sendNotification(
                            "⚠️ Class Alert",
                            "Your bus to $className has already left! You might want to check the next one.",
                            android.R.drawable.stat_notify_error
                        )
                    }
                    timeDiff <= 5 -> {
                        sendNotification(
                            "🏃‍♂️ Time to Go!",
                            "Your bus to $className leaves in ${timeDiff.toInt()} mins. Time to hustle!",
                            android.R.drawable.stat_notify_chat
                        )
                    }
                    timeDiff <= 10 -> {
                        sendNotification(
                            "🚌 Bus Reminder",
                            "Your bus to $className leaves in ${timeDiff.toInt()} mins. If you miss it, I'm telling your instructor.",
                            android.R.drawable.stat_notify_chat
                        )
                    }
                    timeDiff <= 15 -> {
                        sendNotification(
                            "📚 Get Ready",
                            "Your bus to $className leaves in ${timeDiff.toInt()} mins. Time to grab your books!",
                            android.R.drawable.stat_notify_chat
                        )
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun calculateOptimalDepartureTime(classTime: String, busStopId: String): Calendar? {
        try {
            // Parse class time
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val classDate = timeFormat.parse(classTime) ?: return null

            val classCalendar = Calendar.getInstance().apply {
                time = classDate
            }

            // Get stop times for the bus stop
            val stopTimes = database.stopTimeDao().getStopTimesByStop(busStopId)

            // Find the best bus that arrives before class time
            val bestBus = stopTimes
                .filter { stopTime ->
                    val busTime = parseTime(stopTime.departure_time)
                    busTime != null && busTime.before(classCalendar.time)
                }
                .maxByOrNull { stopTime ->
                    parseTime(stopTime.departure_time)?.time ?: 0L
                }

            if (bestBus != null) {
                val busTime = parseTime(bestBus.departure_time)
                if (busTime != null) {
                    val busCalendar = Calendar.getInstance().apply {
                        time = busTime
                    }

                    // Subtract walking time to bus stop (assume 5 minutes)
                    busCalendar.add(Calendar.MINUTE, -5)

                    // Subtract buffer time (assume 2 minutes)
                    busCalendar.add(Calendar.MINUTE, -2)

                    return busCalendar
                }
            }

            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseTime(timeString: String): Date? {
        return try {
            val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            format.parse(timeString)
        } catch (e: Exception) {
            null
        }
    }

    private fun sendNotification(title: String, message: String, icon: Int) {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Class Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming classes and bus departures"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "class_reminders"
        private const val NOTIFICATION_ID = 1

        fun scheduleNotification(
            context: Context,
            className: String,
            classTime: String,
            busStopId: String,
            delayMinutes: Long
        ) {
            val workRequest = OneTimeWorkRequestBuilder<ClassNotificationWorker>()
                .setInitialDelay(delayMinutes, java.util.concurrent.TimeUnit.MINUTES)
                .setInputData(
                    Data.Builder()
                        .putString("class_name", className)
                        .putString("class_time", classTime)
                        .putString("bus_stop_id", busStopId)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
