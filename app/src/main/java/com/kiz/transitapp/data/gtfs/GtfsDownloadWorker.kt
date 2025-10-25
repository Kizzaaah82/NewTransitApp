package com.kiz.transitapp.data.gtfs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class GtfsDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        // Windsor Transit official GTFS ZIP URL
        const val WINDSOR_GTFS_ZIP_URL = "https://opendata.citywindsor.ca/Uploads/google_transit.zip"
        const val PROGRESS_KEY = "PROGRESS"
        const val STATUS_KEY = "STATUS"

        val REQUIRED_GTFS_FILES = listOf(
            "agency.txt",
            "routes.txt",
            "stops.txt",
            "trips.txt",
            "stop_times.txt"
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setProgress(workDataOf(STATUS_KEY to "Starting GTFS download from Windsor Transit...", PROGRESS_KEY to 0))

            // Create temporary directories
            val tempDir = File(applicationContext.cacheDir, "gtfs_temp")
            val extractDir = File(applicationContext.cacheDir, "gtfs_extract")

            if (tempDir.exists()) tempDir.deleteRecursively()
            if (extractDir.exists()) extractDir.deleteRecursively()

            tempDir.mkdirs()
            extractDir.mkdirs()

            // Step 1: Download ZIP file
            setProgress(workDataOf(STATUS_KEY to "Downloading GTFS ZIP from Windsor Transit...", PROGRESS_KEY to 10))
            val zipFile = File(tempDir, "google_transit.zip")

            if (!downloadZipFile(zipFile)) {
                return@withContext Result.failure(workDataOf(STATUS_KEY to "Failed to download GTFS ZIP file"))
            }

            // Step 2: Extract ZIP file
            setProgress(workDataOf(STATUS_KEY to "Extracting GTFS files...", PROGRESS_KEY to 40))

            if (!extractZipFile(zipFile, extractDir)) {
                return@withContext Result.failure(workDataOf(STATUS_KEY to "Failed to extract GTFS ZIP file"))
            }

            // Step 3: Validate extracted files
            setProgress(workDataOf(STATUS_KEY to "Validating GTFS files...", PROGRESS_KEY to 70))

            if (!validateGtfsFiles(extractDir)) {
                return@withContext Result.failure(workDataOf(STATUS_KEY to "Downloaded GTFS files failed validation"))
            }

            // Step 4: Move files to final location
            setProgress(workDataOf(STATUS_KEY to "Installing GTFS files...", PROGRESS_KEY to 85))

            moveFilesToFinalLocation(extractDir)

            // Step 5: Cleanup
            setProgress(workDataOf(STATUS_KEY to "Cleaning up...", PROGRESS_KEY to 95))
            tempDir.deleteRecursively()
            extractDir.deleteRecursively()

            setProgress(workDataOf(STATUS_KEY to "GTFS update completed successfully!", PROGRESS_KEY to 100))

            Result.success(workDataOf(
                STATUS_KEY to "Successfully downloaded and installed fresh GTFS data from Windsor Transit"
            ))

        } catch (e: Exception) {
            android.util.Log.e("GtfsDownload", "GTFS download failed", e)
            Result.failure(workDataOf(STATUS_KEY to "Download failed: ${e.message}"))
        }
    }

    private suspend fun downloadZipFile(zipFile: File): Boolean {
        return try {
            val url = URL(WINDSOR_GTFS_ZIP_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 60000  // 60 seconds for ZIP file
            connection.readTimeout = 120000     // 2 minutes for large file
            connection.setRequestProperty("User-Agent", "Windsor Transit App")

            android.util.Log.d("GtfsDownload", "Downloading from: $WINDSOR_GTFS_ZIP_URL")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val fileSize = connection.contentLength
                android.util.Log.d("GtfsDownload", "ZIP file size: $fileSize bytes")

                FileOutputStream(zipFile).use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        var totalBytesRead = 0L
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            // Update progress during download
                            if (fileSize > 0) {
                                val progress = 10 + ((totalBytesRead * 30) / fileSize).toInt()
                                setProgress(workDataOf(
                                    STATUS_KEY to "Downloading... ${totalBytesRead / 1024}KB / ${fileSize / 1024}KB",
                                    PROGRESS_KEY to progress
                                ))
                            }
                        }
                    }
                }

                android.util.Log.d("GtfsDownload", "Successfully downloaded ZIP file: ${zipFile.length()} bytes")
                zipFile.length() > 0
            } else {
                android.util.Log.e("GtfsDownload", "HTTP Error: ${connection.responseCode}")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("GtfsDownload", "Failed to download ZIP file", e)
            false
        }
    }

    private fun extractZipFile(zipFile: File, extractDir: File): Boolean {
        return try {
            var extractedFiles = 0

            ZipInputStream(FileInputStream(zipFile)).use { zipInputStream ->
                var entry = zipInputStream.nextEntry

                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".txt")) {
                        val extractedFile = File(extractDir, entry.name)
                        android.util.Log.d("GtfsDownload", "Extracting: ${entry.name}")

                        FileOutputStream(extractedFile).use { output ->
                            zipInputStream.copyTo(output)
                        }

                        extractedFiles++
                        android.util.Log.d("GtfsDownload", "Extracted ${entry.name}: ${extractedFile.length()} bytes")
                    }

                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }

            android.util.Log.d("GtfsDownload", "Extracted $extractedFiles GTFS files")
            extractedFiles > 0

        } catch (e: Exception) {
            android.util.Log.e("GtfsDownload", "Failed to extract ZIP file", e)
            false
        }
    }

    private fun validateGtfsFiles(extractDir: File): Boolean {
        return try {
            // Check that all required files exist and have reasonable content
            REQUIRED_GTFS_FILES.all { fileName ->
                val file = File(extractDir, fileName)
                val isValid = file.exists() && file.length() > 100 // At least 100 bytes

                if (isValid) {
                    android.util.Log.d("GtfsDownload", "Validated $fileName: ${file.length()} bytes")
                } else {
                    android.util.Log.e("GtfsDownload", "Validation failed for $fileName: exists=${file.exists()}, size=${if (file.exists()) file.length() else 0}")
                }

                isValid
            }
        } catch (e: Exception) {
            android.util.Log.e("GtfsDownload", "Validation failed", e)
            false
        }
    }

    private fun moveFilesToFinalLocation(extractDir: File) {
        val finalDir = applicationContext.filesDir

        // Get all .txt files from extract directory
        extractDir.listFiles { file -> file.name.endsWith(".txt") }?.forEach { file ->
            val finalFile = File(finalDir, file.name)

            try {
                file.copyTo(finalFile, overwrite = true)
                android.util.Log.d("GtfsDownload", "Installed ${file.name}: ${finalFile.length()} bytes")
            } catch (e: Exception) {
                android.util.Log.e("GtfsDownload", "Failed to install ${file.name}", e)
            }
        }

        // Update timestamp after successful file installation
        updateLastUpdateTimestamp()
    }

    private fun updateLastUpdateTimestamp() {
        val sharedPrefs = applicationContext.getSharedPreferences("gtfs_prefs", android.content.Context.MODE_PRIVATE)
        val timestamp = java.text.SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())
        sharedPrefs.edit().putString("last_update_time", timestamp).apply()
        android.util.Log.d("GtfsDownload", "Updated last_update_time to: $timestamp")
        android.util.Log.d("GtfsDownload", "✅ Fresh GTFS data from Windsor Transit is now active!")
    }
}
