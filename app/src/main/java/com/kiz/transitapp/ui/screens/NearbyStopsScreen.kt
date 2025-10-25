package com.kiz.transitapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.kiz.transitapp.ui.util.haversineDistance
import com.kiz.transitapp.ui.viewmodel.TransitViewModel
import com.kiz.transitapp.ui.viewmodel.BusStop
import com.kiz.transitapp.ui.navigation.Screen
import com.kiz.transitapp.ui.theme.PastelRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class NearbyStop(
    val stop: BusStop,
    val distance: Double, // in meters
    val routes: List<RouteInfo>
)

data class RouteInfo(
    val id: String,
    val shortName: String,
    val color: Color
)

// Simple TransitData structure for NearbyStopsScreen
data class NearbyTransitData(
    val stops: List<BusStop>,
    val stopsForRoute: Map<String, List<BusStop>>,
    val routeColors: Map<String, Color>,
    val routeIdToShortName: Map<String, String>
)

// Remember function for nearby transit data - now gets actual data from ViewModel
@Composable
fun rememberNearbyTransitData(viewModel: TransitViewModel): NearbyTransitData? {
    val transitData by viewModel.transitData.collectAsState()
    val optimizedTransitData by viewModel.optimizedTransitData.collectAsState()

    return remember(transitData, optimizedTransitData) {
        // Use optimized data if available, otherwise fall back to regular transit data
        val optimized = optimizedTransitData
        val regular = transitData

        when {
            optimized != null -> {
                NearbyTransitData(
                    stops = optimized.stops,
                    stopsForRoute = optimized.stopsForRoute,
                    routeColors = optimized.routeShortNameToComputedColor,
                    routeIdToShortName = optimized.routeIdToShortName
                )
            }
            regular != null -> {
                // Convert hex colors to Color objects for regular transit data
                val routeColors = regular.routeShortNameToColor.mapValues { (_, colorHex) ->
                    try {
                        Color(colorHex.toColorInt())
                    } catch (_: Exception) {
                        Color(0xFF0066CC) // Default blue
                    }
                }

                NearbyTransitData(
                    stops = regular.stops,
                    stopsForRoute = regular.stopsForRoute,
                    routeColors = routeColors,
                    routeIdToShortName = regular.routeIdToShortName
                )
            }
            else -> null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyStopsScreen(
    viewModel: TransitViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var currentLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var nearbyStops by remember { mutableStateOf<List<NearbyStop>>(emptyList()) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var isLoadingStops by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Use the renamed function to avoid conflicts
    val transitData = rememberNearbyTransitData(viewModel)
    val favoriteStops by viewModel.favoriteStops.collectAsState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            // Trigger location fetch
        } else {
            errorMessage = "Location permission is required to find nearby stops"
        }
    }

    // Function to get current location using suspendCoroutine
    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        return suspendCoroutine { continuation ->
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            continuation.resume(Pair(location.latitude, location.longitude))
                        } else {
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            } catch (_: Exception) {
                continuation.resume(null)
            }
        }
    }

    // Move the actual computation to a LaunchedEffect for proper scope management
    LaunchedEffect(currentLocation, transitData) {
        currentLocation?.let { (lat, lon) ->
            transitData?.let { data: NearbyTransitData ->
                isLoadingStops = true
                errorMessage = null

                try {
                    // Use ViewModel's optimized getNearbyStops function instead of manual calculation
                    val nearbyBusStops = viewModel.getNearbyStops(lat, lon, 500.0) // 500m radius

                    // Build route info for each stop
                    val nearby = nearbyBusStops.map { stop ->
                        // Calculate distance for display
                        val distance = haversineDistance(lat, lon, stop.location.latitude, stop.location.longitude)

                        // Get routes for this stop
                        val stopRoutes = data.stopsForRoute.entries
                            .filter { (_, stops) -> stops.any { it.id == stop.id } }
                            .map { (routeId, _) ->
                                RouteInfo(
                                    id = routeId,
                                    shortName = data.routeIdToShortName[routeId] ?: routeId,
                                    color = data.routeColors[routeId] ?: Color.Gray
                                )
                            }

                        NearbyStop(
                            stop = stop,
                            distance = distance,
                            routes = stopRoutes
                        )
                    }.filter { it.routes.isNotEmpty() } // Only show stops that have routes

                    nearbyStops = nearby
                } catch (e: Exception) {
                    errorMessage = "Error finding nearby stops: ${e.message}"
                    android.util.Log.e("NearbyStops", "Error finding nearby stops", e)
                } finally {
                    isLoadingStops = false
                }
            }
        }
    }

    // Get location when screen loads
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && currentLocation == null) {
            isLoadingLocation = true
            errorMessage = null

            try {
                val location = getCurrentLocation()
                if (location != null) {
                    currentLocation = location
                } else {
                    errorMessage = "Could not get your location. Please try again."
                }
            } catch (e: Exception) {
                errorMessage = "Error getting location: ${e.message}"
            } finally {
                isLoadingLocation = false
            }
        }
    }

    // Simplified refresh logic - only refresh real-time data, not the static data
    LaunchedEffect(Unit) {
        // Initial fetch of real-time data
        viewModel.fetchTripUpdates()

        // Periodic refresh of only real-time data every 30 seconds
        while (true) {
            kotlinx.coroutines.delay(30000)
            try {
                viewModel.fetchTripUpdates()
            } catch (e: Exception) {
                // Log error but don't crash the app
                android.util.Log.e("NearbyStops", "Error refreshing real-time data", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby Stops") },
                actions = {
                    IconButton(onClick = {
                        if (hasLocationPermission) {
                            currentLocation = null // Force a refresh
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }) {
                        Icon(
                            imageVector = if (hasLocationPermission) Icons.Default.MyLocation else Icons.Default.LocationOff,
                            contentDescription = "Refresh Location"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Show loading state while transit data is being loaded
            if (transitData == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Loading transit data...", modifier = Modifier.padding(top = 16.dp))
                    }
                }
            } else if (isLoadingLocation) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Getting your location...", modifier = Modifier.padding(top = 16.dp))
                    }
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "An unknown error occurred.",
                            modifier = Modifier.padding(16.dp)
                        )
                        if (!hasLocationPermission) {
                            Button(
                                onClick = {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Grant Location Permission")
                            }
                        }
                    }
                }
            } else if (isLoadingStops) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Finding nearby stops...", modifier = Modifier.padding(top = 16.dp))
                    }
                }
            } else if (nearbyStops.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No bus stops found within 500m.")
                        Button(
                            onClick = {
                                currentLocation = null // Trigger refresh
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Refresh Location")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(nearbyStops) { nearbyStop ->
                        NearbyStopCard(
                            nearbyStop = nearbyStop,
                            isFavorite = favoriteStops.contains(nearbyStop.stop.id),
                            onToggleFavorite = { viewModel.toggleFavoriteStop(nearbyStop.stop.id) },
                            onClick = {
                                // Navigate to the timetable screen for this stop
                                // Find the first route that serves this stop for navigation
                                val transitData = viewModel.transitData.value
                                val firstRoute = transitData?.stopsForRoute?.entries?.find { (_, stops) ->
                                    stops.any { it.id == nearbyStop.stop.id }
                                }?.key

                                if (firstRoute != null) {
                                    navController.navigate(Screen.Timetable.createRoute(nearbyStop.stop.id, firstRoute))
                                }
                            },
                            viewModel = viewModel // Pass viewModel to card
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NearbyStopCard(
    nearbyStop: NearbyStop,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    viewModel: TransitViewModel // Add viewModel parameter
) {
    // Fetch real-time arrivals for this stop
    var arrivals by remember { mutableStateOf<List<com.kiz.transitapp.ui.viewmodel.StopArrivalTime>>(emptyList()) }
    var isLoadingArrivals by remember { mutableStateOf(false) }

    LaunchedEffect(nearbyStop.stop.id) {
        isLoadingArrivals = true
        try {
            arrivals = viewModel.getMergedArrivalsForStop(nearbyStop.stop.id)
        } catch (_: Exception) {
            // Handle error silently, arrivals will remain empty
            arrivals = emptyList()
        } finally {
            isLoadingArrivals = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nearbyStop.stop.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${nearbyStop.distance.roundToInt()}m away • Stop #${nearbyStop.stop.code ?: nearbyStop.stop.id}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) PastelRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display routes serving this stop
            if (nearbyStop.routes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    nearbyStop.routes.take(6).forEach { route -> // Limit displayed routes
                        Box(
                            modifier = Modifier
                                .background(
                                    color = route.color,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = route.shortName,
                                color = getContrastingTextColor(route.color),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (nearbyStop.routes.size > 6) {
                        Text(
                            text = "+${nearbyStop.routes.size - 6} more",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time arrival information
            if (isLoadingArrivals) {
                Text(
                    text = "Loading arrivals...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (arrivals.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    arrivals.take(3).forEach { arrival ->
                        if (arrival.isRealTime) {
                            // Real-time arrival with delay information
                            val currentDate = java.time.LocalDate.now()
                            val windsorTimeZone = java.time.ZoneId.of("America/Toronto")
                            val now = java.time.ZonedDateTime.now(windsorTimeZone)

                            val todayArrival = java.time.ZonedDateTime.of(currentDate, arrival.arrivalTime, windsorTimeZone)
                            val tomorrowArrival = todayArrival.plusDays(1)

                            val actualArrival = if (todayArrival.isAfter(now)) {
                                todayArrival
                            } else {
                                tomorrowArrival
                            }

                            val minutesUntil = java.time.Duration.between(now, actualArrival).toMinutes()

                            val displayText = when {
                                minutesUntil < 0 -> "Due"
                                minutesUntil == 0L -> "Due"
                                minutesUntil == 1L -> "1 min"
                                minutesUntil < 60 -> "${minutesUntil.toInt()} mins"
                                else -> actualArrival.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                            }

                            val delayText = when {
                                arrival.delaySeconds > 60 -> " (${arrival.delaySeconds / 60} min late)"
                                arrival.delaySeconds < -60 -> " (${(-arrival.delaySeconds) / 60} min early)"
                                arrival.delaySeconds > 30 -> " (late)"
                                arrival.delaySeconds < -30 -> " (early)"
                                else -> " (on time)"
                            }

                            Text(
                                text = "$displayText$delayText",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    arrival.delaySeconds > 60 -> MaterialTheme.colorScheme.error
                                    arrival.delaySeconds < -60 -> Color(0xFF4CAF50)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        } else {
                            // Static arrival time
                            val formattedTime = arrival.arrivalTime.format(
                                java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                            )
                            Text(
                                text = formattedTime,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No upcoming arrivals",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Add helper function to determine readable text color
private fun getContrastingTextColor(backgroundColor: Color): Color {
    // Calculate luminance of the background color
    val luminance = backgroundColor.luminance()
    
    // If background is light (luminance > 0.5), use dark text
    // If background is dark (luminance <= 0.5), use light text
    return if (luminance > 0.5) Color.Black else Color.White
}
