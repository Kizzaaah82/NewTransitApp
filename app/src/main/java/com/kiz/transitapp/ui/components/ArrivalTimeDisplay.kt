package com.kiz.transitapp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kiz.transitapp.ui.viewmodel.StopArrivalTime
import com.kiz.transitapp.ui.viewmodel.TransitViewModel

/**
 * Standardized arrival time display component that uses ViewModel helper functions
 * for consistent formatting across all screens.
 */
@Composable
fun ArrivalTimeDisplay(
    arrival: StopArrivalTime,
    viewModel: TransitViewModel,
    fontSize: Int = 14
) {
    // Use ViewModel's helper function for countdown calculation
    val countdown = viewModel.getArrivalCountdown(arrival.arrivalTime)

    // Format the arrival display text
    val displayText = when {
        countdown < 1 -> "Due"
        countdown == 1 -> "1 min"
        countdown < 60 -> "$countdown mins"
        else -> arrival.arrivalTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    }

    // Add delay information for real-time arrivals
    val delayText = if (arrival.isRealTime) {
        when {
            arrival.delaySeconds > 60 -> " (${arrival.delaySeconds / 60} min late)"
            arrival.delaySeconds < -60 -> " (${(-arrival.delaySeconds) / 60} min early)"
            arrival.delaySeconds > 30 -> " (late)"
            arrival.delaySeconds < -30 -> " (early)"
            else -> " (on time)"
        }
    } else ""

    // Determine text color based on delay status
    val textColor = when {
        !arrival.isRealTime -> MaterialTheme.colorScheme.onSurface
        arrival.delaySeconds > 60 -> MaterialTheme.colorScheme.error
        arrival.delaySeconds < -60 -> Color(0xFF4CAF50) // Green for early
        else -> MaterialTheme.colorScheme.primary
    }

    Text(
        text = "$displayText$delayText",
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Medium,
        color = textColor
    )
}

