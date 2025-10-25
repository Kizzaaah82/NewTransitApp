package com.kiz.transitapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.kiz.transitapp.ui.viewmodel.TransitViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Helper function to format any date for the timetable title
fun formatDateForTitle(date: LocalDate): String {
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
    return date.format(dayFormatter)
}

// This helper function is perfect as is.
fun formatTimetableTime(gtfsTime: String): String {
    return try {
        val timeParts = gtfsTime.split(":")
        var hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()

        if (hour >= 24) {
            hour -= 24
        }
        val localTime = LocalTime.of(hour, minute)
        localTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    } catch (_: Exception) {
        gtfsTime
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    navController: NavController,
    stopId: String,
    routeId: String,
    viewModel: TransitViewModel
) {
    val isLoading by viewModel.isTimetableLoading.collectAsState()
    val timetable by viewModel.timetable.collectAsState()
    val selectedDate by viewModel.selectedTimetableDate.collectAsState()

    // Load timetable when stop, route, or date changes
    LaunchedEffect(stopId, routeId, selectedDate) {
        viewModel.loadTimetableForStop(stopId, routeId, selectedDate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${formatDateForTitle(selectedDate)} Timetable") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Date Navigation Section
            DateNavigationCard(
                selectedDate = selectedDate,
                onDateChange = { newDate ->
                    viewModel.setTimetableDate(newDate)
                },
                onTodayClick = {
                    viewModel.setTimetableDateToToday()
                },
                canNavigateToDate = { date ->
                    viewModel.canNavigateToDate(date)
                }
            )

            // Content Section
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = if (isLoading || timetable.isEmpty()) Alignment.Center else Alignment.TopCenter
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator()
                    }
                    timetable.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No schedule available",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "for ${formatDateForTitle(selectedDate)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Get the actual stop data from ViewModel's transit data
                            val transitDataState by viewModel.transitData.collectAsState()
                            val actualStop = transitDataState?.stopIdToBusStop?.get(stopId)
                            val actualRoute = transitDataState?.routeIdToRoute?.get(routeId)

                            // Display stop name and code instead of just stop ID
                            if (actualStop != null) {
                                Text(
                                    actualStop.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp, top = 16.dp)
                                )
                                Text(
                                    "Stop ${actualStop.code ?: actualStop.id}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            } else {
                                Text(
                                    "Stop $stopId",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 16.dp, top = 16.dp)
                                )
                            }

                            // Display route information
                            if (actualRoute != null) {
                                Text(
                                    "Route ${actualRoute.shortName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    actualRoute.longName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            } else {
                                Text(
                                    "Route $routeId",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }

                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(timetable) { entry ->
                                    TimetableItem(
                                        routeId = entry.routeId,
                                        time = formatTimetableTime(entry.arrivalTime)
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

@Composable
fun TimetableItem(routeId: String, time: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(time, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun DateNavigationCard(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onTodayClick: () -> Unit,
    canNavigateToDate: (LocalDate) -> Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Date display and navigation arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous day button
                IconButton(
                    onClick = {
                        val previousDay = selectedDate.minusDays(1)
                        if (canNavigateToDate(previousDay)) {
                            onDateChange(previousDay)
                        }
                    },
                    enabled = canNavigateToDate(selectedDate.minusDays(1))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous day"
                    )
                }

                // Date display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Next day button
                IconButton(
                    onClick = {
                        val nextDay = selectedDate.plusDays(1)
                        if (canNavigateToDate(nextDay)) {
                            onDateChange(nextDay)
                        }
                    },
                    enabled = canNavigateToDate(selectedDate.plusDays(1))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next day"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Today button and date range indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Today button
                OutlinedButton(
                    onClick = onTodayClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Today")
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Date range indicator
                Text(
                    text = "±7 days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
