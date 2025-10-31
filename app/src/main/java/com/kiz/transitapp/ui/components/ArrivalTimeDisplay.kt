package com.kiz.transitapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiz.transitapp.ui.theme.PastelBlue
import com.kiz.transitapp.ui.theme.PastelOrange
import com.kiz.transitapp.ui.theme.PastelRed
import com.kiz.transitapp.ui.viewmodel.StopArrivalTime
import com.kiz.transitapp.ui.viewmodel.TransitViewModel
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Enhanced arrival time display component with clear visual hierarchy.
 * Shows primary arrival time (large, color-coded) and next 2 static times below.
 */
@Composable
fun ArrivalTimeDisplay(
    arrival: StopArrivalTime,
    viewModel: TransitViewModel,
    modifier: Modifier = Modifier,
    nextArrivals: List<StopArrivalTime> = emptyList()
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Primary arrival time (large, color-coded)
        PrimaryArrivalDisplay(
            arrival = arrival,
            viewModel = viewModel
        )

        // Next buses section (if available)
        if (nextArrivals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            NextBusesDisplay(
                nextArrivals = nextArrivals.take(2)
            )
        }
    }
}

@Composable
private fun PrimaryArrivalDisplay(
    arrival: StopArrivalTime,
    viewModel: TransitViewModel
) {
    val countdown = viewModel.getArrivalCountdown(arrival.arrivalTime)

    // Determine display text based on countdown
    val displayText = when {
        countdown < 1 -> "Due"
        countdown == 1 -> "1 min"
        countdown < 60 -> "$countdown mins"
        else -> arrival.arrivalTime.format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    // Determine color based on delay (only for real-time data)
    val textColor = if (arrival.isRealTime && arrival.isFeedFresh) {
        // Calculate delay: Try feed's delaySeconds first, then calculate from scheduled time
        val delayMinutes = if (arrival.delaySeconds != 0) {
            // Use the delay from the GTFS-RT feed
            val delayMins = (arrival.delaySeconds / 60.0).roundToInt()
            android.util.Log.d("ArrivalTimeDisplay", "Using feed delay: ${arrival.delaySeconds}s = $delayMins mins (Route: ${arrival.routeId})")
            delayMins
        } else if (arrival.scheduledTime != null) {
            // Fallback: Calculate delay by comparing real-time arrival vs scheduled time
            val scheduledCountdown = viewModel.getArrivalCountdown(arrival.scheduledTime!!)
            val calculatedDelay = countdown - scheduledCountdown
            android.util.Log.d("ArrivalTimeDisplay", "Calculated delay from times: Real=${countdown}m, Scheduled=${scheduledCountdown}m, Delay=${calculatedDelay}m (Route: ${arrival.routeId})")
            calculatedDelay
        } else {
            android.util.Log.d("ArrivalTimeDisplay", "No delay info available (Route: ${arrival.routeId})")
            0
        }

        when {
            delayMinutes >= 5 -> {
                android.util.Log.d("ArrivalTimeDisplay", "🔴 RED: ${delayMinutes} mins late (Route: ${arrival.routeId})")
                PastelRed // Red for 5+ min late
            }
            delayMinutes in 1..4 -> {
                android.util.Log.d("ArrivalTimeDisplay", "🟠 ORANGE: ${delayMinutes} mins late (Route: ${arrival.routeId})")
                PastelOrange // Orange for 1-4 min late
            }
            else -> {
                android.util.Log.d("ArrivalTimeDisplay", "🔵 BLUE: On time or ${-delayMinutes} mins early (Route: ${arrival.routeId})")
                PastelBlue // Blue for on-time/early
            }
        }
    } else {
        android.util.Log.d("ArrivalTimeDisplay", "⚪ GREY: Static schedule (Route: ${arrival.routeId}, isRealTime=${arrival.isRealTime}, isFeedFresh=${arrival.isFeedFresh})")
        // Grey for static scheduled times
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = displayText,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = textColor
    )
}

@Composable
private fun NextBusesDisplay(
    nextArrivals: List<StopArrivalTime>
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Next buses:",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        val times = nextArrivals.map {
            it.arrivalTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        }.joinToString(", ")

        Text(
            text = times,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

