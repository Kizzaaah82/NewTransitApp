package com.kiz.transitapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.kiz.transitapp.R
import com.kiz.transitapp.ui.navigation.Screen
import com.kiz.transitapp.ui.viewmodel.TransitViewModel
import com.kiz.transitapp.ui.viewmodel.BusStop
import com.kiz.transitapp.ui.viewmodel.Route
import com.kiz.transitapp.ui.utils.IconCache
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume

// Transit data structure that matches what's expected
data class TransitData(
    val stops: List<BusStop>,
    val routes: List<Route>,
    val polylines: Map<String, List<LatLng>>,
    val routeColors: Map<String, Color>,
    val stopsForRoute: Map<String, List<BusStop>>,
    val routeIdToShortName: Map<String, String>,
    val stopIdToBusStop: Map<String, BusStop>,
    val tripIdToOrderedStops: Map<String, List<String>>,
    val routeShortNameToTripIds: Map<String, List<String>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: TransitViewModel,
    initialStopId: String? = null
) {
    val context = LocalContext.current

    // Permission state
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        android.util.Log.d("MapScreen", "Permission result: hasLocationPermission = $hasLocationPermission")
    }

    // Request permission on first launch
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            android.util.Log.d("MapScreen", "Requesting location permissions...")
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Observe saved map state from ViewModel
    val savedCameraPosition by viewModel.savedCameraPosition.collectAsState()
    val savedSelectedRoute by viewModel.savedSelectedRoute.collectAsState()
    val savedShowLiveBuses by viewModel.savedShowLiveBuses.collectAsState()

    // Use saved state if available, otherwise use defaults
    var showLiveBuses by remember { mutableStateOf(savedShowLiveBuses) }
    var selectedRoute by remember { mutableStateOf(savedSelectedRoute) }

    // UI state
    var showDrawer by remember { mutableStateOf(false) }
    var selectedStop by remember { mutableStateOf<BusStop?>(null) }
    var selectedVehicleId by remember { mutableStateOf<String?>(null) }
    var shouldZoomToLocation by remember { mutableStateOf(false) } // NEW state for My Location button

    // Transit data - get actual data from ViewModel instead of empty data
    val transitData by viewModel.transitData.collectAsState()

    // Create a derived state that converts the ViewModel data to the MapScreen format
    // Use optimized transit data instead of the expensive derivedStateOf
    val optimizedTransitData by viewModel.optimizedTransitData.collectAsState()

    // Use optimized data when available, fallback to basic data
    val mapTransitData = remember(optimizedTransitData, transitData) {
        optimizedTransitData?.let { optimized ->
            // Convert OptimizedTransitData to MapScreen's expected TransitData format
            TransitData(
                stops = optimized.stops,
                routes = optimized.routes,
                polylines = optimized.routePolylinesOptimized,
                routeColors = optimized.routeShortNameToComputedColor,
                stopsForRoute = optimized.stopsForRoute,
                routeIdToShortName = optimized.routeIdToShortName,
                stopIdToBusStop = optimized.stopIdToBusStop,
                tripIdToOrderedStops = optimized.tripIdToOrderedStops,
                routeShortNameToTripIds = optimized.routeShortNameToTripIds
            )
        } ?: run {
            // Fallback - use basic transit data during initial loading
            transitData?.let { data ->
                TransitData(
                    stops = data.stops,
                    routes = data.routes,
                    polylines = emptyMap(), // Will be populated after optimization
                    routeColors = emptyMap(), // Will be populated after optimization
                    stopsForRoute = data.stopsForRoute,
                    routeIdToShortName = data.routeIdToShortName,
                    stopIdToBusStop = data.stopIdToBusStop,
                    tripIdToOrderedStops = data.tripIdToOrderedStops,
                    routeShortNameToTripIds = data.routeShortNameToTripIds
                )
            } ?: TransitData(
                stops = emptyList(),
                routes = emptyList(),
                polylines = emptyMap(),
                routeColors = emptyMap(),
                stopsForRoute = emptyMap(),
                routeIdToShortName = emptyMap(),
                stopIdToBusStop = emptyMap(),
                tripIdToOrderedStops = emptyMap(),
                routeShortNameToTripIds = emptyMap()
            )
        }
    }

    // Vehicle positions from ViewModel
    val vehiclePositions by viewModel.vehiclePositionFlow.collectAsState()
    val arrivalTimes by viewModel.stopArrivalTimes.collectAsState()

    // Service Alerts - NEW: Official transit agency alerts
    val serviceAlerts by viewModel.serviceAlerts.collectAsState()

    // Camera state
    val cameraPositionState = rememberCameraPositionState {
        position = savedCameraPosition ?: CameraPosition.fromLatLngZoom(LatLng(42.3149, -83.0364), 12f)
    }

    // Filtered vehicles based on selected route
    val filteredVehicles = remember(vehiclePositions, selectedRoute, showLiveBuses) {
        val result = if (!showLiveBuses) {
            emptyList()
        } else {
            // MIDNIGHT FIX: During late night hours ONLY, also show "Unknown" vehicles when filtering by route
            val currentHour = LocalTime.now().hour
            val currentMinute = LocalTime.now().minute

            // More precise midnight detection: Only consider 12:00am-4:59am as "late night"
            // Services starting at 5:30am should be treated as normal morning service
            val isLateNight = currentHour < 5 || (currentHour == 23 && currentMinute >= 30)

            vehiclePositions.filter { vehicle ->
                when {
                    selectedRoute == null -> true // Show all vehicles when no route selected
                    vehicle.routeId == selectedRoute -> true // Direct route match
                    // MIDNIGHT ENHANCEMENT: Only during actual late night hours (12am-5am), show "Unknown" vehicles
                    // when a specific route is selected, as they might actually be serving that route
                    isLateNight && vehicle.routeId == "Unknown" && selectedRoute != null -> {
                        android.util.Log.d("MapScreen", "LATE NIGHT: Including Unknown vehicle ${vehicle.vehicleId} when filtering for route $selectedRoute (hour: $currentHour)")
                        true
                    }
                    else -> false
                }
            }
        }
        android.util.Log.d("MapScreen", "Filtered vehicles: showLiveBuses=$showLiveBuses, total vehicles=${vehiclePositions.size}, filtered=${result.size}, selectedRoute=$selectedRoute")
        result
    }

    val selectedVehicle = remember(selectedVehicleId, filteredVehicles) {
        filteredVehicles.find { it.vehicleId == selectedVehicleId }
    }

    // Auto-refresh vehicle positions every 20 seconds when live buses are enabled
    LaunchedEffect(showLiveBuses) {
        if (showLiveBuses) {
            android.util.Log.d("MapScreen", "Starting vehicle position polling...")
            // Capture the route mapping once to avoid recomputation issues
            val routeMapping = mapTransitData.routeIdToShortName

            while (showLiveBuses) {
                android.util.Log.d("MapScreen", "Fetching vehicle positions at ${System.currentTimeMillis()}")

                if (routeMapping.isNotEmpty()) {
                    viewModel.fetchVehiclePositions(routeMapping)
                } else {
                    android.util.Log.d("MapScreen", "Route mapping is empty, skipping fetch")
                }

                kotlinx.coroutines.delay(20000) // 20 seconds - matches transit agency feed update frequency
            }
            android.util.Log.d("MapScreen", "Stopped vehicle position polling")
        }
    }

    // Handle initial stop selection
    LaunchedEffect(initialStopId, mapTransitData) {
        if (initialStopId != null) {
            val stop = mapTransitData.stopIdToBusStop[initialStopId]
            if (stop != null) {
                selectedStop = stop
                viewModel.getArrivalTimesForStop(initialStopId)
                // Animate to the stop
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(stop.location, 16f),
                    500
                )
            }
        }
    }

    // Auto-zoom to user's location if permission granted
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && savedCameraPosition == null) { // Only auto-zoom if no saved position
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Use suspending coroutine to get location
                    suspendCancellableCoroutine<android.location.Location?> { continuation ->
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            continuation.resume(location)
                        }.addOnFailureListener { _ ->
                            continuation.resume(null)
                        }
                    }?.let { location ->
                        val userLatLng = LatLng(location.latitude, location.longitude)
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(userLatLng, 16f),
                            1000
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MapScreen", "Error getting location: ${e.message}")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                compassEnabled = false
            ),
            onMapLoaded = {
                // Notify IconCache that Google Maps is ready
                try {
                    IconCache.getInstance().onMapReady()
                    android.util.Log.d("MapScreen", "Notified IconCache that Google Maps is ready")
                } catch (e: Exception) {
                    android.util.Log.e("MapScreen", "Error notifying IconCache of map ready state", e)
                }
            },
            onMapClick = { latLng ->
                // Close any open cards when tapping on the map
                selectedStop = null
                selectedVehicleId = null
            }
        ) {
            // Bus stop markers - OPTIMIZED to prevent recomposition on vehicle updates
            val stopsToShow = if (selectedRoute != null) {
                mapTransitData.stopsForRoute[selectedRoute] ?: emptyList()
            } else {
                emptyList() // Show no stops initially
            }

            // Get the route color for bus stops
            val routeColor = if (selectedRoute != null) {
                optimizedTransitData?.routeShortNameToComputedColor?.get(selectedRoute)
                    ?: mapTransitData.routeColors[selectedRoute]
                    ?: Color.Blue
            } else {
                Color.Blue
            }

            // OPTIMIZATION: Create colored bus stop icon once outside the loop
            val busStopIcon = remember(selectedRoute, routeColor) {
                createSimpleBusStopIcon(size = 40, color = routeColor)
            }

            // OPTIMIZATION: Use key() to prevent unnecessary recomposition of bus stop markers
            // when vehicle positions update
            key("bus_stops_$selectedRoute") {
                stopsToShow.forEach { stop ->
                    key(stop.id) { // Each stop gets its own stable key
                        Marker(
                            state = remember(stop.id) { MarkerState(position = stop.location) },
                            title = stop.name,
                            icon = busStopIcon,
                            onClick = {
                                selectedStop = stop
                                viewModel.getArrivalTimesForStop(stop.id)
                                true
                            }
                        )
                    }
                }
            }

            // Live bus markers - OPTIMIZED for smooth updates
            // OPTIMIZATION: Pre-cache all bus icons to avoid expensive operations during updates
            val uniqueRoutes = remember(filteredVehicles) {
                filteredVehicles.map { it.routeId }.toSet()
            }

            // Pre-cache icons for all routes that have vehicles - MOVED OUTSIDE vehicle loop
            val cachedBusIcons = remember(uniqueRoutes, optimizedTransitData) {
                android.util.Log.d("MapScreen", "Pre-caching bus icons for ${uniqueRoutes.size} routes: $uniqueRoutes")
                val iconMap = mutableMapOf<String, BitmapDescriptor?>()
                uniqueRoutes.forEach { routeId ->
                    val routeColor = optimizedTransitData?.routeShortNameToComputedColor?.get(routeId) ?: Color.Blue
                    try {
                        iconMap[routeId] = IconCache.getInstance().getBusIcon(routeId, routeColor)
                        android.util.Log.d("MapScreen", "Successfully cached icon for route $routeId")
                    } catch (e: Exception) {
                        android.util.Log.w("MapScreen", "Failed to cache icon for route $routeId: ${e.message}")
                        iconMap[routeId] = null
                    }
                }
                android.util.Log.d("MapScreen", "Icon caching complete: ${iconMap.size} routes cached")
                iconMap
            }

            // OPTIMIZATION: Separate key scope for vehicles to isolate from bus stops
            key("vehicles") {
                filteredVehicles.forEach { vehicle ->
                    val busIcon = cachedBusIcons[vehicle.routeId]
                    if (busIcon != null) {
                        // KEY OPTIMIZATION: Use vehicle.vehicleId as the key so Google Maps can
                        // intelligently update existing markers instead of recreating them
                        key(vehicle.vehicleId) {
                            // FIX: Include position in remember key to ensure markers update when vehicles move
                            // This forces recomposition when latitude/longitude changes
                            val markerState = remember(vehicle.vehicleId, vehicle.latitude, vehicle.longitude) {
                                MarkerState(
                                    position = LatLng(vehicle.latitude.toDouble(), vehicle.longitude.toDouble())
                                )
                            }

                            // Note: Position is set in remember block above, which now updates on position changes
                            // No need for separate position assignment as marker recreates with new position

                            Marker(
                                state = markerState,
                                title = "Bus ${vehicle.vehicleId} - Route ${vehicle.routeId}",
                                icon = busIcon,
                                rotation = vehicle.bearing ?: 0f,
                                onClick = {
                                    selectedVehicleId = vehicle.vehicleId
                                    true
                                }
                            )
                        }
                    } else {
                        android.util.Log.w("MapScreen", "No cached icon available for vehicle ${vehicle.vehicleId} on route ${vehicle.routeId}")
                    }
                }
            }

            // Route polylines - Enhanced with real-time deviation detection
            PolylineRenderer(
                selectedRoute = selectedRoute,
                optimizedTransitData = optimizedTransitData,
                mapTransitData = mapTransitData,
                viewModel = viewModel
            )
        }

        // Top bar with menu button
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showDrawer = true }
                ) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_hamburger1),
                        contentDescription = "Open menu",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }


        // NEW: My Location button - positioned on the right side
        if (hasLocationPermission) {
            FloatingActionButton(
                onClick = {
                    shouldZoomToLocation = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location"
                )
            }
        }

        // Handle My Location button click
        LaunchedEffect(shouldZoomToLocation) {
            if (shouldZoomToLocation) {
                try {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        suspendCancellableCoroutine<android.location.Location?> { continuation ->
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                continuation.resume(location)
                            }.addOnFailureListener { _ ->
                                continuation.resume(null)
                            }
                        }?.let { location ->
                            val userLatLng = LatLng(location.latitude, location.longitude)
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(userLatLng, 16f),
                                1000
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MapScreen", "Error getting location: ${e.message}")
                } finally {
                    shouldZoomToLocation = false // Reset the trigger
                }
            }
        }

        // Side drawer overlay
        if (showDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = { showDrawer = false })
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(320.dp),
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Map Controls",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Route Selection Section
                        Text(
                            text = "Select Route",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(16.dp))


                        // Route list - now using actual route data with names, always visible and scrollable
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Show loading state if routes are empty
                            if (mapTransitData.routes.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Loading routes...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Show all available routes
                                items(mapTransitData.routes.sortedBy { it.shortName }) { route ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedRoute = route.shortName
                                                showDrawer = false
                                                // Save the selected route state
                                                viewModel.saveMapState(
                                                    cameraPosition = cameraPositionState.position,
                                                    selectedRoute = route.shortName,
                                                    showLiveBuses = showLiveBuses
                                                )
                                            }
                                            .padding(vertical = 8.dp, horizontal = 8.dp)
                                            .background(
                                                if (selectedRoute == route.shortName) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedRoute == route.shortName,
                                            onClick = {
                                                selectedRoute = route.shortName
                                                showDrawer = false
                                                // Save the selected route state
                                                viewModel.saveMapState(
                                                    cameraPosition = cameraPositionState.position,
                                                    selectedRoute = route.shortName,
                                                    showLiveBuses = showLiveBuses
                                                )
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Route badge with proper color
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = mapTransitData.routeColors[route.shortName] ?: MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = route.shortName,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = route.longName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                // Show warning icon if this route has active alerts
                                                if (viewModel.hasActiveAlerts(route.shortName)) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = "Service alert",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                            // Show alert text if there are active alerts
                                            val routeAlerts = viewModel.getAlertsForRoute(route.shortName)
                                            if (routeAlerts.isNotEmpty()) {
                                                Text(
                                                    text = "Route ${route.shortName} • ${routeAlerts.first().headerText}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            } else {
                                                Text(
                                                    text = "Route ${route.shortName}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Show Live Buses Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show Live Buses",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = showLiveBuses,
                                onCheckedChange = {
                                    showLiveBuses = it
                                    if (it) {
                                        viewModel.fetchVehiclePositions(mapTransitData.routeIdToShortName)
                                    }
                                }
                            )
                        }

                        if (showLiveBuses) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedRoute != null) {
                                    "Showing buses for Route $selectedRoute"
                                } else {
                                    "Showing buses for all routes"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Service Alerts Section - NEW
                        selectedRoute?.let { routeId ->
                            val routeAlerts = viewModel.getAlertsForRoute(routeId)
                            if (routeAlerts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(16.dp))

                                // Alert header
                                Text(
                                    text = "Service Alerts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Display each alert
                                routeAlerts.forEach { alert ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            // Alert header with icon
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = alert.headerText,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }

                                            // Alert description
                                            if (alert.descriptionText.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = alert.descriptionText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            // Alert type badge
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Alert type
                                                Surface(
                                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = when (alert.alertType) {
                                                            com.kiz.transitapp.ui.viewmodel.AlertType.DETOUR -> "Detour"
                                                            com.kiz.transitapp.ui.viewmodel.AlertType.DELAY -> "Delay"
                                                            com.kiz.transitapp.ui.viewmodel.AlertType.STOP_CLOSED -> "Stop Closed"
                                                            com.kiz.transitapp.ui.viewmodel.AlertType.STOP_MOVED -> "Stop Moved"
                                                            com.kiz.transitapp.ui.viewmodel.AlertType.SERVICE_CHANGE -> "Service Change"
                                                            else -> "Alert"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Stop Info Card
        selectedStop?.let { stop ->
            StopInfoCard(
                stop = stop,
                arrivalTimes = arrivalTimes,
                onTimetableClick = {
                    viewModel.saveMapState(
                        cameraPosition = cameraPositionState.position,
                        selectedRoute = selectedRoute,
                        showLiveBuses = showLiveBuses
                    )
                    // Use selectedRoute or find the first route for this stop if none selected
                    val routeToUse = selectedRoute ?: transitData?.stopsForRoute?.entries?.find { (_, stops) ->
                        stops.any { it.id == stop.id }
                    }?.key ?: ""

                    if (routeToUse.isNotEmpty()) {
                        navController.navigate(Screen.Timetable.createRoute(stop.id, routeToUse))
                    }
                },
                viewModel = viewModel,
                selectedRoute = selectedRoute,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }

        // Selected Bus Info Card
        selectedVehicle?.let { vehicle ->
            BusInfoCard(
                vehicle = vehicle,
                routeColor = mapTransitData.routeColors[vehicle.routeId] ?: MaterialTheme.colorScheme.primary,
                onDismiss = { selectedVehicleId = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

// Create a simple programmatic bus stop icon without resource loading
private fun createSimpleBusStopIcon(size: Int = 40, color: Color = Color.Blue): BitmapDescriptor? {
    return try {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Create a colored circle with thick black border - clean transit design
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }

        // Calculate proportional stroke width based on icon size - thicker border
        val strokeWidth = (size * 0.25f).coerceAtLeast(4f)
        val circleRadius = size / 2f - strokeWidth / 2f

        // Fill circle with route color (inner color)
        paint.apply {
            this.color = color.toArgb()
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, circleRadius, paint)

        // Thick black outer border for better contrast and professional look
        paint.apply {
            this.color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        canvas.drawCircle(size / 2f, size / 2f, circleRadius, paint)

        BitmapDescriptorFactory.fromBitmap(bitmap)
    } catch (e: Exception) {
        android.util.Log.e("MapScreen", "Failed to create simple bus stop icon with size $size: ${e.message}")
        null
    }
}

@Composable
fun StopInfoCard(
    stop: BusStop,
    arrivalTimes: List<com.kiz.transitapp.ui.viewmodel.StopArrivalTime>,
    onTimetableClick: () -> Unit,
    viewModel: TransitViewModel,
    selectedRoute: String?,
    modifier: Modifier = Modifier
) {
    // Get favorite status from ViewModel
    val favoriteStopRoutes by viewModel.favoriteStopRoutes.collectAsState()

    // Check if this stop-route combination is favorited
    val isFavorite = if (selectedRoute != null) {
        favoriteStopRoutes.any { it.stopId == stop.id && it.routeId == selectedRoute }
    } else {
        // If no route selected, check if any route for this stop is favorited
        favoriteStopRoutes.any { it.stopId == stop.id }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Stop ${stop.code ?: stop.id}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Only heart icon - removed the close button
                IconButton(
                    onClick = {
                        if (selectedRoute != null) {
                            // Toggle favorite for specific route
                            viewModel.toggleFavoriteStop(stop.id, selectedRoute)
                        } else if (arrivalTimes.isNotEmpty()) {
                            // If no route selected but we have arrivals, use first route
                            viewModel.toggleFavoriteStop(stop.id, arrivalTimes.first().routeId)
                        } else {
                            // Fallback - use the old method without route
                            viewModel.toggleFavoriteStop(stop.id)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (arrivalTimes.isEmpty()) {
                Text(
                    text = "No upcoming arrivals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(arrivalTimes.take(5)) { arrival ->
                        ArrivalTimeItem(arrival = arrival, viewModel = viewModel)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = onTimetableClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Timetable")
                }
            }
        }
    }
}

@Composable
fun ArrivalTimeItem(arrival: com.kiz.transitapp.ui.viewmodel.StopArrivalTime, viewModel: TransitViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Route ${arrival.routeId}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (arrival.isRealTime) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Use the standardized component instead of manual calculations
        com.kiz.transitapp.ui.components.ArrivalTimeDisplay(
            arrival = arrival,
            viewModel = viewModel
        )
    }
}

@Composable
fun BusInfoCard(
    vehicle: com.kiz.transitapp.ui.viewmodel.VehiclePositionInfo,
    routeColor: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(routeColor, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Vehicle ${vehicle.vehicleId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow(icon = Icons.Default.DirectionsBus, label = "Vehicle ID", value = vehicle.vehicleId)
                InfoRow(icon = Icons.Default.Info, label = "Route", value = vehicle.routeId)

                vehicle.occupancyStatus?.let { occ ->
                    InfoRow(icon = Icons.Default.Info, label = "Occupancy", value = formatOccupancy(occ))
                }

                vehicle.speedMps?.let { mps ->
                    val kmh = mps * 3.6f
                    InfoRow(icon = Icons.Default.Speed, label = "Speed", value = "${kmh.toInt()} km/h")
                }

                vehicle.bearing?.let { brg ->
                    val dir = bearingToCardinal(brg)
                    InfoRow(icon = Icons.Default.Explore, label = "Heading", value = "${brg.toInt()}° ($dir)")
                }

                vehicle.timestamp?.let { ts ->
                    val text = formatTimestamp(ts)
                    InfoRow(icon = Icons.Default.Schedule, label = "Updated", value = text)
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text = "$label: ", fontWeight = FontWeight.SemiBold)
        Text(text = value)
    }
}

private fun formatOccupancy(status: String): String {
    return when (status) {
        "EMPTY" -> "Empty"
        "MANY_SEATS_AVAILABLE" -> "Many seats available"
        "FEW_SEATS_AVAILABLE" -> "Few seats available"
        "STANDING_ROOM_ONLY" -> "Standing room only"
        "CRUSHED_STANDING_ROOM_ONLY" -> "Crowded (crushed standing)"
        "FULL" -> "Full"
        "NOT_ACCEPTING_PASSENGERS" -> "Not accepting passengers"
        else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}

private fun bearingToCardinal(bearing: Float): String {
    val dirs = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val idx = ((bearing % 360 + 360) % 360) / 22.5f
    return dirs[idx.toInt() % dirs.size]
}

private fun formatTimestamp(epochSeconds: Long): String {
    val zone = ZoneId.of("America/Toronto")
    val instant = Instant.ofEpochSecond(epochSeconds)
    val time = java.time.ZonedDateTime.ofInstant(instant, zone)
    val now = java.time.ZonedDateTime.now(zone)
    val minutesAgo = ChronoUnit.MINUTES.between(time, now)
    val relative = when {
        minutesAgo <= 0 -> "Just now"
        minutesAgo == 1L -> "1 min ago"
        minutesAgo < 60 -> "$minutesAgo mins ago"
        else -> time.format(DateTimeFormatter.ofPattern("h:mm a"))
    }
    return relative
}

@Composable
fun PolylineRenderer(
    selectedRoute: String?,
    optimizedTransitData: com.kiz.transitapp.ui.viewmodel.OptimizedTransitData?,
    mapTransitData: TransitData,
    viewModel: TransitViewModel
) {
    // Simplified approach: always show normal route polylines
    // Detour detection is used only for warning indicators, not for drawing paths
    selectedRoute?.let { route ->
        // Get route color
        val routeColor = remember(route, optimizedTransitData, mapTransitData) {
            optimizedTransitData?.routeShortNameToComputedColor?.get(route)
                ?: mapTransitData.routeColors[route]
                ?: Color.Blue
        }

        // Show ALL shape variations for complete route coverage
        val allVariations = remember(route, optimizedTransitData) {
            optimizedTransitData?.routePolylinesAllVariations?.get(route) ?: emptyList()
        }

        if (allVariations.isNotEmpty()) {
            // Render ALL shape variations for this route
            allVariations.forEachIndexed { index, polylinePoints ->
                if (polylinePoints.isNotEmpty() && polylinePoints.size >= 2) {
                    // Process points (simplify only if necessary)
                    val processedPoints = if (polylinePoints.size > 1000) {
                        // Simplify very large polylines
                        val step = (polylinePoints.size / 800).coerceAtLeast(1)
                        val simplified = mutableListOf<LatLng>()
                        simplified.add(polylinePoints.first())
                        for (i in step until polylinePoints.size step step) {
                            simplified.add(polylinePoints[i])
                        }
                        if (simplified.lastOrNull() != polylinePoints.last()) {
                            simplified.add(polylinePoints.last())
                        }
                        simplified
                    } else {
                        polylinePoints
                    }

                    // Render each variation with a unique key
                    key("polyline_${route}_var_$index") {
                        Polyline(
                            points = processedPoints,
                            color = routeColor,
                            width = 12f,
                            pattern = null,
                            geodesic = true,
                            clickable = false,
                            zIndex = 1f
                        )
                    }
                }
            }

            android.util.Log.d("PolylineRenderer", "Rendered ${allVariations.size} polyline variations for route $route (complete coverage)")
        } else {
            // Fallback to single polyline if no variations available
            val singlePolyline = optimizedTransitData?.routePolylinesOptimized?.get(route)
                ?: mapTransitData.polylines[route]

            singlePolyline?.let { polylinePoints ->
                if (polylinePoints.isNotEmpty() && polylinePoints.size >= 2) {
                    android.util.Log.d("PolylineRenderer", "Fallback: Rendering single polyline for route $route with ${polylinePoints.size} points")

                    key("polyline_fallback_$route") {
                        Polyline(
                            points = polylinePoints,
                            color = routeColor,
                            width = 12f,
                            pattern = null,
                            geodesic = true,
                            clickable = false,
                            zIndex = 1f
                        )
                    }
                }
            }
        }
    }
}
