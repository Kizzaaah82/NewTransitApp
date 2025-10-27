package com.kiz.transitapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kiz.transitapp.ui.navigation.Screen
import com.kiz.transitapp.ui.viewmodel.TransitViewModel
import com.kiz.transitapp.ui.viewmodel.BusStop
import com.kiz.transitapp.ui.components.RoastManager
import java.text.SimpleDateFormat
import java.util.*

// Add missing helper functions
private fun getCurrentTime(): String {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return timeFormat.format(Date())
}

private fun getCurrentDate(): String {
    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return dateFormat.format(Date())
}

// Get appropriate weather icon based on OpenWeatherMap icon code
private fun getWeatherIcon(iconCode: String): ImageVector {
    return when {
        iconCode.startsWith("01") -> Icons.Filled.WbSunny // Clear sky
        iconCode.startsWith("02") -> Icons.Filled.CloudQueue // Few clouds
        iconCode.startsWith("03") || iconCode.startsWith("04") -> Icons.Filled.Cloud // Clouds
        iconCode.startsWith("09") || iconCode.startsWith("10") -> Icons.Filled.Grain // Rain
        iconCode.startsWith("11") -> Icons.Filled.Thunderstorm // Thunderstorm
        iconCode.startsWith("13") -> Icons.Filled.AcUnit // Snow
        iconCode.startsWith("50") -> Icons.Filled.Cloud // Mist/fog
        else -> Icons.Filled.Cloud // Default
    }
}

// Get appropriate time icon based on current hour
private fun getTimeIcon(): ImageVector {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)

    return when (hour) {
        in 6..11 -> Icons.Filled.WbTwilight // Morning (sunrise)
        in 12..17 -> Icons.Filled.WbSunny // Afternoon (sun)
        in 18..20 -> Icons.Filled.WbTwilight // Evening (sunset)
        else -> Icons.Filled.Nightlight // Night (moon)
    }
}

// Get contextual color for time icon
@Composable
private fun getTimeIconColor(): Color {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)

    return when (hour) {
        in 6..11 -> Color(0xFFFFA726) // Morning orange
        in 12..17 -> Color(0xFFFFD54F) // Afternoon yellow
        in 18..20 -> Color(0xFFFF7043) // Evening orange-red
        else -> Color(0xFF9FA8DA) // Night purple-blue
    }
}

// Get contextual color for weather icon
@Composable
private fun getWeatherIconColor(iconCode: String): Color {
    return when {
        iconCode.startsWith("01") -> Color(0xFFFFD54F) // Clear sky - yellow
        iconCode.startsWith("02") -> Color(0xFFB0BEC5) // Few clouds - light gray
        iconCode.startsWith("03") || iconCode.startsWith("04") -> Color(0xFF90A4AE) // Clouds - gray
        iconCode.startsWith("09") || iconCode.startsWith("10") -> Color(0xFF64B5F6) // Rain - blue
        iconCode.startsWith("11") -> Color(0xFF9575CD) // Thunderstorm - purple
        iconCode.startsWith("13") -> Color(0xFFE1F5FE) // Snow - light blue/white
        iconCode.startsWith("50") -> Color(0xFFCFD8DC) // Mist - light gray
        else -> Color(0xFF90A4AE) // Default - gray
    }
}

@Composable
fun HomeScreen(
    viewModel: TransitViewModel,
    navController: NavController
) {
    val favoriteStopsWithArrivals by viewModel.favoriteStopsWithArrivals.collectAsState()
    val favoriteStopRoutes by viewModel.favoriteStopRoutes.collectAsState() // Add this to show cards immediately
    val transitDataState by viewModel.transitData.collectAsState() // Move this to the top level

    // OPTIMIZED: Single loading effect with no redundant calls
    LaunchedEffect(Unit) {
        // Initial load
        viewModel.fetchTripUpdates()
        viewModel.loadFavoritesWithArrivals()

        // Periodic refresh every 15 seconds
        var failureCount = 0
        while (true) {
            kotlinx.coroutines.delay(15000L) // 15 seconds

            try {
                viewModel.fetchTripUpdates()
                // Only refresh if we have favorites
                if (viewModel.favoriteStops.value.isNotEmpty()) {
                    viewModel.loadFavoritesWithArrivals()
                }
                failureCount = 0
            } catch (_: Exception) {
                failureCount = minOf(failureCount + 1, 4)
            }
        }
    }

    // Only reload favorites when they are actually added/removed (not on every state change)
    val favoriteStopsCount by viewModel.favoriteStops.collectAsState()
    LaunchedEffect(favoriteStopsCount.size) {
        // Only trigger if favorites exist and this isn't the initial load
        if (favoriteStopsCount.isNotEmpty()) {
            kotlinx.coroutines.delay(1000) // Debounce
            viewModel.loadFavoritesWithArrivals()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Weather card pinned to top (not scrolling)
        WeatherCard(
            viewModel = viewModel,
            modifier = Modifier.padding(16.dp)
        )

        // Scrollable content below
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Roast card
            item {
                RoastCard()
            }

            item {
                Text(
                    text = "Favourite Stops",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Show cards immediately if we have favorite stop-route pairs, even without arrivals
            if (favoriteStopRoutes.isEmpty()) {
                item {
                    FavoriteStopsEmptyState()
                }
            } else {
                // Show cards for all favorite stop-route pairs immediately
                items(favoriteStopRoutes.toList()) { favoriteStopRoute ->
                    val actualStop = transitDataState?.stopIdToBusStop?.get(favoriteStopRoute.stopId)

                    // Use actual stop data if available, otherwise create fallback
                    val stop = actualStop ?: BusStop(
                        id = favoriteStopRoute.stopId,
                        code = null,
                        name = "Stop ${favoriteStopRoute.stopId}", // Fallback name
                        location = com.google.android.gms.maps.model.LatLng(0.0, 0.0) // Default location
                    )

                    // Get route color from transit data
                    val routeColor = transitDataState?.routeShortNameToColor?.get(favoriteStopRoute.routeId)?.let { colorHex ->
                        try {
                            // Parse hex color string manually to avoid import issues
                            val cleanHex = if (colorHex.startsWith("#")) colorHex.substring(1) else colorHex
                            val colorInt = cleanHex.toLong(16).toInt()
                            Color(colorInt or 0xFF000000.toInt()) // Ensure alpha is FF
                        } catch (_: Exception) {
                            Color.Blue // Fallback color
                        }
                    } ?: Color.Blue

                    // Find matching arrivals for this specific stop-route combination
                    val matchingArrivals = favoriteStopsWithArrivals.find {
                        it.stopId == favoriteStopRoute.stopId && it.routeId == favoriteStopRoute.routeId
                    }?.arrivals ?: emptyList()

                    FavoriteStopCard(
                        stop = stop,
                        arrivals = matchingArrivals, // Will be empty list initially, then populate when loaded
                        routeId = favoriteStopRoute.routeId,
                        routeColor = routeColor,
                        viewModel = viewModel,
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
fun FavoriteStopsEmptyState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No favorite stops yet",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap the heart on any bus stop to add it here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherCard(viewModel: TransitViewModel, modifier: Modifier = Modifier) {
    val weatherData by viewModel.weatherData.collectAsState()
    val isWeatherLoading by viewModel.isWeatherLoading.collectAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (weatherData != null) {
                // Show actual weather data
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = getTimeIcon(),
                                    contentDescription = "Time",
                                    tint = getTimeIconColor(),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = getCurrentTime(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Event,
                                    contentDescription = "Date",
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = getCurrentDate(),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = getWeatherIcon(weatherData!!.icon),
                                    contentDescription = "Weather",
                                    tint = getWeatherIconColor(weatherData!!.icon),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${weatherData!!.temperature}°C",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "Feels ${weatherData!!.feelsLike}°C",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = weatherData!!.description,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "${weatherData!!.humidity}% humidity",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Show elegant time/date card without weather
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = getTimeIcon(),
                                    contentDescription = "Time",
                                    tint = getTimeIconColor(),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = getCurrentTime(),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Event,
                                    contentDescription = "Date",
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = getCurrentDate(),
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = "Location",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Windsor",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "Ontario",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isWeatherLoading) {
                        Text(
                            text = "Loading weather...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            text = "Weather service connecting...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteStopCard(
    stop: BusStop,
    arrivals: List<com.kiz.transitapp.ui.viewmodel.StopArrivalTime>,
    routeId: String, // Add route ID parameter
    routeColor: Color, // Add route color parameter
    viewModel: TransitViewModel,
    navController: NavController
) {
    // Check if this specific stop-route combination is favorited
    val favoriteStopRoutes by viewModel.favoriteStopRoutes.collectAsState()
    val isFavorite = favoriteStopRoutes.any { it.stopId == stop.id && it.routeId == routeId }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Route color strip on the left edge
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(routeColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header with stop name and heart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stop.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Stop ${stop.code ?: stop.id} • Route $routeId", // Show stop code if available, fallback to stop ID
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleFavoriteStop(stop.id, routeId) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Real-time arrival information with simplified display
                if (arrivals.isNotEmpty()) {
                    // Show first arrival as primary, next 2 as secondary
                    val primaryArrival = arrivals.first()
                    val nextArrivals = arrivals.drop(1).take(2)

                    com.kiz.transitapp.ui.components.ArrivalTimeDisplay(
                        arrival = primaryArrival,
                        viewModel = viewModel,
                        nextArrivals = nextArrivals
                    )
                } else {
                    Text(
                        text = "No upcoming arrivals",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Timetable button
                Button(
                    onClick = {
                        // Use the routeId parameter from this function and stopId from stop parameter
                        navController.navigate(Screen.Timetable.createRoute(stop.id, routeId))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(
                        text = "View Timetable",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun RoastCard() {
    // Generate a seed based on current timestamp to get a different roast each app open
    // Using System.currentTimeMillis() ensures it changes every time the app is opened
    val roastSeed = remember { System.currentTimeMillis() }
    val roast = remember(roastSeed) {
        RoastManager.randomRoast(seed = roastSeed)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (roast.category) {
                    RoastManager.Category.TRANSIT -> "🚌 Transit Wisdom"
                    RoastManager.Category.WEATHER -> "🌦️ Weather Check"
                    RoastManager.Category.TIME_OF_DAY -> "⏰ Time Check"
                    RoastManager.Category.DEVICE -> "📱 Device Status"
                    RoastManager.Category.PERSONAL -> "💭 Personal Update"
                    RoastManager.Category.PHILOSOPHY -> "🤔 Deep Thoughts"
                    RoastManager.Category.VET_TECH -> "🐾 Vet Tech Life"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = roast.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )
        }
    }
}


