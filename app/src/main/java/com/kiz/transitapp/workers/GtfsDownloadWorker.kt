package com.kiz.transitapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class GtfsDownloadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val baseUrl = "https://raw.githubusercontent.com/Kizzaaah82/gtfs/main/"
                val filesToDownload = listOf(
                    "agency.txt", "calendar.txt", "calendar_dates.txt",
                    "fare_attributes.txt", "fare_rules.txt", "feed_info.txt",
                    "routes.txt", "shapes.txt", "stops.txt", "stop_times.txt", "trips.txt"
                )

                filesToDownload.forEach { fileName ->
                    val request = Request.Builder().url(baseUrl + fileName).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val file = File(applicationContext.filesDir, fileName)
                        FileOutputStream(file).use { outputStream ->
                            response.body?.byteStream()?.copyTo(outputStream)
                        }
                        Log.d("GtfsDownloadWorker", "Successfully downloaded $fileName")
                    } else {
                        Log.e("GtfsDownloadWorker", "Failed to download $fileName: ${response.code}")
                        // Optionally, decide if one failure should abort the whole process
                    }
                }
                Result.success()
            } catch (e: Exception) {
                Log.e("GtfsDownloadWorker", "Error downloading GTFS files", e)
                Result.failure()
            }
        }
    }
}

