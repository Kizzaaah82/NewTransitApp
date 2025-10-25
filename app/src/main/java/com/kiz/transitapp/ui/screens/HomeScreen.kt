package com.kiz.transitapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kiz.transitapp.ui.navigation.Screen
import com.kiz.transitapp.ui.viewmodel.TransitViewModel
import com.kiz.transitapp.ui.viewmodel.BusStop
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
                            Text(
                                text = getCurrentTime(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = getCurrentDate(),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${weatherData!!.temperature}°C",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
                            Text(
                                text = getCurrentTime(),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = getCurrentDate(),
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "Windsor",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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

                // Real-time arrival information with limited display
                if (arrivals.isNotEmpty()) {
                    // More intelligent service hours check - if there are static arrivals scheduled
                    // for the next few hours, then real-time data should be valid
                    val currentTime = java.time.LocalTime.now()
                    val nextFewHours = currentTime.plusHours(3) // Check next 3 hours for scheduled service

                    val hasScheduledService = arrivals.any { arrival ->
                        !arrival.isRealTime && (
                            arrival.arrivalTime.isAfter(currentTime) && arrival.arrivalTime.isBefore(nextFewHours) ||
                            // Handle overnight service (e.g., 1am arrival when it's currently 11pm)
                            (currentTime.hour > 21 && arrival.arrivalTime.hour < 6)
                        )
                    }

                    // Separate real-time and static arrivals, but only filter real-time if no service is scheduled
                    val realTimeArrivals = arrivals.filter { it.isRealTime && hasScheduledService }
                    val staticArrivals = arrivals.filter { !it.isRealTime || !hasScheduledService }

                    // Display logic: 1 real-time + 2 static, or 3 static if no valid real-time
                    val arrivalsToShow = if (realTimeArrivals.isNotEmpty()) {
                        // Show 1 real-time + 2 static
                        realTimeArrivals.take(1) + staticArrivals.take(2)
                    } else {
                        // Show 3 static arrivals
                        staticArrivals.take(3)
                    }

                    arrivalsToShow.forEach { arrival ->
                        // Use standardized arrival time display component
                        com.kiz.transitapp.ui.components.ArrivalTimeDisplay(
                            arrival = arrival,
                            viewModel = viewModel
                        )
                    }
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
