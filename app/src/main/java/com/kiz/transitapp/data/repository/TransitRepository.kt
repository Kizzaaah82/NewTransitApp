package com.kiz.transitapp.data.repository

import android.content.Context
import android.util.Log
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.kiz.transitapp.data.api.GTFSRealtimeApi
import com.kiz.transitapp.data.api.GTFSRealtimeClient
import com.kiz.transitapp.data.database.GTFSDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

// The existing data class for static arrival data.
data class OptimizedArrivalTime(
    val routeId: String,
    val arrivalTime: LocalTime,
    val isRealTime: Boolean, // This will always be false for this class now
    val tripId: String? = null
)

// A data class to hold the fully merged result.
data class MergedArrivalTime(
    val routeId: String,
    val tripId: String,
    val scheduledTime: LocalTime, // The original timetable time
    val arrivalTime: LocalTime,   // The predicted real-time or scheduled time
    val isRealTime: Boolean,
    val delaySeconds: Int
)

class TransitRepository(private val context: Context) {
    private val apiService: GTFSRealtimeApi = GTFSRealtimeClient.instance
    private val database = GTFSDatabase.getDatabase(context)

    private var cachedTripUpdates: FeedMessage? = null
    private var lastTripUpdateTime: Long = 0
    private var cachedVehiclePositions: FeedMessage? = null
    private var lastVehicleUpdateTime: Long = 0
    private var cachedServiceAlerts: FeedMessage? = null
    private var lastServiceAlertsUpdateTime: Long = 0

    private val CACHE_DURATION_MS = 5_000 // 5 seconds cache - ensures fresh data on 10-second map polling
    private val ALERTS_CACHE_DURATION_MS = 60_000 // 60 seconds cache - alerts don't change frequently

    // Use correct timezone from agency.txt
    private val windsorTimeZone = ZoneId.of("America/New_York")

    /**
     * Enhanced function to get real-time arrivals with proper formatting for UI display.
     * Returns 1 real-time prediction followed by 2 static schedule times.
     */
    suspend fun getMergedArrivalsForStop(
        stopId: String,
        activeServiceIds: List<String>
    ): List<MergedArrivalTime> = withContext(Dispatchers.IO) {
        // 1. Fetch the base static schedule for the stop for today's active services.
        val staticArrivals = getStaticArrivalsForStop(stopId, activeServiceIds)

        // 2. Fetch the latest real-time trip updates from the cache or API.
        val tripUpdates = getCachedTripUpdates()

        // 3. Process real-time updates into an easy-to-use map for quick lookups.
        val realTimeUpdateMap = mutableMapOf<String, Pair<Long, Int>>() // Key: TripId, Value: Pair(ArrivalTimeUnix, DelaySeconds)
        tripUpdates?.entityList?.forEach { entity ->
            if (entity.hasTripUpdate()) {
                val tripUpdate = entity.tripUpdate
                tripUpdate.stopTimeUpdateList.forEach { stopTimeUpdate ->
                    if (stopTimeUpdate.stopId == stopId && stopTimeUpdate.hasArrival()) {
                        val arrivalInfo = stopTimeUpdate.arrival
                        val tripId = tripUpdate.trip.tripId
                        if (tripId.isNotBlank()) {
                            realTimeUpdateMap[tripId] = Pair(arrivalInfo.time, arrivalInfo.delay)
                        }
                    }
                }
            }
        }

        // 4. Merge the static schedule with the real-time data and organize for proper display
        val now = LocalTime.now(windsorTimeZone)
        val mergedList = mutableListOf<MergedArrivalTime>()
        val realTimeArrivals = mutableListOf<MergedArrivalTime>()
        val staticOnlyArrivals = mutableListOf<MergedArrivalTime>()

        staticArrivals.forEach { staticArrival ->
            val tripId = staticArrival.tripId ?: return@forEach

            if (realTimeUpdateMap.containsKey(tripId)) {
                // This trip has a real-time update. Use it.
                val (realTimeUnix, delaySeconds) = realTimeUpdateMap[tripId]!!
                val realTimeArrival = Instant.ofEpochSecond(realTimeUnix).atZone(windsorTimeZone).toLocalTime()

                // Only include if it's in the future (or very recent)
                if (realTimeArrival.isAfter(now.minusMinutes(1)) ||
                    (realTimeArrival.hour < 4 && now.hour > 20)) {
                    realTimeArrivals.add(
                        MergedArrivalTime(
                            routeId = staticArrival.routeId,
                            tripId = tripId,
                            scheduledTime = staticArrival.arrivalTime,
                            arrivalTime = realTimeArrival,
                            isRealTime = true,
                            delaySeconds = delaySeconds
                        )
                    )
                }
            } else {
                // No real-time update for this trip. Use the static scheduled time.
                if (staticArrival.arrivalTime.isAfter(now) ||
                    (staticArrival.arrivalTime.hour < 4 && now.hour > 20)) {
                    staticOnlyArrivals.add(
                        MergedArrivalTime(
                            routeId = staticArrival.routeId,
                            tripId = tripId,
                            scheduledTime = staticArrival.arrivalTime,
                            arrivalTime = staticArrival.arrivalTime,
                            isRealTime = false,
                            delaySeconds = 0
                        )
                    )
                }
            }
        }

        // 5. Sort and organize: 1 real-time + 2 static times per route
        val groupedByRoute = (realTimeArrivals + staticOnlyArrivals)
            .sortedBy { it.arrivalTime }
            .groupBy { it.routeId }

        groupedByRoute.forEach { (routeId, arrivals) ->
            val realTime = arrivals.filter { it.isRealTime }.take(1)
            val static = arrivals.filter { !it.isRealTime }.take(2)

            // Add 1 real-time if available
            mergedList.addAll(realTime)

            // Fill remaining slots with static times, ensuring we don't exceed 3 total per route
            val remainingSlots = 3 - realTime.size
            mergedList.addAll(static.take(remainingSlots))
        }

        return@withContext mergedList
            .sortedBy { it.arrivalTime }
            .distinctBy { "${it.routeId}_${it.tripId}" }
    }

    // =========================================================================
    // THIS FUNCTION HAS BEEN REWRITTEN FOR SIMPLICITY AND RELIABILITY
    // =========================================================================
    /**
     * Helper function to read GTFS files and get the static, scheduled arrivals for a given stop
     * that are active on the current day. This version is more robust.
     */
    private suspend fun getStaticArrivalsForStop(
        stopId: String,
        activeServiceIds: List<String>
    ): List<OptimizedArrivalTime> = withContext(Dispatchers.IO) {
        if (activeServiceIds.isEmpty()) {
            Log.w("TransitRepository", "getStaticArrivalsForStop called with no active service IDs. No arrivals will be found.")
            return@withContext emptyList()
        }

        try {
            // Step 1: Create a map of only the trips that are active today.
            // Map Key: trip_id, Map Value: route_id
            val activeTripsMap = mutableMapOf<String, String>()
            openGtfsFile(context, "trips.txt").bufferedReader().useLines { tripLines ->
                val iterator = tripLines.iterator()
                if (!iterator.hasNext()) return@useLines
                val header = getHeaderIndexMap(iterator.next())
                val tripIdIdx = header["trip_id"]!!
                val routeIdIdx = header["route_id"]!!
                val serviceIdIdx = header["service_id"]!!

                iterator.forEach { line ->
                    val tokens = line.split(",")
                    if (tokens.size > maxOf(tripIdIdx, routeIdIdx, serviceIdIdx)) {
                        val serviceId = tokens[serviceIdIdx].trim('"')
                        if (activeServiceIds.contains(serviceId)) {
                            val tripId = tokens[tripIdIdx].trim('"')
                            val routeId = tokens[routeIdIdx].trim('"')
                            activeTripsMap[tripId] = routeId
                        }
                    }
                }
            }

            // Step 2: Now, read stop_times and only consider trips that are in our active map.
            val arrivals = mutableListOf<OptimizedArrivalTime>()
            openGtfsFile(context, "stop_times.txt").bufferedReader().useLines { stopTimeLines ->
                val iterator = stopTimeLines.iterator()
                if (!iterator.hasNext()) return@useLines
                val header = getHeaderIndexMap(iterator.next())
                val tripIdIdx = header["trip_id"]!!
                val arrivalTimeIdx = header["arrival_time"]!!
                val stopIdIdx = header["stop_id"]!!

                iterator.forEach { line ->
                    val tokens = line.split(",")
                    if (tokens.size > maxOf(tripIdIdx, arrivalTimeIdx, stopIdIdx)) {
                        val currentStopId = tokens[stopIdIdx].trim('"')
                        if (currentStopId == stopId) {
                            val tripId = tokens[tripIdIdx].trim('"')
                            // Check if this trip is active today
                            val routeId = activeTripsMap[tripId]
                            if (routeId != null) {
                                try {
                                    val arrivalTime = parseGtfsTime(tokens[arrivalTimeIdx].trim('"'))
                                    arrivals.add(
                                        OptimizedArrivalTime(
                                            routeId = routeId,
                                            arrivalTime = arrivalTime,
                                            isRealTime = false,
                                            tripId = tripId
                                        )
                                    )
                                } catch (e: Exception) {
                                    // Ignore malformed time
                                }
                            }
                        }
                    }
                }
            }
            return@withContext arrivals
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error getting static arrivals for stop $stopId", e)
            return@withContext emptyList()
        }
    }

    private fun openGtfsFile(context: Context, fileName: String): java.io.InputStream {
        val internalFile = java.io.File(context.filesDir, fileName)
        return if (internalFile.exists() && internalFile.length() > 0) {
            java.io.FileInputStream(internalFile)
        } else {
            context.assets.open(fileName)
        }
    }

    private fun getHeaderIndexMap(headerLine: String): Map<String, Int> {
        return headerLine.split(",").mapIndexed { index, name ->
            name.trim().replace("\"", "") to index
        }.toMap()
    }

    private fun parseGtfsTime(timeString: String): LocalTime {
        val parts = timeString.split(":")
        var hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val second = if (parts.size > 2) parts[2].toInt() else 0

        if (hour >= 24) {
            hour -= 24
        }
        return LocalTime.of(hour, minute, second)
    }

    suspend fun getTripUpdates(): FeedMessage {
        return getCachedTripUpdates() ?: throw Exception("Failed to fetch trip updates")
    }

    private suspend fun getCachedTripUpdates(): FeedMessage? {
        val currentTime = System.currentTimeMillis()
        if (cachedTripUpdates != null && (currentTime - lastTripUpdateTime) < CACHE_DURATION_MS) {
            return cachedTripUpdates
        }
        return try {
            val response = apiService.getTripUpdates()
            if (response.isSuccessful && response.body() != null) {
                cachedTripUpdates = FeedMessage.parseFrom(response.body()!!.byteStream())
                lastTripUpdateTime = currentTime
                cachedTripUpdates
            } else {
                Log.w("TransitRepository", "Failed to fetch trip updates: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error fetching trip updates", e)
            null
        }
    }

    suspend fun getVehiclePositions(): FeedMessage {
        val currentTime = System.currentTimeMillis()
        if (cachedVehiclePositions != null && (currentTime - lastVehicleUpdateTime) < CACHE_DURATION_MS) {
            return cachedVehiclePositions!!
        }
        val response = apiService.getVehiclePositions()
        if (response.isSuccessful && response.body() != null) {
            cachedVehiclePositions = FeedMessage.parseFrom(response.body()!!.byteStream())
            lastVehicleUpdateTime = currentTime
            return cachedVehiclePositions!!
        } else {
            throw Exception("Failed to fetch vehicle positions: ${response.code()} ${response.message()}")
        }
    }

    /**
     * Fetch GTFS-Realtime Service Alerts from API
     * These contain official transit agency alerts about detours, delays, stop closures, etc.
     */
    suspend fun getServiceAlerts(): FeedMessage {
        val currentTime = System.currentTimeMillis()
        if (cachedServiceAlerts != null && (currentTime - lastServiceAlertsUpdateTime) < ALERTS_CACHE_DURATION_MS) {
            return cachedServiceAlerts!!
        }
        return try {
            val response = apiService.getServiceAlerts()
            if (response.isSuccessful && response.body() != null) {
                cachedServiceAlerts = FeedMessage.parseFrom(response.body()!!.byteStream())
                lastServiceAlertsUpdateTime = currentTime
                cachedServiceAlerts!!
            } else {
                Log.w("TransitRepository", "Failed to fetch service alerts: ${response.code()}")
                cachedServiceAlerts ?: throw Exception("Failed to fetch service alerts: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error fetching service alerts", e)
            // Return cached version if available, otherwise throw
            cachedServiceAlerts ?: throw e
        }
    }


    /**
     * This function is intended for the full timetable view. It requires the active service IDs
     * to be passed in from the ViewModel.
     */
    suspend fun getAllStaticArrivalsForStop(
        stopId: String,
        activeServiceIds: List<String>
    ): List<OptimizedArrivalTime> {
        return getStaticArrivalsForStop(stopId, activeServiceIds)
    }
}