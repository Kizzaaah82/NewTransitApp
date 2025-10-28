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
    val delaySeconds: Int,
    val isFeedFresh: Boolean = true // Whether the real-time feed is fresh
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
    private val FEED_STALENESS_THRESHOLD_MS = 5 * 60 * 1000 // 5 minutes - reject feeds older than this

    // Use correct timezone from agency.txt
    private val windsorTimeZone = ZoneId.of("America/New_York")

    /**
     * Check if a GTFS-RT feed is stale based on its header timestamp
     */
    private fun isFeedStale(feed: FeedMessage): Boolean {
        if (!feed.hasHeader() || !feed.header.hasTimestamp()) {
            Log.w("TransitRepository", "Feed missing header or timestamp - treating as potentially stale")
            return false // Don't reject feeds without timestamps, but log warning
        }

        val feedTimestamp = feed.header.timestamp * 1000 // Convert from seconds to milliseconds
        val currentTime = System.currentTimeMillis()
        val age = currentTime - feedTimestamp

        if (age > FEED_STALENESS_THRESHOLD_MS) {
            Log.w("TransitRepository", "Feed is stale: ${age / 1000} seconds old (threshold: ${FEED_STALENESS_THRESHOLD_MS / 1000}s)")
            return true
        }

        return false
    }

    /**
     * Check if a GTFS-RT feed is fresh enough to show LIVE badges (< 180 seconds old)
     */
    private fun isFeedFresh(feed: FeedMessage?): Boolean {
        if (feed == null || !feed.hasHeader() || !feed.header.hasTimestamp()) {
            return false
        }

        val feedTimestamp = feed.header.timestamp * 1000 // Convert from seconds to milliseconds
        val currentTime = System.currentTimeMillis()
        val age = currentTime - feedTimestamp

        // Fresh if less than 180 seconds (3 minutes) old
        return age < 180_000
    }

    /**
     * Enhanced function to get real-time arrivals with proper formatting for UI display.
     * Returns 1 real-time prediction followed by 2 static schedule times per route (using short names).
     */
    suspend fun getMergedArrivalsForStop(
        stopId: String,
        activeServiceIds: List<String>,
        routeIdToShortName: Map<String, String> = emptyMap()
    ): List<MergedArrivalTime> = withContext(Dispatchers.IO) {
        // 1. Fetch the base static schedule for the stop for today's active services.
        val staticArrivals = getStaticArrivalsForStop(stopId, activeServiceIds)

        // 2. Fetch the latest real-time trip updates from the cache or API.
        val tripUpdates = getCachedTripUpdates()

        // 2a. Check if the feed is fresh (< 180 seconds old)
        val feedFresh = isFeedFresh(tripUpdates)

        // 3. Process real-time updates into an easy-to-use map for quick lookups.
        val realTimeUpdateMap = mutableMapOf<String, Pair<Long, Int>>() // Key: TripId, Value: Pair(ArrivalTimeUnix, DelaySeconds)
        tripUpdates?.entityList?.forEach { entity ->
            if (entity.hasTripUpdate()) {
                val tripUpdate = entity.tripUpdate
                tripUpdate.stopTimeUpdateList.forEach { stopTimeUpdate ->
                    if (stopTimeUpdate.stopId == stopId) {
                        val tripId = tripUpdate.trip.tripId
                        if (tripId.isNotBlank()) {
                            // Accept EITHER arrival OR departure (or both)
                            val hasArrival = stopTimeUpdate.hasArrival()
                            val hasDeparture = stopTimeUpdate.hasDeparture()

                            if (hasArrival || hasDeparture) {
                                // Prefer arrival, fall back to departure
                                val timeInfo = when {
                                    hasArrival && hasDeparture -> {
                                        // Both present: use arrival time, but take max delay
                                        val arrivalDelay = stopTimeUpdate.arrival.delay
                                        val departureDelay = stopTimeUpdate.departure.delay
                                        val maxDelay = maxOf(arrivalDelay, departureDelay)
                                        Pair(stopTimeUpdate.arrival.time, maxDelay)
                                    }
                                    hasArrival -> {
                                        Pair(stopTimeUpdate.arrival.time, stopTimeUpdate.arrival.delay)
                                    }
                                    else -> { // hasDeparture
                                        Pair(stopTimeUpdate.departure.time, stopTimeUpdate.departure.delay)
                                    }
                                }
                                realTimeUpdateMap[tripId] = timeInfo
                            }
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
                            delaySeconds = delaySeconds,
                            isFeedFresh = feedFresh
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
                            delaySeconds = 0,
                            isFeedFresh = true // Static arrivals are always "fresh"
                        )
                    )
                }
            }
        }

        // 5. Sort and organize: 1 real-time + 2 static times per route (using SHORT NAMES)
        // IMPORTANT: realTimeArrivals and staticOnlyArrivals should NEVER have the same tripId
        // because we separate them in step 4. But we still need to organize by route.

        // Convert route IDs to short names and group by short name
        val allArrivalsWithShortNames = (realTimeArrivals + staticOnlyArrivals).map { arrival ->
            val shortName = routeIdToShortName[arrival.routeId] ?: arrival.routeId
            arrival to shortName
        }

        val groupedByShortName = allArrivalsWithShortNames.groupBy { it.second }

        groupedByShortName.forEach { (shortName, arrivalsWithNames) ->
            val arrivals = arrivalsWithNames.map { it.first }

            // Separate real-time and static, then SORT BY TIME before taking
            val realTime = arrivals
                .filter { it.isRealTime }
                .sortedBy { it.arrivalTime }
                .take(1)

            val static = arrivals
                .filter { !it.isRealTime }
                .sortedBy { it.arrivalTime }
                .take(2)

            // DEBUG: Log what we're adding for this route
            Log.d("TransitRepository", "Route $shortName - RT count: ${realTime.size}, Static count: ${static.size}")
            realTime.forEach { rt ->
                Log.d("TransitRepository", "  RT: Trip ${rt.tripId} @ ${rt.arrivalTime} (delay: ${rt.delaySeconds}s, scheduled: ${rt.scheduledTime})")
            }
            static.forEach { st ->
                Log.d("TransitRepository", "  Static: Trip ${st.tripId} @ ${st.arrivalTime}")
            }

            // Check for same arrival time but different trips (this is valid!)
            if (realTime.isNotEmpty()) {
                val rtTime = realTime[0].arrivalTime
                val duplicateTimeStatic = static.filter { it.arrivalTime == rtTime }
                if (duplicateTimeStatic.isNotEmpty()) {
                    Log.w("TransitRepository", "Route $shortName: Real-time at $rtTime (trip ${realTime[0].tripId}) and ${duplicateTimeStatic.size} static arrival(s) also at $rtTime (trips: ${duplicateTimeStatic.map { it.tripId }})")
                }
            }

            // Add in specific order: 1 real-time FIRST, then 2 static
            // Do NOT sort after this - preserve the RT-first ordering!
            mergedList.addAll(realTime)
            mergedList.addAll(static)
        }

        // Final deduplication as safety net - should not be needed if logic above is correct
        val result = mergedList.distinctBy { "${it.routeId}_${it.tripId}" }
        Log.d("TransitRepository", "getMergedArrivalsForStop: Returning ${result.size} arrivals (from ${mergedList.size} before dedup)")
        return@withContext result
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
            // Check if cached feed is stale
            if (isFeedStale(cachedTripUpdates!!)) {
                Log.w("TransitRepository", "Cached trip updates feed is stale, fetching fresh data")
                cachedTripUpdates = null // Force refresh
            } else {
                return cachedTripUpdates
            }
        }
        return try {
            val response = apiService.getTripUpdates()
            if (response.isSuccessful && response.body() != null) {
                val feed = FeedMessage.parseFrom(response.body()!!.byteStream())

                // Check staleness before accepting new feed
                if (isFeedStale(feed)) {
                    Log.w("TransitRepository", "Received stale trip updates feed from API")
                    // Still cache it but log the warning - better than nothing
                }

                cachedTripUpdates = feed
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
            // Check if cached feed is stale
            if (!isFeedStale(cachedVehiclePositions!!)) {
                return cachedVehiclePositions!!
            }
            Log.w("TransitRepository", "Cached vehicle positions feed is stale, fetching fresh data")
        }
        val response = apiService.getVehiclePositions()
        if (response.isSuccessful && response.body() != null) {
            val feed = FeedMessage.parseFrom(response.body()!!.byteStream())

            // Check staleness before accepting new feed
            if (isFeedStale(feed)) {
                Log.w("TransitRepository", "Received stale vehicle positions feed from API")
            }

            cachedVehiclePositions = feed
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
            // Check if cached feed is stale
            if (isFeedStale(cachedServiceAlerts!!)) {
                Log.w("TransitRepository", "Cached service alerts feed is stale, fetching fresh data")
                cachedServiceAlerts = null // Force refresh
            } else {
                return cachedServiceAlerts!!
            }
        }
        return try {
            val response = apiService.getServiceAlerts()
            if (response.isSuccessful && response.body() != null) {
                val feed = FeedMessage.parseFrom(response.body()!!.byteStream())

                // Check staleness before accepting new feed
                if (isFeedStale(feed)) {
                    Log.w("TransitRepository", "Received stale service alerts feed from API")
                    // Still use it but log the warning
                }

                cachedServiceAlerts = feed
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