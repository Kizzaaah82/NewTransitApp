package com.kiz.transitapp.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.graphics.toColorInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.transit.realtime.GtfsRealtime
import com.kiz.transitapp.data.repository.TransitRepository
import com.kiz.transitapp.data.weather.WeatherData
import com.kiz.transitapp.data.weather.WeatherRepository
import com.kiz.transitapp.ui.utils.IconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

// Data class for the UI. It now contains all the necessary fields.
data class StopArrivalTime(
    val routeId: String,
    val arrivalTime: LocalTime,
    val isRealTime: Boolean,
    val delaySeconds: Int = 0, // Delay in seconds (positive = late, negative = early)
    val scheduledTime: LocalTime? = null, // Original scheduled time for real-time arrivals
    val isFeedFresh: Boolean = true // Whether the real-time feed is fresh (<180s old)
)

data class TimetableEntry(
    val routeId: String,
    val arrivalTime: String
)

data class VehiclePositionInfo(
    val vehicleId: String,
    val latitude: Float,
    val longitude: Float,
    val routeId: String,
    // New optional realtime attributes
    val bearing: Float? = null,
    val speedMps: Float? = null,
    val timestamp: Long? = null,
    val label: String? = null,
    val occupancyStatus: String? = null,
    val headsign: String? = null
)


// Service Alert from GTFS-Realtime (official transit agency alerts)
// Service Alert from GTFS-Realtime (official transit agency alerts)
data class ServiceAlert(
    val alertId: String,
    val affectedRoutes: List<String>, // Route SHORT NAMES (not route_id!) - normalized during parsing for UI consistency
    val headerText: String,
    val descriptionText: String,
    val alertType: AlertType,
    val severity: AlertSeverity,
    val activePeriodStart: Long? = null,
    val activePeriodEnd: Long? = null,
    val activePeriods: List<Pair<Long?, Long?>> = listOf(Pair(activePeriodStart, activePeriodEnd)), // All active time windows
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class AlertType {
    NO_SERVICE,
    REDUCED_SERVICE,
    SIGNIFICANT_DELAYS,
    DETOUR,
    ADDITIONAL_SERVICE,
    MODIFIED_SERVICE,
    STOP_MOVED,
    STOP_CLOSED,
    OTHER_EFFECT,
    UNKNOWN_EFFECT,
    // Legacy types for backwards compatibility
    DELAY,
    SERVICE_CHANGE,
    OTHER
}

enum class AlertSeverity {
    UNKNOWN,
    INFO,
    WARNING,
    SEVERE
}

// Operational warnings derived from TripUpdates (not formal alerts)
data class OperationalWarning(
    val routeId: String,
    val warningType: OperationalWarningType,
    val delayMinutes: Int,
    val affectedTrips: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class OperationalWarningType {
    SIGNIFICANT_DELAYS,  // Multiple trips running >10 min late
    MODERATE_DELAYS,     // Multiple trips running 5-10 min late
    TRIP_CANCELLATION    // Trip cancelled (SKIPPED schedule relationship)
}

data class FavoriteStopWithArrivals(
    val stopId: String,
    val routeId: String,
    val arrivals: List<StopArrivalTime>
)

// Add new data class for storing favorite stop-route pairs
data class FavoriteStopRoute(
    val stopId: String,
    val routeId: String
)

// Transit data structures
data class BusStop(
    val id: String,
    val code: String?, // Add stop code field
    val name: String,
    val location: LatLng
)

// New optimized data structure with precomputed values
data class OptimizedTransitData(
    val routes: List<Route>,
    val stops: List<BusStop>,
    val stopsForRoute: Map<String, List<BusStop>>, // route short name -> stops
    val routeIdToShortName: Map<String, String>, // GTFS route_id -> short name
    val routeShortNameToColor: Map<String, String>,
    val routeShortNameToComputedColor: Map<String, androidx.compose.ui.graphics.Color>, // Precomputed Color objects
    val stopIdToBusStop: Map<String, BusStop>,
    val routeShortNameToTripIds: Map<String, List<String>>,
    val tripIdToOrderedStops: Map<String, List<String>>,
    val routePolylines: Map<String, List<LatLng>>, // route short name -> polyline points
    val routePolylinesOptimized: Map<String, List<LatLng>>, // Precomputed with route short names
    val routePolylinesAllVariations: Map<String, List<List<LatLng>>> // NEW: All shape variations per route
)

data class TransitData(
    val routes: List<Route>,
    val stops: List<BusStop>,
    val stopsForRoute: Map<String, List<BusStop>>, // route short name -> stops
    val routeIdToShortName: Map<String, String>, // GTFS route_id -> short name
    val routeIdToRoute: Map<String, Route>, // GTFS route_id -> Route object
    val routeShortNameToColor: Map<String, String>,
    val stopIdToBusStop: Map<String, BusStop>,
    val routeShortNameToTripIds: Map<String, List<String>>,
    val tripIdToOrderedStops: Map<String, List<String>>,
    val routePolylines: Map<String, List<LatLng>>, // route short name -> polyline points
    val routePolylinesAllVariations: Map<String, List<List<LatLng>>> // NEW: All shape variations per route
)

data class Route(
    val id: String,
    val shortName: String,
    val longName: String,
    val color: String,
    val textColor: String
)

class TransitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TransitRepository(application)
    private val weatherRepository = WeatherRepository()

    private val _transitData = MutableStateFlow<TransitData?>(null)
    val transitData: StateFlow<TransitData?> = _transitData

    // Add optimized transit data with precomputed values
    private val _optimizedTransitData = MutableStateFlow<OptimizedTransitData?>(null)
    val optimizedTransitData: StateFlow<OptimizedTransitData?> = _optimizedTransitData

    // Add loading states
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress

    private val _tripIdToRouteId = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _tripIdToServiceId = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _tripIdToHeadsign = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _activeServiceIdsToday = MutableStateFlow<Set<String>>(emptySet())

    private val _isStaticDataReady = MutableStateFlow(false)
    val isStaticDataReady: StateFlow<Boolean> = _isStaticDataReady

    private val _isTimetableLoading = MutableStateFlow(false)
    val isTimetableLoading: StateFlow<Boolean> = _isTimetableLoading

    private val _isArrivalsLoading = MutableStateFlow(false)

    private val _tripUpdates = MutableStateFlow<GtfsRealtime.FeedMessage?>(null)
    private val _vehiclePositions = MutableStateFlow<GtfsRealtime.FeedMessage?>(null)
    private val _vehiclePositionFlow = MutableStateFlow<List<VehiclePositionInfo>>(emptyList())
    val vehiclePositionFlow: StateFlow<List<VehiclePositionInfo>> = _vehiclePositionFlow

    // Service alerts from GTFS-Realtime API (official transit agency alerts)
    private val _serviceAlerts = MutableStateFlow<List<ServiceAlert>>(emptyList())
    val serviceAlerts: StateFlow<List<ServiceAlert>> = _serviceAlerts

    // Operational warnings derived from TripUpdates (delays, cancellations)
    private val _operationalWarnings = MutableStateFlow<List<OperationalWarning>>(emptyList())
    val operationalWarnings: StateFlow<List<OperationalWarning>> = _operationalWarnings

    private val _stopArrivalTimes = MutableStateFlow<List<StopArrivalTime>>(emptyList())
    val stopArrivalTimes: StateFlow<List<StopArrivalTime>> = _stopArrivalTimes

    // Add automatic refresh mechanism for real-time updates
    private val _isAutoRefreshEnabled = MutableStateFlow(true)
    val isAutoRefreshEnabled: StateFlow<Boolean> = _isAutoRefreshEnabled

    private var currentStopId: String? = null
    private val isRefreshing = AtomicBoolean(false)

    // Create a flow that emits every minute to update countdown timers
    private val minuteTickFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000) // Update every minute
        }
    }

    private val _savedCameraPosition = MutableStateFlow<CameraPosition?>(null)
    val savedCameraPosition: StateFlow<CameraPosition?> = _savedCameraPosition
    private val _savedSelectedRoute = MutableStateFlow<String?>(null)
    val savedSelectedRoute: StateFlow<String?> = _savedSelectedRoute
    private val _savedShowLiveBuses = MutableStateFlow(false)
    val savedShowLiveBuses: StateFlow<Boolean> = _savedShowLiveBuses

    // Update favorites to store stop-route pairs instead of just stop IDs
    private val _favoriteStopRoutes = MutableStateFlow<Set<FavoriteStopRoute>>(emptySet())
    val favoriteStopRoutes: StateFlow<Set<FavoriteStopRoute>> = _favoriteStopRoutes

    // Keep the old favoriteStops for backward compatibility with existing UI
    private val _favoriteStops = MutableStateFlow<Set<String>>(emptySet())
    val favoriteStops: StateFlow<Set<String>> = _favoriteStops

    private val _favoriteStopsWithArrivals = MutableStateFlow<List<FavoriteStopWithArrivals>>(emptyList())
    val favoriteStopsWithArrivals: StateFlow<List<FavoriteStopWithArrivals>> = _favoriteStopsWithArrivals

    private val _timetable = MutableStateFlow<List<TimetableEntry>>(emptyList())
    val timetable: StateFlow<List<TimetableEntry>> = _timetable

    // Add state for selected timetable date
    private val _selectedTimetableDate = MutableStateFlow(LocalDate.now())
    val selectedTimetableDate: StateFlow<LocalDate> = _selectedTimetableDate

    private val _weatherData = MutableStateFlow<WeatherData?>(null)
    val weatherData: StateFlow<WeatherData?> = _weatherData
    private val _isWeatherLoading = MutableStateFlow(false)
    val isWeatherLoading: StateFlow<Boolean> = _isWeatherLoading

    // Track the last hour when services were updated
    private val _lastServiceUpdateHour = MutableStateFlow(-1)

    // Add function to refresh active services for midnight crossing
    private suspend fun refreshActiveServices() {
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val activeServices = mutableSetOf<String>()
            val today = LocalDate.now()
            val todayDayOfWeek = today.dayOfWeek
            val todayDateFormatted = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            // MIDNIGHT FIX: Also check yesterday's services for late night routes
            val yesterday = today.minusDays(1)
            val yesterdayDayOfWeek = yesterday.dayOfWeek
            val yesterdayDateFormatted = yesterday.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val isAfterMidnight = LocalTime.now().hour < 6 // Consider 12am-6am as "late night"

            try {
                openGtfsFile(context, "calendar.txt").bufferedReader().use { reader ->
                    val lines = reader.readLines()
                    if (lines.isEmpty()) return@use

                    val header = getHeaderIndexMap(lines[0])
                    val serviceIdIdx = header["service_id"] ?: return@use
                    val mondayIdx = header["monday"] ?: return@use
                    val tuesdayIdx = header["tuesday"] ?: return@use
                    val wednesdayIdx = header["wednesday"] ?: return@use
                    val thursdayIdx = header["thursday"] ?: return@use
                    val fridayIdx = header["friday"] ?: return@use
                    val saturdayIdx = header["saturday"] ?: return@use
                    val sundayIdx = header["sunday"] ?: return@use
                    val startDateIdx = header["start_date"] ?: return@use
                    val endDateIdx = header["end_date"] ?: return@use

                    val todayDayIndex = when (todayDayOfWeek) {
                        DayOfWeek.MONDAY -> mondayIdx
                        DayOfWeek.TUESDAY -> tuesdayIdx
                        DayOfWeek.WEDNESDAY -> wednesdayIdx
                        DayOfWeek.THURSDAY -> thursdayIdx
                        DayOfWeek.FRIDAY -> fridayIdx
                        DayOfWeek.SATURDAY -> saturdayIdx
                        DayOfWeek.SUNDAY -> sundayIdx
                    }

                    val yesterdayDayIndex = when (yesterdayDayOfWeek) {
                        DayOfWeek.MONDAY -> mondayIdx
                        DayOfWeek.TUESDAY -> tuesdayIdx
                        DayOfWeek.WEDNESDAY -> wednesdayIdx
                        DayOfWeek.THURSDAY -> thursdayIdx
                        DayOfWeek.FRIDAY -> fridayIdx
                        DayOfWeek.SATURDAY -> saturdayIdx
                        DayOfWeek.SUNDAY -> sundayIdx
                    }

                    for (i in 1 until lines.size) {
                        val tokens = lines[i].split(",")
                        if (tokens.size > kotlin.math.max(kotlin.math.max(todayDayIndex, yesterdayDayIndex), endDateIdx)) {
                            val serviceId = tokens[serviceIdIdx].trim('"')
                            val startDate = tokens[startDateIdx].trim('"')
                            val endDate = tokens[endDateIdx].trim('"')

                            // Add today's services
                            if (todayDateFormatted >= startDate && todayDateFormatted <= endDate && tokens[todayDayIndex].trim('"') == "1") {
                                activeServices.add(serviceId)
                            }

                            // MIDNIGHT FIX: If it's after midnight (12am-6am), also include yesterday's services
                            if (isAfterMidnight && yesterdayDateFormatted >= startDate && yesterdayDateFormatted <= endDate && tokens[yesterdayDayIndex].trim('"') == "1") {
                                activeServices.add(serviceId)
                                Log.d("TransitViewModel", "Added yesterday's service $serviceId for late night operation")
                            }
                        }
                    }
                }

                val oldServiceCount = _activeServiceIdsToday.value.size
                _activeServiceIdsToday.value = activeServices

                Log.d("ServiceRefresh", "Refreshed active services: ${oldServiceCount} -> ${activeServices.size} (midnight-aware: $isAfterMidnight)")

            } catch (e: Exception) {
                Log.e("ServiceRefresh", "Error refreshing active services", e)
            }
        }
    }

    // Initialize optimized icon cache for better performance
    fun initializeIconCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val optimizedData = _optimizedTransitData.value
                if (optimizedData != null) {
                    // Initialize bitmap descriptors when Maps is ready
                    IconCache.getInstance().preloadBitmaps()
                    Log.d("IconCache", "BitmapDescriptors initialized successfully")
                }
            } catch (e: Exception) {
                Log.e("IconCache", "Error initializing BitmapDescriptors", e)
            }
        }
    }

    // Get stops for a specific route
    fun getStopsForRoute(routeId: String): List<BusStop> {
        return _transitData.value?.stopsForRoute?.get(routeId) ?: emptyList()
    }

    // Get route color
    fun getRouteColor(routeId: String): String {
        return _transitData.value?.routeShortNameToColor?.get(routeId) ?: "#0066CC"
    }

    // Get route by ID
    fun getRouteById(routeId: String): Route? {
        return _transitData.value?.routes?.find { it.shortName == routeId }
    }

    // Get stop by ID
    fun getStopById(stopId: String): BusStop? {
        return _transitData.value?.stopIdToBusStop?.get(stopId)
    }

    // Check if a stop-route combination is favorited
    fun isFavorite(stopId: String, routeId: String): Boolean {
        return _favoriteStopRoutes.value.contains(FavoriteStopRoute(stopId, routeId))
    }

    // Check if a stop is favorited (backward compatibility)
    fun isFavorite(stopId: String): Boolean {
        return _favoriteStops.value.contains(stopId)
    }

    // Get all routes
    fun getAllRoutes(): List<Route> {
        return _transitData.value?.routes ?: emptyList()
    }

    // Get all stops
    fun getAllStops(): List<BusStop> {
        return _transitData.value?.stops ?: emptyList()
    }

    // Force refresh real-time data
    fun forceRefreshRealTimeData() {
        viewModelScope.launch {
            val transitData = _transitData.value
            if (transitData != null) {
                fetchVehiclePositions(transitData.routeIdToShortName)
                fetchTripUpdates()

                // Refresh current stop arrivals if we have one selected
                currentStopId?.let { stopId ->
                    refreshArrivalTimes(stopId)
                }

                // Refresh favorites
                loadFavoritesWithArrivals()
            }
        }
    }

    // Clear all cached data and reload
    fun clearAndReload() {
        viewModelScope.launch {
            _isInitialLoading.value = true
            _loadingProgress.value = 0f

            // Clear existing data
            _transitData.value = null
            _optimizedTransitData.value = null
            _vehiclePositionFlow.value = emptyList()
            _stopArrivalTimes.value = emptyList()
            _favoriteStopsWithArrivals.value = emptyList()
            _timetable.value = emptyList()

            // Reload everything
            withContext(Dispatchers.IO) {
                loadAndProcessGtfsData(getApplication())
                createOptimizedTransitData()
            }

            val deferredTasks = listOf(
                async { loadStaticGtfsData(getApplication()) },
                async { loadFavoriteStops() },
                async { fetchWeatherData() }
            )

            deferredTasks.awaitAll()
            _loadingProgress.value = 1.0f
            _isInitialLoading.value = false
        }
    }

    // Get nearby stops within a radius
    fun getNearbyStops(centerLat: Double, centerLng: Double, radiusMeters: Double = 500.0): List<BusStop> {
        val allStops = _transitData.value?.stops ?: return emptyList()

        return allStops.filter { stop ->
            val distance = calculateDistanceMeters(
                centerLat, centerLng,
                stop.location.latitude, stop.location.longitude
            )
            distance <= radiusMeters
        }.sortedBy { stop ->
            calculateDistanceMeters(
                centerLat, centerLng,
                stop.location.latitude, stop.location.longitude
            )
        }
    }

    // Calculate distance between two points in meters
    private fun calculateDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadius * c
    }

    // Get routes serving a specific stop
    fun getRoutesForStop(stopId: String): List<String> {
        val transitData = _transitData.value ?: return emptyList()

        return transitData.stopsForRoute.entries
            .filter { (_, stops) -> stops.any { it.id == stopId } }
            .map { (routeId, _) -> routeId }
    }

    // Search stops by name
    fun searchStops(query: String): List<BusStop> {
        if (query.isBlank()) return emptyList()

        val allStops = _transitData.value?.stops ?: return emptyList()
        val lowercaseQuery = query.lowercase()

        return allStops.filter { stop ->
            stop.name.lowercase().contains(lowercaseQuery) ||
            stop.code?.lowercase()?.contains(lowercaseQuery) == true ||
            stop.id.lowercase().contains(lowercaseQuery)
        }.take(20) // Limit results for performance
    }

    // Search routes by name or number
    fun searchRoutes(query: String): List<Route> {
        if (query.isBlank()) return emptyList()

        val allRoutes = _transitData.value?.routes ?: return emptyList()
        val lowercaseQuery = query.lowercase()

        return allRoutes.filter { route ->
            route.shortName.lowercase().contains(lowercaseQuery) ||
            route.longName.lowercase().contains(lowercaseQuery) ||
            route.id.lowercase().contains(lowercaseQuery)
        }.take(20) // Limit results for performance
    }

    // Get vehicle positions for a specific route
    fun getVehiclesForRoute(routeId: String): List<VehiclePositionInfo> {
        return _vehiclePositionFlow.value.filter { it.routeId == routeId }
    }

    // Check if route has active vehicles
    fun hasActiveVehicles(routeId: String): Boolean {
        return _vehiclePositionFlow.value.any { it.routeId == routeId }
    }

    // Get route polyline points
    fun getRoutePolyline(routeId: String): List<LatLng> {
        return _optimizedTransitData.value?.routePolylinesOptimized?.get(routeId) ?: emptyList()
    }

    /**
     * Fetch service alerts from GTFS-Realtime API
     */
    fun fetchServiceAlerts() {
        viewModelScope.launch {
            try {
                val alertsFeed = repository.getServiceAlerts()
                val parsedAlerts = parseServiceAlerts(alertsFeed)
                _serviceAlerts.value = parsedAlerts
                Log.d("ServiceAlerts", "Fetched ${parsedAlerts.size} service alerts")
            } catch (e: Exception) {
                Log.e("ServiceAlerts", "Error fetching service alerts: ${e.message}")
            }
        }
    }

    /**
     * Fetch and analyze TripUpdates to detect operational warnings
     * (significant delays, cancellations not covered by formal alerts)
     */
    fun fetchOperationalWarnings() {
        viewModelScope.launch {
            try {
                val tripUpdatesFeed = repository.getTripUpdates()
                val warnings = extractOperationalWarnings(tripUpdatesFeed)
                _operationalWarnings.value = warnings
                Log.d("OperationalWarnings", "Detected ${warnings.size} operational warnings")
            } catch (e: Exception) {
                Log.e("OperationalWarnings", "Error extracting operational warnings: ${e.message}")
            }
        }
    }

    /**
     * Extract operational warnings from TripUpdates feed
     * Detects: significant delays (>10 min), moderate delays (5-10 min), cancellations
     */
    private fun extractOperationalWarnings(feed: GtfsRealtime.FeedMessage?): List<OperationalWarning> {
        if (feed == null) return emptyList()

        val warnings = mutableListOf<OperationalWarning>()
        val tripIdToRouteId = _tripIdToRouteId.value
        val routeIdToShortName = _transitData.value?.routeIdToShortName ?: emptyMap()

        // Group delays by route
        val routeDelays = mutableMapOf<String, MutableList<Int>>() // route -> list of delays in seconds
        val routeCancellations = mutableMapOf<String, Int>() // route -> count of cancelled trips

        feed.entityList.forEach { entity ->
            if (entity.hasTripUpdate()) {
                val tripUpdate = entity.tripUpdate
                val tripId = tripUpdate.trip.tripId

                // Map trip to route
                val gtfsRouteId = tripIdToRouteId[tripId]
                val routeShortName = if (gtfsRouteId != null) {
                    routeIdToShortName[gtfsRouteId] ?: gtfsRouteId
                } else {
                    null
                }

                if (routeShortName != null) {
                    // Check for cancellations
                    if (tripUpdate.trip.hasScheduleRelationship() &&
                        tripUpdate.trip.scheduleRelationship == GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED) {
                        routeCancellations[routeShortName] = (routeCancellations[routeShortName] ?: 0) + 1
                    }

                    // Collect delays from stop time updates
                    tripUpdate.stopTimeUpdateList.forEach { stopTimeUpdate ->
                        val delay = when {
                            stopTimeUpdate.hasArrival() -> stopTimeUpdate.arrival.delay
                            stopTimeUpdate.hasDeparture() -> stopTimeUpdate.departure.delay
                            else -> 0
                        }

                        // Only track significant delays (>5 minutes)
                        if (delay > 300) { // 5 minutes in seconds
                            routeDelays.getOrPut(routeShortName) { mutableListOf() }.add(delay)
                        }
                    }
                }
            }
        }

        // Generate warnings for routes with significant delays
        routeDelays.forEach { (routeId, delays) ->
            if (delays.size >= 2) { // At least 2 delayed stop times to be significant
                val avgDelay = delays.average().toInt()
                val avgDelayMinutes = avgDelay / 60

                when {
                    avgDelayMinutes >= 10 -> {
                        warnings.add(
                            OperationalWarning(
                                routeId = routeId,
                                warningType = OperationalWarningType.SIGNIFICANT_DELAYS,
                                delayMinutes = avgDelayMinutes,
                                affectedTrips = delays.size
                            )
                        )
                    }
                    avgDelayMinutes >= 5 -> {
                        warnings.add(
                            OperationalWarning(
                                routeId = routeId,
                                warningType = OperationalWarningType.MODERATE_DELAYS,
                                delayMinutes = avgDelayMinutes,
                                affectedTrips = delays.size
                            )
                        )
                    }
                }
            }
        }

        // Generate warnings for cancellations
        routeCancellations.forEach { (routeId, count) ->
            if (count > 0) {
                warnings.add(
                    OperationalWarning(
                        routeId = routeId,
                        warningType = OperationalWarningType.TRIP_CANCELLATION,
                        delayMinutes = 0,
                        affectedTrips = count
                    )
                )
            }
        }

        return warnings
    }

    /**
     * Parse GTFS-Realtime service alerts feed
     */
    /**
     * Parse GTFS-Realtime service alerts feed.
     *
     * NORMALIZATION STRATEGY:
     * - GTFS-RT feeds use route_id from routes.txt
     * - UI code uses route short_name (e.g., "1C", "2", "3")
     * - We normalize route_id → short_name during parsing so:
     *   1. ServiceAlert.affectedRoutes contains SHORT NAMES
     *   2. UI can call getAlertsForRoute(shortName) directly
     *   3. No translation needed at query time
     * - If route_id == short_name (common case), this is transparent
     * - If they differ, this prevents silently dropping alerts
     */
    private fun parseServiceAlerts(feed: com.google.transit.realtime.GtfsRealtime.FeedMessage): List<ServiceAlert> {
        val alerts = mutableListOf<ServiceAlert>()
        val routeIdToShortName = _transitData.value?.routeIdToShortName ?: emptyMap()
        val tripIdToRouteId = _tripIdToRouteId.value
        val allRouteShortNames = routeIdToShortName.values.toSet()

        feed.entityList.forEach { entity ->
            if (entity.hasAlert()) {
                val alert = entity.alert

                // Extract affected routes and NORMALIZE to short names
                // This ensures UI code can query by short name without translation
                val affectedRoutes = mutableSetOf<String>()

                if (alert.informedEntityList.isEmpty()) {
                    // No informed entities = agency-wide alert affecting all routes
                    affectedRoutes.addAll(allRouteShortNames)
                } else {
                    alert.informedEntityList.forEach { informedEntity ->
                        // Handle route_id - NORMALIZE to short name
                        if (informedEntity.hasRouteId()) {
                            val shortName = routeIdToShortName[informedEntity.routeId] ?: informedEntity.routeId
                            affectedRoutes.add(shortName)
                        }

                        // Handle trip_id by mapping back to route - NORMALIZE to short name
                        if (informedEntity.hasTrip() && informedEntity.trip.hasTripId()) {
                            val tripId = informedEntity.trip.tripId
                            val routeId = tripIdToRouteId[tripId]
                            if (routeId != null) {
                                val shortName = routeIdToShortName[routeId] ?: routeId
                                affectedRoutes.add(shortName)
                            }
                        }
                    }
                }

                // Extract header and description
                val headerText = if (alert.hasHeaderText() && alert.headerText.translationCount > 0) {
                    alert.headerText.getTranslation(0).text
                } else {
                    "Service Alert"
                }

                val descriptionText = if (alert.hasDescriptionText() && alert.descriptionText.translationCount > 0) {
                    alert.descriptionText.getTranslation(0).text
                } else {
                    ""
                }

                // Use protobuf effect enum FIRST, then fallback to keyword sniffing
                val alertType = if (alert.hasEffect()) {
                    when (alert.effect) {
                        GtfsRealtime.Alert.Effect.NO_SERVICE -> AlertType.NO_SERVICE
                        GtfsRealtime.Alert.Effect.REDUCED_SERVICE -> AlertType.REDUCED_SERVICE
                        GtfsRealtime.Alert.Effect.SIGNIFICANT_DELAYS -> AlertType.SIGNIFICANT_DELAYS
                        GtfsRealtime.Alert.Effect.DETOUR -> AlertType.DETOUR
                        GtfsRealtime.Alert.Effect.ADDITIONAL_SERVICE -> AlertType.ADDITIONAL_SERVICE
                        GtfsRealtime.Alert.Effect.MODIFIED_SERVICE -> AlertType.MODIFIED_SERVICE
                        GtfsRealtime.Alert.Effect.STOP_MOVED -> AlertType.STOP_MOVED
                        GtfsRealtime.Alert.Effect.OTHER_EFFECT -> AlertType.OTHER_EFFECT
                        GtfsRealtime.Alert.Effect.UNKNOWN_EFFECT -> {
                            // Fallback to keyword sniffing for unknown effects
                            inferAlertTypeFromText(descriptionText)
                        }
                        else -> AlertType.UNKNOWN_EFFECT
                    }
                } else {
                    // No effect field, use keyword sniffing
                    inferAlertTypeFromText(descriptionText)
                }

                // Get severity - use cause if available, otherwise infer
                val severity = if (alert.hasCause()) {
                    when (alert.cause) {
                        GtfsRealtime.Alert.Cause.ACCIDENT,
                        GtfsRealtime.Alert.Cause.STRIKE,
                        GtfsRealtime.Alert.Cause.POLICE_ACTIVITY -> AlertSeverity.SEVERE
                        GtfsRealtime.Alert.Cause.CONSTRUCTION,
                        GtfsRealtime.Alert.Cause.MAINTENANCE,
                        GtfsRealtime.Alert.Cause.TECHNICAL_PROBLEM -> AlertSeverity.WARNING
                        else -> inferSeverityFromText(descriptionText, alertType)
                    }
                } else {
                    inferSeverityFromText(descriptionText, alertType)
                }

                // Process ALL active periods (not just the first one)
                val activePeriods = mutableListOf<Pair<Long?, Long?>>()
                if (alert.activePeriodCount > 0) {
                    for (i in 0 until alert.activePeriodCount) {
                        val period = alert.getActivePeriod(i)
                        val start = if (period.hasStart()) period.start else null
                        val end = if (period.hasEnd()) period.end else null
                        activePeriods.add(Pair(start, end))
                    }
                } else {
                    // No active periods means always active
                    activePeriods.add(Pair(null, null))
                }

                // Store first period in main fields for backwards compatibility
                val firstPeriod = activePeriods.firstOrNull() ?: Pair(null, null)

                if (affectedRoutes.isNotEmpty()) {
                    alerts.add(
                        ServiceAlert(
                            alertId = entity.id,
                            affectedRoutes = affectedRoutes.toList(),
                            headerText = headerText,
                            descriptionText = descriptionText,
                            alertType = alertType,
                            severity = severity,
                            activePeriodStart = firstPeriod.first,
                            activePeriodEnd = firstPeriod.second,
                            activePeriods = activePeriods
                        )
                    )
                }
            }
        }

        return alerts
    }

    /**
     * Infer alert type from description text (fallback when effect enum is unknown/missing)
     */
    private fun inferAlertTypeFromText(text: String): AlertType {
        return when {
            text.contains("detour", ignoreCase = true) -> AlertType.DETOUR
            text.contains("delay", ignoreCase = true) -> AlertType.DELAY
            text.contains("stop moved", ignoreCase = true) -> AlertType.STOP_MOVED
            text.contains("stop closed", ignoreCase = true) -> AlertType.STOP_CLOSED
            text.contains("no service", ignoreCase = true) -> AlertType.NO_SERVICE
            text.contains("reduced service", ignoreCase = true) -> AlertType.REDUCED_SERVICE
            text.contains("service change", ignoreCase = true) -> AlertType.SERVICE_CHANGE
            else -> AlertType.OTHER
        }
    }

    /**
     * Infer severity from description text and alert type
     */
    private fun inferSeverityFromText(text: String, alertType: AlertType): AlertSeverity {
        return when {
            text.contains("severe", ignoreCase = true) ||
            text.contains("emergency", ignoreCase = true) ||
            alertType == AlertType.NO_SERVICE -> AlertSeverity.SEVERE
            text.contains("warning", ignoreCase = true) ||
            alertType == AlertType.DETOUR ||
            alertType == AlertType.SIGNIFICANT_DELAYS -> AlertSeverity.WARNING
            text.contains("info", ignoreCase = true) -> AlertSeverity.INFO
            else -> AlertSeverity.UNKNOWN
        }
    }

    /**
     * Get all active service alerts for a specific route.
     *
     * IMPORTANT: Pass route SHORT NAME (e.g., "1C", "2", "3") - NOT the GTFS route_id!
     * ServiceAlert.affectedRoutes contains short names, normalized during parsing.
     *
     * @param routeId The route SHORT NAME to query
     * @return List of active ServiceAlert objects affecting this route
     */
    fun getAlertsForRoute(routeId: String): List<ServiceAlert> {
        val currentTime = System.currentTimeMillis() / 1000 // Convert to seconds
        return _serviceAlerts.value.filter { alert ->
            // Check if route is affected
            val isRouteAffected = alert.affectedRoutes.contains(routeId)

            // Check if alert is currently active in ANY of its active periods
            val isActive = alert.activePeriods.isEmpty() || alert.activePeriods.any { (start, end) ->
                when {
                    start == null && end == null -> true // No time restriction
                    start != null && end != null -> currentTime >= start && currentTime <= end
                    start != null -> currentTime >= start
                    end != null -> currentTime <= end
                    else -> true
                }
            }

            isRouteAffected && isActive
        }
    }

    /**
     * Check if a route has any active detours.
     *
     * IMPORTANT: Pass route SHORT NAME (e.g., "1C", "2", "3") - NOT the GTFS route_id!
     *
     * @param routeId The route SHORT NAME to check
     * @return true if any active detour alerts affect this route
     */
    fun hasActiveDetour(routeId: String): Boolean {
        return getAlertsForRoute(routeId).any { it.alertType == AlertType.DETOUR }
    }

    /**
     * Check if a route has any active alerts (of any type).
     *
     * IMPORTANT: Pass route SHORT NAME (e.g., "1C", "2", "3") - NOT the GTFS route_id!
     *
     * @param routeId The route SHORT NAME to check
     * @return true if any active alerts affect this route
     */
    fun hasActiveAlerts(routeId: String): Boolean {
        return getAlertsForRoute(routeId).isNotEmpty()
    }

    /**
     * Get operational warnings for a specific route
     */
    fun getOperationalWarningsForRoute(routeId: String): List<OperationalWarning> {
        return _operationalWarnings.value.filter { it.routeId == routeId }
    }

    /**
     * Check if a route has operational warnings (delays or cancellations)
     */
    fun hasOperationalWarnings(routeId: String): Boolean {
        return getOperationalWarningsForRoute(routeId).isNotEmpty()
    }

    /**
     * HELPER: Get route short name from any identifier (route_id or shortName).
     * Use this if you're unsure whether you have a route_id or shortName.
     *
     * @param identifier Could be either route_id (e.g., "ROUTE_1C") or shortName (e.g., "1C")
     * @return The route short name, or the original identifier if not found
     */
    fun getRouteShortName(identifier: String): String {
        val routeIdToShortName = _transitData.value?.routeIdToShortName ?: emptyMap()

        // If it's already a shortName (in the values), return as-is
        if (routeIdToShortName.containsValue(identifier)) {
            return identifier
        }

        // If it's a route_id (in the keys), translate to shortName
        if (routeIdToShortName.containsKey(identifier)) {
            val shortName = routeIdToShortName[identifier]!!
            Log.d("TransitViewModel", "Translated route_id '$identifier' to shortName '$shortName' for alert query")
            return shortName
        }

        // Unknown identifier - log warning and return as-is
        Log.w("TransitViewModel", "Unknown route identifier '$identifier' - using as-is for alert query. " +
                "If alerts aren't showing, ensure you're passing route shortName not route_id")
        return identifier
    }

    /**
     * Check if a route has ANY issues (formal alerts OR operational warnings)
     */
    fun hasAnyIssues(routeId: String): Boolean {
        return hasActiveAlerts(routeId) || hasOperationalWarnings(routeId)
    }

    /**
     * Get all active alerts across all routes
     */
    fun getAllActiveAlerts(): List<ServiceAlert> {
        val currentTime = System.currentTimeMillis() / 1000
        return _serviceAlerts.value.filter { alert ->
            // Check if alert is currently active in ANY of its active periods
            alert.activePeriods.isEmpty() || alert.activePeriods.any { (start, end) ->
                when {
                    start == null && end == null -> true // No time restriction
                    start != null && end != null -> currentTime >= start && currentTime <= end
                    start != null -> currentTime >= start
                    end != null -> currentTime <= end
                    else -> true
                }
            }
        }
    }

    /**
     * Get count of alerts by severity for a route
     */
    fun getAlertSummaryForRoute(routeId: String): Map<AlertSeverity, Int> {
        val alerts = getAlertsForRoute(routeId)
        return alerts.groupingBy { it.severity }.eachCount()
    }

    // Toggle auto-refresh
    fun toggleAutoRefresh() {
        _isAutoRefreshEnabled.value = !_isAutoRefreshEnabled.value
    }

    // Manual refresh current stop
    fun refreshCurrentStop() {
        currentStopId?.let { stopId ->
            viewModelScope.launch {
                refreshArrivalTimes(stopId)
            }
        }
    }

    // Clear current stop
    fun clearCurrentStop() {
        currentStopId = null
        _stopArrivalTimes.value = emptyList()
    }

    // Save current map state
    fun saveCurrentMapState(cameraPosition: CameraPosition?, selectedRoute: String?, showLiveBuses: Boolean) {
        cameraPosition?.let { _savedCameraPosition.value = it }
        _savedSelectedRoute.value = selectedRoute
        _savedShowLiveBuses.value = showLiveBuses
    }

    // Compatibility wrapper for MapScreen calls using saveMapState(...)
    fun saveMapState(cameraPosition: CameraPosition, selectedRoute: String?, showLiveBuses: Boolean) {
        // Delegate to the existing function to keep single source of truth
        saveCurrentMapState(cameraPosition, selectedRoute, showLiveBuses)
    }

    // Reset map state
    fun clearSavedMapState() {
        _savedCameraPosition.value = null
        _savedSelectedRoute.value = null
        _savedShowLiveBuses.value = false
    }

    // Get arrival countdown in minutes
    fun getArrivalCountdown(arrivalTime: LocalTime): Int {
        val now = LocalTime.now()
        val arrival = arrivalTime

        // Handle next-day arrivals (after midnight)
        val minutesUntilArrival = if (arrival.isBefore(now)) {
            // Next day arrival
            val minutesUntilMidnight = 24 * 60 - (now.hour * 60 + now.minute)
            val minutesFromMidnight = arrival.hour * 60 + arrival.minute
            minutesUntilMidnight + minutesFromMidnight
        } else {
            // Same day arrival
            val nowMinutes = now.hour * 60 + now.minute
            val arrivalMinutes = arrival.hour * 60 + arrival.minute
            arrivalMinutes - nowMinutes
        }

        return minutesUntilArrival
    }

    // Format arrival time for display
    fun formatArrivalTime(arrivalTime: LocalTime, isRealTime: Boolean, delaySeconds: Int = 0): String {
        val countdown = getArrivalCountdown(arrivalTime)

        return when {
            countdown < 1 -> "Due"
            countdown == 1 -> "1 min"
            countdown < 60 -> "$countdown mins"
            else -> arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } + if (isRealTime && delaySeconds != 0) {
            val delayMinutes = delaySeconds / 60
            when {
                delayMinutes > 0 -> " (+${delayMinutes}m)"
                delayMinutes < 0 -> " (${delayMinutes}m)"
                else -> ""
            }
        } else ""
    }

    // Get formatted delay text
    fun getDelayText(delaySeconds: Int): String {
        val delayMinutes = delaySeconds / 60
        return when {
            delayMinutes > 1 -> "${delayMinutes} min late"
            delayMinutes < -1 -> "${-delayMinutes} min early"
            delayMinutes == 1 -> "1 min late"
            delayMinutes == -1 -> "1 min early"
            else -> "On time"
        }
    }

    // Handle app pause/resume for efficiency
    fun onAppPaused() {
        _isAutoRefreshEnabled.value = false
        Log.d("TransitViewModel", "App paused - disabled auto refresh")
    }

    fun onAppResumed() {
        _isAutoRefreshEnabled.value = true
        // Trigger a refresh when app resumes
        forceRefreshRealTimeData()
        Log.d("TransitViewModel", "App resumed - enabled auto refresh and triggered data refresh")
    }

    // Get statistics about the transit data
    fun getTransitStats(): Map<String, Any> {
        val transitData = _transitData.value
        val vehicleCount = _vehiclePositionFlow.value.size
        val favoriteCount = _favoriteStopRoutes.value.size

        return mapOf(
            "routes" to (transitData?.routes?.size ?: 0),
            "stops" to (transitData?.stops?.size ?: 0),
            "activeVehicles" to vehicleCount,
            "favorites" to favoriteCount,
            "activeServices" to _activeServiceIdsToday.value.size,
            "isDataReady" to _isStaticDataReady.value,
            "lastRefresh" to System.currentTimeMillis()
        )
    }

    init {
        // Optimized initialization - load basic data first, then optimize in background
        viewModelScope.launch {
            _loadingProgress.value = 0.1f

            // Load basic transit data on background thread
            withContext(Dispatchers.IO) {
                _loadingProgress.value = 0.2f
                loadAndProcessGtfsData(application)
                _loadingProgress.value = 0.5f

                // Create optimized data structure with precomputed values
                createOptimizedTransitData()
                _loadingProgress.value = 0.7f
            }

            // Load other data in parallel
            val deferredTasks = listOf(
                async { loadStaticGtfsData(application) },
                async { loadFavoriteStops() },
                async { fetchWeatherData() }
            )

            deferredTasks.awaitAll()
            _loadingProgress.value = 1.0f
            _isInitialLoading.value = false

            Log.d("TransitViewModel", "App initialization complete")
        }

        // Set up automatic refresh for real-time arrivals
        viewModelScope.launch {
            combine(
                minuteTickFlow,
                isAutoRefreshEnabled
            ) { _, autoRefreshEnabled ->
                autoRefreshEnabled
            }.collect { autoRefreshEnabled ->
                if (autoRefreshEnabled && currentStopId != null && !isRefreshing.get()) {
                    refreshArrivalTimes(currentStopId!!)
                }
            }
        }

        // Set up periodic refresh of active service IDs for midnight crossing
        viewModelScope.launch {
            while (true) {
                delay(300_000) // Check every 5 minutes

                val currentHour = LocalTime.now().hour
                val lastUpdateHour = _lastServiceUpdateHour.value

                // Refresh services if we've crossed midnight or if it's the first update
                if (lastUpdateHour == -1 ||
                    (lastUpdateHour >= 23 && currentHour <= 1) ||
                    (lastUpdateHour <= 1 && currentHour >= 23)) {

                    Log.d("ServiceRefresh", "Refreshing active services due to time change (last: $lastUpdateHour, current: $currentHour)")
                    refreshActiveServices()
                    _lastServiceUpdateHour.value = currentHour
                }
            }
        }

        // Set up automatic service alerts fetching (every 60 seconds)
        viewModelScope.launch {
            // Fetch immediately on startup
            fetchServiceAlerts()

            while (true) {
                delay(60_000) // Refresh every 60 seconds
                fetchServiceAlerts()
            }
        }

        // Set up automatic operational warnings extraction (every 30 seconds)
        viewModelScope.launch {
            // Fetch immediately on startup
            fetchOperationalWarnings()

            while (true) {
                delay(30_000) // Refresh every 30 seconds (more frequent than alerts)
                fetchOperationalWarnings()
            }
        }
    }

    // Create optimized data structure with all expensive computations done once
    private suspend fun createOptimizedTransitData() {
        withContext(Dispatchers.IO) {
            val transitData = _transitData.value ?: return@withContext

            Log.d("Optimization", "Starting data optimization...")

            // Precompute Color objects from hex strings (expensive operation)
            val routeShortNameToComputedColor = transitData.routeShortNameToColor.mapValues { (_, colorHex) ->
                try {
                    androidx.compose.ui.graphics.Color(colorHex.toColorInt())
                } catch (e: Exception) {
                    androidx.compose.ui.graphics.Color(0xFF0066CC) // Default blue
                }
            }

            // Precompute polylines with route short names as keys (eliminates runtime conversion)
            val routePolylinesOptimized = mutableMapOf<String, List<LatLng>>()
            transitData.routePolylines.forEach { (routeId, polylinePoints) ->
                val shortName = transitData.routeIdToShortName[routeId]
                if (shortName != null) {
                    routePolylinesOptimized[shortName] = polylinePoints
                }
            }

            // NEW: Precompute ALL polyline variations with route short names
            // FIXED: Merge all variations from routes with the same short name
            val routePolylinesAllVariationsOptimized = mutableMapOf<String, MutableList<List<LatLng>>>()
            transitData.routePolylinesAllVariations.forEach { (routeId, allVariations) ->
                val shortName = transitData.routeIdToShortName[routeId]
                if (shortName != null) {
                    // Merge variations for routes with the same short name
                    val existingVariations = routePolylinesAllVariationsOptimized.getOrPut(shortName) { mutableListOf() }
                    existingVariations.addAll(allVariations)

                    // ENHANCED LOGGING for Route 3
                    if (shortName == "3") {
                        Log.d("Optimization", "⭐ ROUTE 3 OPTIMIZATION:")
                        Log.d("Optimization", "  - Short name: $shortName, Route ID: $routeId")
                        Log.d("Optimization", "  - Adding ${allVariations.size} variations (total so far: ${existingVariations.size})")
                        allVariations.forEachIndexed { idx, variation ->
                            Log.d("Optimization", "    Variation #${idx + 1}: ${variation.size} points")
                        }
                    }
                }
            }

            // Log final merged counts
            routePolylinesAllVariationsOptimized.forEach { (shortName, variations) ->
                if (shortName == "3") {
                    Log.d("Optimization", "⭐ ROUTE 3 FINAL: ${variations.size} total variations merged")
                }
            }

            // Initialize icon cache with all route colors (eliminates expensive bitmap operations)
            try {
                // Initialize the IconCache if not already initialized
                IconCache.initialize(getApplication())

                // Preload bitmaps (but not BitmapDescriptors yet - that requires Maps to be ready)
                IconCache.getInstance().preloadBitmaps()
                IconCache.getInstance().preloadBusIconsForRoutes(routeShortNameToComputedColor)

                Log.d("Optimization", "Icon cache bitmaps preloaded successfully")
            } catch (e: Exception) {
                Log.e("Optimization", "Error preloading icon cache bitmaps", e)
            }

            val optimizedData = OptimizedTransitData(
                routes = transitData.routes,
                stops = transitData.stops,
                stopsForRoute = transitData.stopsForRoute,
                routeIdToShortName = transitData.routeIdToShortName,
                routeShortNameToColor = transitData.routeShortNameToColor,
                routeShortNameToComputedColor = routeShortNameToComputedColor,
                stopIdToBusStop = transitData.stopIdToBusStop,
                routeShortNameToTripIds = transitData.routeShortNameToTripIds,
                tripIdToOrderedStops = transitData.tripIdToOrderedStops,
                routePolylines = transitData.routePolylines,
                routePolylinesOptimized = routePolylinesOptimized,
                routePolylinesAllVariations = routePolylinesAllVariationsOptimized // NEW: All variations
            )

            withContext(Dispatchers.Main) {
                _optimizedTransitData.value = optimizedData
            }

            Log.d("Optimization", "Data optimization complete - ${routeShortNameToComputedColor.size} colors, ${routePolylinesOptimized.size} polylines, ${routePolylinesAllVariationsOptimized.size} routes with variations")
        }
    }

    private fun loadAndProcessGtfsData(context: Context) {
        _transitData.value = loadTransitData(context)
    }

    // Utility function to load transit data from GTFS files
    private fun loadTransitData(context: Context): TransitData {
        val routes = mutableListOf<Route>()
        val stops = mutableListOf<BusStop>()
        val stopsForRoute = mutableMapOf<String, MutableList<BusStop>>()
        val routeIdToShortName = mutableMapOf<String, String>()
        val routeShortNameToColor = mutableMapOf<String, String>()
        val stopIdToBusStop = mutableMapOf<String, BusStop>()
        val routeShortNameToTripIds = mutableMapOf<String, MutableList<String>>()
        val tripIdToOrderedStops = mutableMapOf<String, MutableList<String>>()
        val routePolylines = mutableMapOf<String, List<LatLng>>()
        val routeToAllShapePolylines = mutableMapOf<String, MutableList<List<LatLng>>>() // NEW: Declare at function scope

        try {
            // First, load calendar data to determine active services
            val activeServiceIds = loadActiveServiceIds(context)
            Log.d("TransitViewModel", "Found ${activeServiceIds.size} active services for today")
            // Make active services immediately available for downstream real-time calls
            _activeServiceIdsToday.value = activeServiceIds

            // Load routes from routes.txt
            val allRoutes = mutableMapOf<String, Route>()
            openGtfsFile(context, "routes.txt").bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isNotEmpty()) {
                    val header = getHeaderIndexMap(lines[0])
                    val routeIdIdx = header["route_id"] ?: return@use
                    val shortNameIdx = header["route_short_name"] ?: return@use
                    val longNameIdx = header["route_long_name"] ?: return@use
                    val colorIdx = header["route_color"]
                    val textColorIdx = header["route_text_color"]

                    for (line in lines.drop(1)) {
                        val fields = line.split(",").map { it.trim('"') }
                        if (fields.size > routeIdIdx) {
                            val routeId = fields[routeIdIdx]
                            val shortName = fields.getOrNull(shortNameIdx) ?: routeId
                            val longName = fields.getOrNull(longNameIdx) ?: shortName
                            val color = if (colorIdx != null && fields.size > colorIdx) fields[colorIdx] else "0066CC"
                            val textColor = if (textColorIdx != null && fields.size > textColorIdx) fields[textColorIdx] else "FFFFFF"

                            val route = Route(routeId, shortName, longName, "#$color", "#$textColor")
                            allRoutes[routeId] = route
                            routeIdToShortName[routeId] = shortName
                            routeShortNameToColor[shortName] = "#$color"
                        }
                    }
                }
            }

            // Load trips and filter routes to only include those with active service
            val activeRouteIds = mutableSetOf<String>()
            openGtfsFile(context, "trips.txt").bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isNotEmpty()) {
                    val header = getHeaderIndexMap(lines[0])
                    val routeIdIdx = header["route_id"] ?: return@use
                    val serviceIdIdx = header["service_id"] ?: return@use
                    val tripIdIdx = header["trip_id"] ?: return@use

                    for (line in lines.drop(1)) {
                        val fields = line.split(",").map { it.trim('"') }
                        if (fields.size > maxOf(routeIdIdx, serviceIdIdx, tripIdIdx)) {
                            val routeId = fields[routeIdIdx]
                            val serviceId = fields[serviceIdIdx]
                            val tripId = fields[tripIdIdx]

                            // Only include routes that have active service today
                            if (activeServiceIds.contains(serviceId)) {
                                activeRouteIds.add(routeId)

                                val shortName = routeIdToShortName[routeId]
                                if (shortName != null) {
                                    routeShortNameToTripIds.getOrPut(shortName) { mutableListOf() }.add(tripId)
                                }
                            }
                        }
                    }
                }
            }

            // Only add routes that have active service
            routes.addAll(allRoutes.values.filter { activeRouteIds.contains(it.id) })
            Log.d("TransitViewModel", "Filtered to ${routes.size} active routes from ${allRoutes.size} total routes")

            // Load stops from stops.txt
            openGtfsFile(context, "stops.txt").bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isNotEmpty()) {
                    val header = getHeaderIndexMap(lines[0])
                    val stopIdIdx = header["stop_id"] ?: return@use
                    val stopCodeIdx = header["stop_code"] // Add stop code index
                    val stopNameIdx = header["stop_name"] ?: return@use
                    val stopLatIdx = header["stop_lat"] ?: return@use
                    val stopLonIdx = header["stop_lon"] ?: return@use

                    for (line in lines.drop(1)) {
                        val fields = line.split(",").map { it.trim('"') }
                        if (fields.size > maxOf(stopIdIdx, stopNameIdx, stopLatIdx, stopLonIdx)) {
                            try {
                                val stopId = fields[stopIdIdx]
                                val stopCode = stopCodeIdx?.let { fields.getOrNull(it)?.takeIf { it.isNotBlank() } }
                                val stopName = fields[stopNameIdx]
                                val lat = fields[stopLatIdx].toDouble()
                                val lon = fields[stopLonIdx].toDouble()

                                val stop = BusStop(stopId, stopCode, stopName, LatLng(lat, lon))
                                stops.add(stop)
                                stopIdToBusStop[stopId] = stop
                            } catch (e: NumberFormatException) {
                                Log.w("TransitViewModel", "Invalid lat/lon for stop: $line")
                            }
                        }
                    }
                }
            }

            // Load trips to map routes to trips
            val tripIdToRouteId = mutableMapOf<String, String>()
            openGtfsFile(context, "trips.txt").bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isNotEmpty()) {
                    val header = getHeaderIndexMap(lines[0])
                    val routeIdIdx = header["route_id"] ?: return@use
                    val tripIdIdx = header["trip_id"] ?: return@use

                    for (line in lines.drop(1)) {
                        val fields = line.split(",").map { it.trim('"') }
                        if (fields.size > maxOf(routeIdIdx, tripIdIdx)) {
                            val routeId = fields[routeIdIdx]
                            val tripId = fields[tripIdIdx]
                            tripIdToRouteId[tripId] = routeId

                            val shortName = routeIdToShortName[routeId]
                            if (shortName != null) {
                                routeShortNameToTripIds.getOrPut(shortName) { mutableListOf() }.add(tripId)
                            }
                        }
                    }
                }
            }

            // Load stop_times to map stops to routes
            openGtfsFile(context, "stop_times.txt").bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isNotEmpty()) {
                    val header = getHeaderIndexMap(lines[0])
                    val tripIdIdx = header["trip_id"] ?: return@use
                    val stopIdIdx = header["stop_id"] ?: return@use
                    val stopSequenceIdx = header["stop_sequence"] ?: return@use

                    for (line in lines.drop(1)) {
                        val fields = line.split(",").map { it.trim('"') }
                        if (fields.size > maxOf(tripIdIdx, stopIdIdx, stopSequenceIdx)) {
                            val tripId = fields[tripIdIdx]
                            val stopId = fields[stopIdIdx]
                            val stopSequence = fields[stopSequenceIdx].toIntOrNull() ?: 0

                            val routeId = tripIdToRouteId[tripId]
                            val shortName = routeId?.let { routeIdToShortName[it] }
                            val stop = stopIdToBusStop[stopId]

                            if (shortName != null && stop != null) {
                                stopsForRoute.getOrPut(shortName) { mutableListOf() }.let { stopsList ->
                                    if (!stopsList.contains(stop)) {
                                        stopsList.add(stop)
                                    }
                                }
                            }

                            // Build ordered stops for each trip
                            tripIdToOrderedStops.getOrPut(tripId) { mutableListOf() }.add(stopId)
                        }
                    }
                }
            }

            // Load shapes from shapes.txt
            openGtfsFile(context, "shapes.txt").bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isNotEmpty()) {
                    val header = getHeaderIndexMap(lines[0])
                    val shapeIdIdx = header["shape_id"] ?: return@use
                    val shapePtLatIdx = header["shape_pt_lat"] ?: return@use
                    val shapePtLonIdx = header["shape_pt_lon"] ?: return@use
                    val shapePtSequenceIdx = header["shape_pt_sequence"] ?: return@use

                    // Store shape points with their sequence numbers for proper sorting
                    val shapePointsWithSequence = mutableMapOf<String, MutableList<Triple<Int, Double, Double>>>()

                    for (line in lines.drop(1)) {
                        val fields = line.split(",").map { it.trim('"') }
                        if (fields.size > maxOf(shapeIdIdx, shapePtLatIdx, shapePtLonIdx, shapePtSequenceIdx)) {
                            val shapeId = fields[shapeIdIdx]
                            val lat = fields[shapePtLatIdx].toDoubleOrNull()
                            val lon = fields[shapePtLonIdx].toDoubleOrNull()
                            val sequence = fields[shapePtSequenceIdx].toIntOrNull()

                            if (lat != null && lon != null && sequence != null) {
                                shapePointsWithSequence.getOrPut(shapeId) { mutableListOf() }.add(Triple(sequence, lat, lon))
                            }
                        }
                    }

                    // Process shapes by route, keeping track of which shapes belong to which routes
                    val routeToShapes = mutableMapOf<String, MutableSet<String>>()

                    // First, map shapes to routes
                    for (shapeId in shapePointsWithSequence.keys) {
                        val routeId = shapeIdToRouteId(shapeId, tripIdToRouteId)
                        if (routeId != null) {
                            routeToShapes.getOrPut(routeId) { mutableSetOf() }.add(shapeId)
                        }
                    }

                    // For each route, process ALL shapes and keep track of them
                    for ((routeId, shapeIds) in routeToShapes) {
                        if (shapeIds.isNotEmpty()) {
                            // NEW: Store all valid shapes for this route
                            val allShapesForRoute = mutableListOf<List<LatLng>>()
                            var bestShape: List<LatLng>? = null
                            var maxPoints = 0

                            shapeIds.forEach { shapeId ->
                                val unsortedPoints = shapePointsWithSequence[shapeId]
                                if (unsortedPoints != null && unsortedPoints.isNotEmpty()) {
                                    // Sort points by sequence to get correct order
                                    val sortedPoints = unsortedPoints.sortedBy { it.first }

                                    // Convert to LatLng list
                                    val shapePolylinePoints = sortedPoints.map { (_, lat, lon) ->
                                        LatLng(lat, lon)
                                    }

                                    // NEW: Add ALL shapes with at least 2 points
                                    if (shapePolylinePoints.size >= 2) {
                                        allShapesForRoute.add(shapePolylinePoints)
                                    }

                                    // Keep track of the shape with the most points (for backwards compatibility)
                                    if (shapePolylinePoints.size > maxPoints) {
                                        maxPoints = shapePolylinePoints.size
                                        bestShape = shapePolylinePoints
                                    }
                                }
                            }

                            // Store ALL shapes for this route
                            if (allShapesForRoute.isNotEmpty()) {
                                routeToAllShapePolylines[routeId] = allShapesForRoute

                                // ENHANCED LOGGING for Route 3 debugging
                                if (routeId == "3" || routeId.contains("3")) {
                                    Log.d("PolylineDebug", "⭐ ROUTE 3 DETAILED ANALYSIS:")
                                    Log.d("PolylineDebug", "  - Found ${shapeIds.size} shape IDs: $shapeIds")
                                    Log.d("PolylineDebug", "  - Processed ${allShapesForRoute.size} valid shape variations")
                                    allShapesForRoute.forEachIndexed { idx, shape ->
                                        Log.d("PolylineDebug", "    Shape #${idx + 1}: ${shape.size} points")
                                        if (shape.isNotEmpty()) {
                                            Log.d("PolylineDebug", "      Start: ${shape.first()}")
                                            Log.d("PolylineDebug", "      End: ${shape.last()}")
                                        }
                                    }
                                } else {
                                    Log.d("PolylineDebug", "Route $routeId: Found ${allShapesForRoute.size} shape variations (from ${shapeIds.size} shape IDs)")
                                }
                            }

                            // Use the best (most complete) shape for backwards compatibility
                            bestShape?.let { polylinePoints ->
                                routePolylines[routeId] = polylinePoints
                                Log.d("PolylineDebug", "Route $routeId: Using best shape with ${polylinePoints.size} points for single-polyline mode")
                            }
                        }
                    }
                }
            }

            // Sort stops for each trip by stop sequence
            tripIdToOrderedStops.forEach { (_, stops) ->
                stops.sortBy { stopId ->
                    // This is a simplified sort - you might want to sort by actual stop_sequence
                    stops.indexOf(stopId)
                }
            }

            Log.d("TransitViewModel", "Loaded ${routes.size} routes, ${stops.size} stops")

        } catch (e: Exception) {
            Log.e("TransitViewModel", "Error loading GTFS data", e)
        }

        return TransitData(
            routes = routes,
            stops = stops,
            stopsForRoute = stopsForRoute,
            routeIdToShortName = routeIdToShortName,
            routeIdToRoute = routes.associateBy { it.id }, // Create route ID to Route object mapping
            routeShortNameToColor = routeShortNameToColor,
            stopIdToBusStop = stopIdToBusStop,
            routeShortNameToTripIds = routeShortNameToTripIds,
            tripIdToOrderedStops = tripIdToOrderedStops,
            routePolylines = routePolylines, // Include polylines in TransitData
            routePolylinesAllVariations = routeToAllShapePolylines // NEW: Include all shape variations
        )
    }

    // Utility function to open GTFS files
    private fun openGtfsFile(context: Context, fileName: String): InputStream {
        val downloadedFile = File(context.filesDir, fileName)
        if (downloadedFile.exists() && downloadedFile.length() > 0) {
            Log.d("TransitViewModel", "Loading $fileName from downloaded files")
            return downloadedFile.inputStream()
        }
        Log.d("TransitViewModel", "Loading $fileName from bundled assets")
        return context.assets.open(fileName)
    }

    // Utility function to parse CSV headers
    private fun getHeaderIndexMap(headerLine: String): Map<String, Int> {
        return headerLine.split(",")
            .mapIndexed { index, header -> header.trim('"') to index }
            .toMap()
    }

    // Utility function to load active service IDs for today
    private fun loadActiveServiceIds(context: Context): Set<String> {
        val activeServices = mutableSetOf<String>()
        val today = LocalDate.now()
        val todayDayOfWeek = today.dayOfWeek
        val todayDateFormatted = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        // MIDNIGHT FIX: Also check yesterday's services for late night routes
        val yesterday = today.minusDays(1)
        val yesterdayDayOfWeek = yesterday.dayOfWeek
        val yesterdayDateFormatted = yesterday.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val isAfterMidnight = LocalTime.now().hour < 6 // Consider 12am-6am as "after midnight"

        try {
            openGtfsFile(context, "calendar.txt").bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isEmpty()) return@use

                val header = getHeaderIndexMap(lines[0])
                val serviceIdIdx = header["service_id"] ?: return@use
                val mondayIdx = header["monday"] ?: return@use
                val tuesdayIdx = header["tuesday"] ?: return@use
                val wednesdayIdx = header["wednesday"] ?: return@use
                val thursdayIdx = header["thursday"] ?: return@use
                val fridayIdx = header["friday"] ?: return@use
                val saturdayIdx = header["saturday"] ?: return@use
                val sundayIdx = header["sunday"] ?: return@use
                val startDateIdx = header["start_date"] ?: return@use
                val endDateIdx = header["end_date"] ?: return@use

                val todayDayIndex = when (todayDayOfWeek) {
                    DayOfWeek.MONDAY -> mondayIdx
                    DayOfWeek.TUESDAY -> tuesdayIdx
                    DayOfWeek.WEDNESDAY -> wednesdayIdx
                    DayOfWeek.THURSDAY -> thursdayIdx
                    DayOfWeek.FRIDAY -> fridayIdx
                    DayOfWeek.SATURDAY -> saturdayIdx
                    DayOfWeek.SUNDAY -> sundayIdx
                }

                val yesterdayDayIndex = when (yesterdayDayOfWeek) {
                    DayOfWeek.MONDAY -> mondayIdx
                    DayOfWeek.TUESDAY -> tuesdayIdx
                    DayOfWeek.WEDNESDAY -> wednesdayIdx
                    DayOfWeek.THURSDAY -> thursdayIdx
                    DayOfWeek.FRIDAY -> fridayIdx
                    DayOfWeek.SATURDAY -> saturdayIdx
                    DayOfWeek.SUNDAY -> sundayIdx
                }

                for (i in 1 until lines.size) {
                    val tokens = lines[i].split(",")
                    if (tokens.size > kotlin.math.max(kotlin.math.max(todayDayIndex, yesterdayDayIndex), endDateIdx)) {
                        val serviceId = tokens[serviceIdIdx].trim('"')
                        val startDate = tokens[startDateIdx].trim('"')
                        val endDate = tokens[endDateIdx].trim('"')

                        // Add today's services
                        if (todayDateFormatted >= startDate && todayDateFormatted <= endDate && tokens[todayDayIndex].trim('"') == "1") {
                            activeServices.add(serviceId)
                        }

                        // MIDNIGHT FIX: If it's after midnight (12am-6am), also include yesterday's services
                        if (isAfterMidnight && yesterdayDateFormatted >= startDate && yesterdayDateFormatted <= endDate && tokens[yesterdayDayIndex].trim('"') == "1") {
                            activeServices.add(serviceId)
                            Log.d("TransitViewModel", "Added yesterday's service $serviceId for late night operation")
                        }
                    }
                }
            }
            Log.d("TransitViewModel", "Found ${activeServices.size} active services for today (midnight-aware: $isAfterMidnight)")
        } catch (e: Exception) {
            Log.e("TransitViewModel", "Error loading calendar.txt", e)
        }

        return activeServices
    }

    // Add missing helper function for shape mapping
    private fun shapeIdToRouteId(shapeId: String, tripIdToRouteId: Map<String, String>): String? {
        return try {
            val context = getApplication<Application>()
            openGtfsFile(context, "trips.txt").bufferedReader().use { reader ->
                val lines = reader.readLines()
                if (lines.isEmpty()) return@use null

                val header = getHeaderIndexMap(lines[0])
                val shapeIdIdx = header["shape_id"] ?: return@use null
                val routeIdIdx = header["route_id"] ?: return@use null

                for (line in lines.drop(1)) {
                    val fields = line.split(",").map { it.trim('"') }
                    if (fields.size > maxOf(shapeIdIdx, routeIdIdx)) {
                        val tripShapeId = fields[shapeIdIdx]
                        if (tripShapeId == shapeId) {
                            val routeId = fields[routeIdIdx]
                            return@use routeId
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e("TransitViewModel", "Error connecting shape $shapeId to route", e)
            null
        }
    }

    // Helper function to infer route from vehicle location when trip/route IDs are missing
    private fun inferRouteFromLocation(
        latitude: Double,
        longitude: Double,
        vehicleId: String
    ): String? {
        // Find the closest route polyline to this vehicle's position
        val optimizedData = _optimizedTransitData.value
        if (optimizedData != null) {
            var closestRoute: String? = null
            var minDistance = Double.MAX_VALUE
            val maxProximityMeters = 100.0 // Only consider routes within 100 meters

            optimizedData.routePolylinesAllVariations.forEach { (routeShortName, variations) ->
                // Check all polyline variations for this route
                variations.forEach { polyline ->
                    // Check distance to each point in the polyline
                    polyline.forEach { point ->
                        val distance = calculateDistance(
                            latitude, longitude,
                            point.latitude, point.longitude
                        )
                        if (distance < minDistance) {
                            minDistance = distance
                            closestRoute = routeShortName
                        }
                    }
                }
            }

            // Only return the route if the vehicle is reasonably close to it
            if (closestRoute != null && minDistance <= maxProximityMeters) {
                return closestRoute
            }
        }

        // Could not infer route
        return null
    }

    // Helper function to calculate distance between two coordinates (Haversine formula)
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadius * c
    }

    // Add missing vehicle processing function
    private fun processVehiclePositions(routeIdToShortName: Map<String, String>, tripIdToRouteId: Map<String, String>) {
        val vehiclePositions = _vehiclePositions.value ?: return

        // Cache frequently accessed values to avoid repeated StateFlow access
        val activeServices = _activeServiceIdsToday.value
        val tripIdToServiceId = _tripIdToServiceId.value
        val tripIdToHeadsign = _tripIdToHeadsign.value

        Log.d("VehicleDebug", "=== PROCESSING VEHICLES ===")
        Log.d("VehicleDebug", "Total raw vehicles: ${vehiclePositions.entityList.size}")
        Log.d("VehicleDebug", "Active services: ${activeServices.size} - $activeServices")
        Log.d("VehicleDebug", "Trip to route mapping size: ${tripIdToRouteId.size}")
        Log.d("VehicleDebug", "Route to short name mapping size: ${routeIdToShortName.size}")

        // Pre-allocate list with expected capacity for better performance
        val vehicleInfoList = ArrayList<VehiclePositionInfo>(vehiclePositions.entityList.size)


        var processedCount = 0
        var skippedNoTrip = 0
        var skippedNoRoute = 0
        var skippedInactiveService = 0
        var fallbackRouteCount = 0

        // MIDNIGHT FIX: Be more permissive with vehicle filtering after midnight
        val currentHour = LocalTime.now().hour
        val isAfterMidnight = currentHour < 6 || currentHour >= 22 // 10pm-6am is "late night"
        val shouldBePermissive = isAfterMidnight

        vehiclePositions.entityList.forEach { entity ->
            // Early exit conditions for better performance
            if (!entity.hasVehicle() || !entity.vehicle.hasPosition()) {
                return@forEach
            }

            val vehicle = entity.vehicle
            val trip = vehicle.trip
            val position = vehicle.position

            // ENHANCED LOGIC: Handle vehicles with and without trip IDs
            var tripId: String? = null
            var routeId: String? = null
            var routeShortName: String? = null

            // Method 1: Try to get trip ID (existing logic)
            if (trip.hasTripId() && trip.tripId.isNotBlank()) {
                tripId = trip.tripId
                routeId = tripIdToRouteId[tripId]
                if (routeId != null) {
                    routeShortName = routeIdToShortName[routeId]
                } else if (shouldBePermissive) {
                    // MIDNIGHT DEBUG: Log failed trip lookups
                    Log.d("VehicleMidnight", "Vehicle ${vehicle.vehicle.id}: Trip '$tripId' not found in mapping (after midnight)")
                }
            }

            // Method 2: FALLBACK - Try to get route directly from vehicle
            if (routeShortName == null && trip.hasRouteId() && trip.routeId.isNotBlank()) {
                routeId = trip.routeId
                routeShortName = routeIdToShortName[routeId]
                if (routeShortName != null) {
                    fallbackRouteCount++
                    if (shouldBePermissive) {
                        Log.d("VehicleMidnight", "Vehicle ${vehicle.vehicle.id}: Found route via fallback - Route: $routeShortName")
                    }
                }
            }

            // METHOD 3: SMART FALLBACK - Infer route from vehicle location
            if (routeShortName == null && shouldBePermissive) {
                val vehicleId = if (vehicle.hasVehicle()) vehicle.vehicle.id else entity.id

                // Try to infer route from geographic proximity to route polylines
                val inferredRoute = inferRouteFromLocation(
                    latitude = position.latitude.toDouble(),
                    longitude = position.longitude.toDouble(),
                    vehicleId = vehicleId
                )

                if (inferredRoute != null) {
                    routeShortName = inferredRoute
                    fallbackRouteCount++
                    Log.d("VehicleMidnight", "Vehicle $vehicleId: Inferred route '$inferredRoute' from location (${position.latitude}, ${position.longitude})")
                } else {
                    // Last resort: mark as Unknown
                    Log.w("VehicleMidnight", "Vehicle $vehicleId: Could not determine route - tripId='$tripId', routeId='$routeId', marking as Unknown")
                    routeShortName = "Unknown"
                    fallbackRouteCount++
                }
            } else if (routeShortName == null) {
                // Not in permissive mode and no route found - skip this vehicle
                if (tripId == null) {
                    skippedNoTrip++
                } else {
                    skippedNoRoute++
                }
                return@forEach
            }

            // Service filtering: During midnight hours, allow ALL vehicles regardless of service status
            val serviceId = if (tripId != null) tripIdToServiceId[tripId] else null
            val isServiceActive = activeServices.contains(serviceId)

            if (!isServiceActive && !shouldBePermissive && serviceId != null) {
                // Only filter during normal business hours (6am-10pm)
                skippedInactiveService++
                return@forEach
            }

            val vehicleId = if (vehicle.hasVehicle()) vehicle.vehicle.id else entity.id
            val timestamp = if (vehicle.hasTimestamp()) vehicle.timestamp else System.currentTimeMillis() / 1000
            val bearing = if (position.hasBearing()) position.bearing else null

            val vehicleInfo = VehiclePositionInfo(
                vehicleId = vehicleId,
                latitude = position.latitude,
                longitude = position.longitude,
                routeId = routeShortName,
                bearing = bearing,
                speedMps = if (position.hasSpeed()) position.speed else null,
                timestamp = timestamp,
                label = if (vehicle.hasVehicle() && vehicle.vehicle.hasLabel()) vehicle.vehicle.label else null,
                occupancyStatus = if (vehicle.hasOccupancyStatus()) vehicle.occupancyStatus.name else null,
                headsign = if (tripId != null) tripIdToHeadsign[tripId] else null
            )

            vehicleInfoList.add(vehicleInfo)
            processedCount++
        }

        Log.d("VehicleDebug", "=== PROCESSING SUMMARY ===")
        Log.d("VehicleDebug", "Processed: $processedCount vehicles")
        Log.d("VehicleDebug", "Fallback route matches: $fallbackRouteCount")
        Log.d("VehicleDebug", "Permissive mode (midnight): $shouldBePermissive")
        Log.d("VehicleDebug", "Skipped - No trip: $skippedNoTrip")
        Log.d("VehicleDebug", "Skipped - No route: $skippedNoRoute")
        Log.d("VehicleDebug", "Skipped - Inactive service: $skippedInactiveService")

        // Update vehicle positions flow
        _vehiclePositionFlow.value = vehicleInfoList
    }

    // Add toggle favorite functions
    fun toggleFavoriteStop(stopId: String, routeId: String) {
        val currentFavorites = _favoriteStopRoutes.value.toMutableSet()
        val favorite = FavoriteStopRoute(stopId, routeId)
        if (currentFavorites.contains(favorite)) {
            currentFavorites.remove(favorite)
        } else {
            currentFavorites.add(favorite)
        }
        _favoriteStopRoutes.value = currentFavorites
        saveFavoriteStops()
        loadFavoritesWithArrivals() // Refresh favorites display
    }

    fun toggleFavoriteStop(stopId: String) {
        val transitData = _transitData.value
        val primaryRouteId = transitData?.stopsForRoute?.entries?.find { (_, stops) ->
            stops.any { it.id == stopId }
        }?.key

        if (primaryRouteId != null) {
            toggleFavoriteStop(stopId, primaryRouteId)
        }
    }

    // Add save/load favorite functions
    private fun saveFavoriteStops() {
        val prefs = getApplication<Application>().getSharedPreferences("transit_prefs", Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val favoritesJson = gson.toJson(_favoriteStopRoutes.value.toList())
        prefs.edit().putString("favorite_stops", favoritesJson).apply()
    }

    private fun loadFavoriteStops() {
        val prefs = getApplication<Application>().getSharedPreferences("transit_prefs", Context.MODE_PRIVATE)
        val favoritesJson = prefs.getString("favorite_stops", null)
        if (favoritesJson != null) {
            try {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<FavoriteStopRoute>>() {}.type
                val favoritesList: List<FavoriteStopRoute> = gson.fromJson(favoritesJson, type)
                _favoriteStopRoutes.value = favoritesList.toSet()
            } catch (e: Exception) {
                Log.e("TransitViewModel", "Error loading favorite stops", e)
            }
        }
    }

    private fun fetchWeatherData() {
        viewModelScope.launch {
            _isWeatherLoading.value = true
            try {
                val weather = weatherRepository.getCurrentWeather()
                _weatherData.value = weather
            } catch (e: Exception) {
                Log.e("TransitViewModel", "Error fetching weather", e)
            } finally {
                _isWeatherLoading.value = false
            }
        }
    }

    fun fetchTripUpdates() {
        viewModelScope.launch {
            try {
                _tripUpdates.value = repository.getTripUpdates()
            } catch (e: Exception) {
                Log.e("TransitViewModel", "Error fetching trip updates", e)
            }
        }
    }

    /**
     * Loads arrivals for all favorited stop-route pairs by calling the new repository method
     * that merges static and real-time data.
     */
    fun loadFavoritesWithArrivals() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val favoriteStopRoutes = _favoriteStopRoutes.value
                if (favoriteStopRoutes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _favoriteStopsWithArrivals.value = emptyList()
                    }
                    return@launch
                }

                isStaticDataReady.first { it }
                val activeServiceIds = _activeServiceIdsToday.value.toList()
                val transitDataSnapshot = _transitData.value

                // Process all favorites in parallel for faster loading.
                val favoritesWithArrivals = favoriteStopRoutes.map { favoriteStopRoute ->
                    async {
                        try {
                            // Call the repository method that returns MergedArrivalTime
                            val mergedArrivals = repository.getMergedArrivalsForStop(
                                stopId = favoriteStopRoute.stopId,
                                activeServiceIds = activeServiceIds,
                                routeIdToShortName = transitDataSnapshot?.routeIdToShortName ?: emptyMap()
                            )

                            // Filter arrivals to only show the favorited route (match by short name)
                            val routeSpecificArrivals = mergedArrivals.filter { arrival ->
                                val shortName = transitDataSnapshot?.routeIdToShortName?.get(arrival.routeId) ?: arrival.routeId
                                shortName == favoriteStopRoute.routeId
                            }

                            // Map MergedArrivalTime to StopArrivalTime using short names for UI
                            val arrivals = routeSpecificArrivals.map { arrival ->
                                val shortName = transitDataSnapshot?.routeIdToShortName?.get(arrival.routeId) ?: arrival.routeId
                                StopArrivalTime(
                                    routeId = shortName,
                                    arrivalTime = arrival.arrivalTime,
                                    isRealTime = arrival.isRealTime,
                                    delaySeconds = arrival.delaySeconds,
                                    scheduledTime = if (arrival.isRealTime) arrival.scheduledTime else null,
                                    isFeedFresh = arrival.isFeedFresh
                                )
                            }

                            FavoriteStopWithArrivals(
                                stopId = favoriteStopRoute.stopId,
                                routeId = favoriteStopRoute.routeId,
                                arrivals = arrivals
                            )
                        } catch (e: Exception) {
                            Log.w("TransitViewModel", "Error loading arrivals for favorite stop ${favoriteStopRoute.stopId} route ${favoriteStopRoute.routeId}", e)
                            FavoriteStopWithArrivals(
                                stopId = favoriteStopRoute.stopId,
                                routeId = favoriteStopRoute.routeId,
                                arrivals = emptyList()
                            )
                        }
                    }
                }.awaitAll()

                withContext(Dispatchers.Main) {
                    _favoriteStopsWithArrivals.value = favoritesWithArrivals
                    // Update backward compatibility favorites list
                    _favoriteStops.value = favoriteStopRoutes.map { it.stopId }.toSet()
                }

            } catch (e: Exception) {
                Log.e("TransitViewModel", "Error in batch loading favorites", e)
                withContext(Dispatchers.Main) {
                    _favoriteStopsWithArrivals.value = emptyList()
                }
            }
        }
    }

    /**
     * Gets merged arrivals for a single stop. Used by screens like NearbyStops and MapScreen.
     */
    suspend fun getMergedArrivalsForStop(stopId: String): List<StopArrivalTime> {
        return withContext(Dispatchers.IO) {
            try {
                isStaticDataReady.first { it }
                val activeServiceIds = _activeServiceIdsToday.value.toList()
                val transitDataSnapshot = _transitData.value

                // Call the repository method that returns MergedArrivalTime
                val mergedArrivals = repository.getMergedArrivalsForStop(
                    stopId = stopId,
                    activeServiceIds = activeServiceIds,
                    routeIdToShortName = transitDataSnapshot?.routeIdToShortName ?: emptyMap()
                )

                // Map MergedArrivalTime to StopArrivalTime with correct field mapping (route short names)
                return@withContext mergedArrivals.map { arrival ->
                    val shortName = transitDataSnapshot?.routeIdToShortName?.get(arrival.routeId) ?: arrival.routeId
                    StopArrivalTime(
                        routeId = shortName,
                        arrivalTime = arrival.arrivalTime,
                        isRealTime = arrival.isRealTime,
                        delaySeconds = arrival.delaySeconds,
                        scheduledTime = if (arrival.isRealTime) arrival.scheduledTime else null,
                        isFeedFresh = arrival.isFeedFresh
                    )
                }
            } catch (e: Exception) {
                Log.e("TransitViewModel", "Error loading merged arrivals for stop $stopId", e)
                return@withContext emptyList()
            }
        }
    }

    /**
     * Enhanced function to load and auto-refresh arrival times for a stop
     */
    fun loadStopArrivalTimes(stopId: String) {
        currentStopId = stopId
        viewModelScope.launch {
            refreshArrivalTimes(stopId)
        }
    }

    private suspend fun refreshArrivalTimes(stopId: String) {
        if (isRefreshing.compareAndSet(false, true)) {
            try {
                isStaticDataReady.first { it }
                val activeServiceIds = _activeServiceIdsToday.value.toList()
                val transitDataSnapshot = _transitData.value

                // Call the enhanced repository method
                val mergedArrivals = repository.getMergedArrivalsForStop(stopId, activeServiceIds)

                // Convert to UI data class with improved time handling (use short names)
                val uiArrivals = mergedArrivals.map { arrival ->
                    val shortName = transitDataSnapshot?.routeIdToShortName?.get(arrival.routeId) ?: arrival.routeId
                    StopArrivalTime(
                        routeId = shortName,
                        arrivalTime = arrival.arrivalTime,
                        isRealTime = arrival.isRealTime,
                        delaySeconds = arrival.delaySeconds,
                        scheduledTime = if (arrival.isRealTime) arrival.scheduledTime else null
                    )
                }.take(6) // Limit to 6 arrivals total (typically 2 routes × 3 times each)

                _stopArrivalTimes.value = uiArrivals

            } catch (e: Exception) {
                Log.e("TransitViewModel", "Error refreshing arrivals for stop $stopId", e)
            } finally {
                isRefreshing.set(false)
            }
        }
    }

    /**
     * Toggle automatic refresh of real-time data
     */
    fun setAutoRefresh(enabled: Boolean) {
        _isAutoRefreshEnabled.value = enabled
    }

    fun fetchVehiclePositions(routeIdToShortName: Map<String, String>) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                // Always try to fetch real-time data, regardless of service schedule
                _vehiclePositions.value = repository.getVehiclePositions()

                // Ensure static data is ready AND trip mapping is populated
                if (!isStaticDataReady.value || _tripIdToRouteId.value.isEmpty()) {
                    Log.d("VehiclePositions", "Waiting for static data to be ready... (isReady: ${isStaticDataReady.value}, tripMappingSize: ${_tripIdToRouteId.value.size})")
                    isStaticDataReady.first { it }

                    // Double-check that trip mapping is actually populated
                    if (_tripIdToRouteId.value.isEmpty()) {
                        Log.w("VehiclePositions", "Static data ready but trip mapping is empty! Skipping vehicle processing.")
                        return@launch
                    }
                    Log.d("VehiclePositions", "Static data became ready, trip mapping size: ${_tripIdToRouteId.value.size}")
                }

                // Validate route mapping input
                if (routeIdToShortName.isEmpty()) {
                    Log.w("VehiclePositions", "Route ID to short name mapping is empty - cannot process vehicles")
                    return@launch
                }

                // Process vehicles with verified data
                val tripMapping = _tripIdToRouteId.value
                Log.d("VehiclePositions", "Processing vehicles with ${tripMapping.size} trip mappings and ${routeIdToShortName.size} route mappings")
                processVehiclePositions(routeIdToShortName, tripMapping)

                // Log everything after processing is complete
                val vehicleCount = _vehiclePositions.value?.entityList?.size ?: 0
                val processedCount = _vehiclePositionFlow.value.size
                val fetchTime = System.currentTimeMillis() - startTime

                Log.d("VehiclePositions", "Raw vehicles: $vehicleCount, Processed: $processedCount, Active services: ${_activeServiceIdsToday.value.size}")
                Log.d("MapScreen", "Vehicle fetch took ${fetchTime}ms")

                // If we processed 0 vehicles but had raw data, log the issue
                if (vehicleCount > 0 && processedCount == 0) {
                    Log.w("VehiclePositions", "WARNING: Received $vehicleCount vehicles but processed 0 - check service filtering and mapping logic")
                }

            } catch (e: Exception) {
                Log.e("TransitViewModel", "Error fetching vehicle positions: ${e.message}")
                // Don't clear existing positions on error - keep showing last known positions
            }
        }
    }

    fun getArrivalTimesForStop(stopId: String) {
        viewModelScope.launch {
            _isArrivalsLoading.value = true
            try {
                _stopArrivalTimes.value = getMergedArrivalsForStop(stopId)
            } catch (e: Exception) {
                Log.e("TransitViewModel", "Error getting arrival times for stop $stopId", e)
                _stopArrivalTimes.value = emptyList()
            } finally {
                _isArrivalsLoading.value = false
            }
        }
    }

    fun loadTimetableForStop(stopId: String) {
        loadTimetableForStop(stopId, _selectedTimetableDate.value)
    }

    fun loadTimetableForStop(stopId: String, date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            _isTimetableLoading.value = true
            _timetable.value = emptyList()

            try {
                isStaticDataReady.first { it }
                val activeServiceIds = getActiveServiceIdsForDate(date)
                val staticArrivals = repository.getAllStaticArrivalsForStop(stopId, activeServiceIds)

                val timetableEntries = staticArrivals
                    .groupBy { it.routeId }
                    .flatMap { (routeId, arrivals) ->
                        arrivals.map { arrival ->
                            TimetableEntry(
                                routeId = routeId,
                                arrivalTime = arrival.arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                            )
                        }
                    }
                    .sortedBy { it.arrivalTime }

                withContext(Dispatchers.Main) {
                    _timetable.value = timetableEntries
                }
            } catch (e: Exception) {
                Log.e("TransitViewModel", "Error loading timetable for stop $stopId on date $date", e)
                withContext(Dispatchers.Main) {
                    _timetable.value = emptyList()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isTimetableLoading.value = false
                }
            }
        }
    }

    // New method that filters by both stop and route
    fun loadTimetableForStop(stopId: String, routeId: String, date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            _isTimetableLoading.value = true
            _timetable.value = emptyList()

            try {
                isStaticDataReady.first { it }
                val activeServiceIds = getActiveServiceIdsForDate(date)
                val staticArrivals = repository.getAllStaticArrivalsForStop(stopId, activeServiceIds)

                // Convert routeId (short name) to actual GTFS route ID for filtering
                val transitData = _transitData.value
                val actualRouteId = transitData?.routes?.find { it.shortName == routeId }?.id

                val timetableEntries = if (actualRouteId != null) {
                    // Filter by actual GTFS route ID and convert to display format
                    staticArrivals
                        .filter { it.routeId == actualRouteId } // Filter by actual GTFS route ID
                        .map { arrival ->
                            TimetableEntry(
                                routeId = routeId, // Use short name for display
                                arrivalTime = arrival.arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                            )
                        }
                        .distinctBy { it.arrivalTime } // Remove duplicates by arrival time
                        .sortedBy { it.arrivalTime }
                } else {
                    // Fallback: filter by short name directly (only if actualRouteId lookup failed)
                    staticArrivals
                        .filter { arrival ->
                            // Check if the arrival's routeId matches the short name or if we can convert it
                            arrival.routeId == routeId ||
                            transitData?.routeIdToShortName?.get(arrival.routeId) == routeId
                        }
                        .map { arrival ->
                            TimetableEntry(
                                routeId = routeId, // Use provided short name for display
                                arrivalTime = arrival.arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                            )
                        }
                        .distinctBy { it.arrivalTime } // Remove duplicates by arrival time
                        .sortedBy { it.arrivalTime }
                }

                withContext(Dispatchers.Main) {
                    _timetable.value = timetableEntries
                }
            } catch (e: Exception) {
                Log.e("TransitViewModel", "Error loading timetable for stop $stopId, route $routeId on date $date", e)
                withContext(Dispatchers.Main) {
                    _timetable.value = emptyList()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isTimetableLoading.value = false
                }
            }
        }
    }

    // Date selection methods for timetable
    fun setTimetableDate(date: LocalDate) {
        _selectedTimetableDate.value = date
    }

    fun setTimetableDateToToday() {
        _selectedTimetableDate.value = LocalDate.now()
    }

    fun canNavigateToDate(date: LocalDate): Boolean {
        val today = LocalDate.now()
        val daysDifference = java.time.temporal.ChronoUnit.DAYS.between(today, date)
        return daysDifference >= -7 && daysDifference <= 7
    }

    private suspend fun getActiveServiceIdsForDate(date: LocalDate): List<String> {
        return withContext(Dispatchers.IO) {
            val activeServices = mutableSetOf<String>()
            val dayOfWeek = date.dayOfWeek
            val dateFormatted = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            // Handle late night services (midnight crossing)
            val yesterday = date.minusDays(1)
            val yesterdayDayOfWeek = yesterday.dayOfWeek
            val yesterdayDateFormatted = yesterday.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val isAfterMidnight = LocalTime.now().hour < 6 && date == LocalDate.now()

            try {
                val context = getApplication<Application>()
                openGtfsFile(context, "calendar.txt").bufferedReader().use { reader ->
                    val lines = reader.readLines()
                    if (lines.isEmpty()) return@use

                    val header = getHeaderIndexMap(lines[0])
                    val serviceIdIdx = header["service_id"] ?: return@use
                    val mondayIdx = header["monday"] ?: return@use
                    val tuesdayIdx = header["tuesday"] ?: return@use
                    val wednesdayIdx = header["wednesday"] ?: return@use
                    val thursdayIdx = header["thursday"] ?: return@use
                    val fridayIdx = header["friday"] ?: return@use
                    val saturdayIdx = header["saturday"] ?: return@use
                    val sundayIdx = header["sunday"] ?: return@use
                    val startDateIdx = header["start_date"] ?: return@use
                    val endDateIdx = header["end_date"] ?: return@use

                    val dayIndex = when (dayOfWeek) {
                        DayOfWeek.MONDAY -> mondayIdx
                        DayOfWeek.TUESDAY -> tuesdayIdx
                        DayOfWeek.WEDNESDAY -> wednesdayIdx
                        DayOfWeek.THURSDAY -> thursdayIdx
                        DayOfWeek.FRIDAY -> fridayIdx
                        DayOfWeek.SATURDAY -> saturdayIdx
                        DayOfWeek.SUNDAY -> sundayIdx
                    }

                    val yesterdayDayIndex = when (yesterdayDayOfWeek) {
                        DayOfWeek.MONDAY -> mondayIdx
                        DayOfWeek.TUESDAY -> tuesdayIdx
                        DayOfWeek.WEDNESDAY -> wednesdayIdx
                        DayOfWeek.THURSDAY -> thursdayIdx
                        DayOfWeek.FRIDAY -> fridayIdx
                        DayOfWeek.SATURDAY -> saturdayIdx
                        DayOfWeek.SUNDAY -> sundayIdx
                    }

                    for (i in 1 until lines.size) {
                        val tokens = lines[i].split(",")
                        if (tokens.size > kotlin.math.max(kotlin.math.max(dayIndex, yesterdayDayIndex), endDateIdx)) {
                            val serviceId = tokens[serviceIdIdx].trim('"')
                            val startDate = tokens[startDateIdx].trim('"')
                            val endDate = tokens[endDateIdx].trim('"')

                            // Add services for the selected date
                            if (dateFormatted >= startDate && dateFormatted <= endDate && tokens[dayIndex].trim('"') == "1") {
                                activeServices.add(serviceId)
                            }

                            // Add yesterday's services for midnight crossing (only for today if after midnight)
                            if (isAfterMidnight && yesterdayDateFormatted >= startDate && yesterdayDateFormatted <= endDate && tokens[yesterdayDayIndex].trim('"') == "1") {
                                activeServices.add(serviceId)
                                Log.d("GTFS_Load", "Added yesterday's service $serviceId for late night operation on $date")
                            }
                        }
                    }
                }

                Log.d("GTFS_Load", "Found ${activeServices.size} active services for date $date")

            } catch (e: Exception) {
                Log.e("GTFS_Load", "Error loading calendar for date $date", e)
            }

            activeServices.toList()
        }
    }

    private suspend fun loadStaticGtfsData(context: Context) {
        withContext(Dispatchers.IO) {
            _isStaticDataReady.value = false
            // Load static GTFS mapping data
            val tripToRoute = mutableMapOf<String, String>()
            val tripToService = mutableMapOf<String, String>()
            val tripToHeadsign = mutableMapOf<String, String>()

            try {
                openGtfsFile(context, "trips.txt").bufferedReader().use { reader ->
                    val lines = reader.readLines()
                    if (lines.isNotEmpty()) {
                        val header = getHeaderIndexMap(lines[0])
                        val routeIdIdx = header["route_id"] ?: return@use
                        val serviceIdIdx = header["service_id"] ?: return@use
                        val tripIdIdx = header["trip_id"] ?: return@use
                        val headsignIdx = header["trip_headsign"]

                        for (line in lines.drop(1)) {
                            val tokens = line.split(",")
                            if (tokens.size > maxOf(routeIdIdx, serviceIdIdx, tripIdIdx)) {
                                val tripId = tokens[tripIdIdx].trim('"')
                                tripToRoute[tripId] = tokens[routeIdIdx].trim('"')
                                tripToService[tripId] = tokens[serviceIdIdx].trim('"')
                                if (headsignIdx != null && tokens.size > headsignIdx) {
                                    val hs = tokens[headsignIdx].trim('"')
                                    if (hs.isNotBlank()) tripToHeadsign[tripId] = hs
                                }
                            }
                        }
                    }
                }
                _tripIdToRouteId.value = tripToRoute
                _tripIdToServiceId.value = tripToService
                _tripIdToHeadsign.value = tripToHeadsign
                Log.d("GTFS_Load", "Loaded ${tripToRoute.size} trips")
            } catch (e: Exception) {
                Log.e("GTFS_Load", "Error loading trips.txt", e)
            }

            _isStaticDataReady.value = true
            Log.d("GTFS_Load", "Static GTFS data is now ready")
        }
    }

    // Clean up resources
    override fun onCleared() {
        super.onCleared()
        Log.d("TransitViewModel", "ViewModel cleared - cleaning up resources")
    }
}

