package com.kiz.transitapp.workers

import android.content.Context
import androidx.work.*
import com.kiz.transitapp.data.database.GTFSDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import java.io.FileInputStream

class DatabasePreloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("DatabasePreloadWorker", "Starting database preload...")

            val database = GTFSDatabase.getDatabase(applicationContext)

            // FIXED: Always force preload for now to ensure we get fresh data
            Log.d("DatabasePreloadWorker", "Force preloading to ensure fresh data...")

            // Preload essential data from GTFS files into database
            preloadStopTimes(database)
            preloadTrips(database)
            preloadRoutes(database)
            preloadStops(database)

            Log.d("DatabasePreloadWorker", "Database preload completed successfully")
            Result.success()

        } catch (e: Exception) {
            Log.e("DatabasePreloadWorker", "Error during database preload", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun preloadStopTimes(database: GTFSDatabase) {
        try {
            Log.d("DatabasePreloadWorker", "Starting stop times preload...")

            // Clear any existing data first
            database.stopTimeDao().deleteAll()

            // CRITICAL FIX: Force a database checkpoint before starting to ensure clean state
            try {
                database.query("PRAGMA wal_checkpoint(TRUNCATE)", null)
                Log.d("DatabasePreloadWorker", "Pre-insert WAL checkpoint completed")
            } catch (e: Exception) {
                Log.w("DatabasePreloadWorker", "Could not do pre-insert checkpoint: ${e.message}")
            }

            // --- RAW BULK INSERT FOR MAXIMUM SPEED ---
            val db = database.openHelper.writableDatabase
            val insertSql = "INSERT INTO stop_times (trip_id, arrival_time, departure_time, stop_id, stop_sequence) VALUES (?, ?, ?, ?, ?)"
            db.beginTransaction()
            try {
                openGtfsFile(applicationContext, "stop_times.txt").bufferedReader().useLines { lines ->
                    val iterator = lines.iterator()
                    if (!iterator.hasNext()) {
                        Log.w("DatabasePreloadWorker", "stop_times.txt appears to be empty")
                        return
                    }
                    val header = getHeaderIndexMap(iterator.next())
                    val tripIdIdx = header["trip_id"] ?: run {
                        Log.e("DatabasePreloadWorker", "trip_id column not found in stop_times.txt")
                        return
                    }
                    val arrivalTimeIdx = header["arrival_time"] ?: run {
                        Log.e("DatabasePreloadWorker", "arrival_time column not found in stop_times.txt")
                        return
                    }
                    val departureTimeIdx = header["departure_time"] ?: run {
                        Log.e("DatabasePreloadWorker", "departure_time column not found in stop_times.txt")
                        return
                    }
                    val stopIdIdx = header["stop_id"] ?: run {
                        Log.e("DatabasePreloadWorker", "stop_id column not found in stop_times.txt")
                        return
                    }
                    val stopSequenceIdx = header["stop_sequence"] ?: run {
                        Log.e("DatabasePreloadWorker", "stop_sequence column not found in stop_times.txt")
                        return
                    }
                    var totalCount = 0
                    while (iterator.hasNext()) {
                        val tokens = iterator.next().split(",")
                        if (tokens.size > maxOf(tripIdIdx, arrivalTimeIdx, departureTimeIdx, stopIdIdx, stopSequenceIdx)) {
                            db.execSQL(
                                insertSql,
                                arrayOf(
                                    tokens[tripIdIdx].trim('"'),
                                    tokens[arrivalTimeIdx].trim('"'),
                                    tokens[departureTimeIdx].trim('"'),
                                    tokens[stopIdIdx].trim('"'),
                                    tokens[stopSequenceIdx].toIntOrNull() ?: 0
                                )
                            )
                            totalCount++
                            if (totalCount % 10000 == 0) {
                                Log.d("DatabasePreloadWorker", "Inserted batch, total so far: $totalCount")
                            }
                        }
                    }
                    Log.d("DatabasePreloadWorker", "Preloaded $totalCount stop times (raw SQL)")
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            // --- END RAW BULK INSERT ---

            // CRITICAL FIX: Force multiple checkpoints to ensure data is visible
            try {
                database.query("PRAGMA wal_checkpoint(TRUNCATE)", null)
                database.query("PRAGMA wal_checkpoint(FULL)", null)
                Log.d("DatabasePreloadWorker", "Post-insert WAL checkpoints completed")
            } catch (e: Exception) {
                Log.w("DatabasePreloadWorker", "Could not checkpoint WAL, but continuing: ${e.message}")
            }

            // VERIFICATION: Test if we can actually read the data we just inserted
            try {
                val testCount = database.stopTimeDao().getArrivalCountForStop("any_test_stop")
                Log.d("DatabasePreloadWorker", "Post-insert verification: Can query database (result: $testCount)")

                // Try to find some actual stop times to verify data is accessible
                val sampleStopTimes = database.stopTimeDao().getStopTimesByStop("124226")
                Log.d("DatabasePreloadWorker", "Sample verification: Stop 124226 has ${sampleStopTimes.size} stop times")

                if (sampleStopTimes.isNotEmpty()) {
                    Log.d("DatabasePreloadWorker", "Sample stop time: trip=${sampleStopTimes[0].trip_id}, arrival=${sampleStopTimes[0].arrival_time}")
                }
            } catch (e: Exception) {
                Log.e("DatabasePreloadWorker", "CRITICAL: Cannot read data we just inserted!", e)
            }
        } // End of verification try/catch
        catch (e: Exception) {
            Log.e("DatabasePreloadWorker", "Error preloading stop times", e)
            throw e // Re-throw to ensure the worker knows it failed
        }
    } // End of preloadStopTimes

    private suspend fun preloadTrips(database: GTFSDatabase) {
        try {
            // Clear any existing data first
            database.tripDao().deleteAll()

            val trips = mutableListOf<com.kiz.transitapp.data.database.entities.Trip>()
            var totalCount = 0

            openGtfsFile(applicationContext, "trips.txt").bufferedReader().useLines { lines ->
                val iterator = lines.iterator()
                if (!iterator.hasNext()) return

                val header = getHeaderIndexMap(iterator.next())
                val tripIdIdx = header["trip_id"] ?: return
                val routeIdIdx = header["route_id"] ?: return
                val serviceIdIdx = header["service_id"] ?: return

                while (iterator.hasNext()) {
                    val tokens = iterator.next().split(",")
                    if (tokens.size > maxOf(tripIdIdx, routeIdIdx, serviceIdIdx)) {
                        trips.add(
                            com.kiz.transitapp.data.database.entities.Trip(
                                trip_id = tokens[tripIdIdx].trim('"'),
                                route_id = tokens[routeIdIdx].trim('"'),
                                service_id = tokens[serviceIdIdx].trim('"')
                            )
                        )
                        totalCount++

                        if (trips.size >= 1000) {
                            database.tripDao().insertAll(trips)
                            trips.clear()
                        }
                    }
                }

                if (trips.isNotEmpty()) {
                    database.tripDao().insertAll(trips)
                }
            }

            // Force WAL checkpoint for trips too
            try {
                val db = database.openHelper.writableDatabase
                db.execSQL("PRAGMA wal_checkpoint(FULL)")
            } catch (e: Exception) {
                Log.w("DatabasePreloadWorker", "Could not checkpoint WAL for trips", e)
            }

            Log.d("DatabasePreloadWorker", "Preloaded $totalCount trips")

        } catch (e: Exception) {
            Log.e("DatabasePreloadWorker", "Error preloading trips", e)
        }
    }

    private fun preloadRoutes(database: GTFSDatabase) {
        // Routes preloading can be implemented later if needed
        // For now, just log completion to avoid unused parameter warning
        Log.d("DatabasePreloadWorker", "Routes preload completed (placeholder)")
    }

    private suspend fun preloadStops(database: GTFSDatabase) {
        try {
            Log.d("DatabasePreloadWorker", "Starting stops preload...")

            // Clear any existing data first
            database.stopDao().deleteAll()

            val stops = mutableListOf<com.kiz.transitapp.data.database.entities.Stop>()
            var totalCount = 0

            openGtfsFile(applicationContext, "stops.txt").bufferedReader().useLines { lines ->
                val iterator = lines.iterator()
                if (!iterator.hasNext()) {
                    Log.w("DatabasePreloadWorker", "stops.txt appears to be empty")
                    return
                }

                val header = getHeaderIndexMap(iterator.next())
                val stopIdIdx = header["stop_id"] ?: return
                val stopCodeIdx = header["stop_code"]
                val stopNameIdx = header["stop_name"] ?: return
                val stopDescIdx = header["stop_desc"]
                val stopLatIdx = header["stop_lat"] ?: return
                val stopLonIdx = header["stop_lon"] ?: return
                val zoneIdIdx = header["zone_id"]
                val stopUrlIdx = header["stop_url"]
                val locationTypeIdx = header["location_type"]
                val parentStationIdx = header["parent_station"]
                val stopTimezoneIdx = header["stop_timezone"]
                val wheelchairBoardingIdx = header["wheelchair_boarding"]
                val platformCodeIdx = header["platform_code"]

                while (iterator.hasNext()) {
                    val tokens = iterator.next().split(",")
                    if (tokens.size > stopIdIdx) {
                        stops.add(
                            com.kiz.transitapp.data.database.entities.Stop(
                                stop_id = tokens[stopIdIdx].trim('"'),
                                stop_code = stopCodeIdx?.let { tokens.getOrNull(it)?.trim('"') },
                                stop_name = tokens[stopNameIdx].trim('"'),
                                stop_desc = stopDescIdx?.let { tokens.getOrNull(it)?.trim('"') },
                                stop_lat = tokens[stopLatIdx].toDoubleOrNull() ?: 0.0,
                                stop_lon = tokens[stopLonIdx].toDoubleOrNull() ?: 0.0,
                                zone_id = zoneIdIdx?.let { tokens.getOrNull(it)?.trim('"') },
                                stop_url = stopUrlIdx?.let { tokens.getOrNull(it)?.trim('"') },
                                location_type = locationTypeIdx?.let { tokens.getOrNull(it)?.toIntOrNull() },
                                parent_station = parentStationIdx?.let { tokens.getOrNull(it)?.trim('"') },
                                stop_timezone = stopTimezoneIdx?.let { tokens.getOrNull(it)?.trim('"') },
                                wheelchair_boarding = wheelchairBoardingIdx?.let { tokens.getOrNull(it)?.toIntOrNull() },
                                platform_code = platformCodeIdx?.let { tokens.getOrNull(it)?.trim('"') }
                            )
                        )
                        totalCount++

                        if (stops.size >= 1000) {
                            database.stopDao().insertAll(stops)
                            stops.clear()
                        }
                    }
                }

                if (stops.isNotEmpty()) {
                    database.stopDao().insertAll(stops)
                }
            }

            Log.d("DatabasePreloadWorker", "Preloaded $totalCount stops")

        } catch (e: Exception) {
            Log.e("DatabasePreloadWorker", "Error preloading stops", e)
        }
    }

    private fun openGtfsFile(context: Context, fileName: String): java.io.InputStream {
        val internalFile = File(context.filesDir, fileName)
        return if (internalFile.exists() && internalFile.length() > 0) {
            FileInputStream(internalFile)
        } else {
            context.assets.open(fileName)
        }
    }

    private fun getHeaderIndexMap(headerLine: String): Map<String, Int> {
        return headerLine.split(",").mapIndexed { index, name ->
            name.trim().replace("\"", "") to index
        }.toMap()
    }

    companion object {
        fun schedulePreload(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<DatabasePreloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "database_preload",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
