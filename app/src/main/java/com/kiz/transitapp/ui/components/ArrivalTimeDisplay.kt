package com.kiz.transitapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiz.transitapp.ui.viewmodel.StopArrivalTime
import com.kiz.transitapp.ui.viewmodel.TransitViewModel
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Enhanced arrival time display component with clear visual hierarchy.
 * Shows real-time predictions with delay badges and scheduled baseline times.
 */
@Composable
fun ArrivalTimeDisplay(
    arrival: StopArrivalTime,
    viewModel: TransitViewModel,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val countdown = viewModel.getArrivalCountdown(arrival.arrivalTime)

    // Determine display text for the main ETA
    val displayText = when {
        countdown < 1 -> "Due"
        countdown == 1 -> "1 min"
        countdown < 60 -> "$countdown mins"
        else -> arrival.arrivalTime.format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    // Only show LIVE and delay badges if feed is fresh
    val showLiveBadges = arrival.isRealTime && arrival.isFeedFresh

    if (arrival.isRealTime) {
        // REAL-TIME ARRIVAL: Big ETA with delay badge and scheduled baseline
        RealTimeArrivalDisplay(
            displayText = displayText,
            delaySeconds = arrival.delaySeconds,
            scheduledTime = arrival.scheduledTime,
            countdown = countdown,
            isCompact = isCompact,
            showLiveBadges = showLiveBadges,
            modifier = modifier
        )
    } else {
        // STATIC SCHEDULED ARRIVAL: Simple, smaller display
        StaticArrivalDisplay(
            displayText = displayText,
            isCompact = isCompact,
            modifier = modifier
        )
    }
}

@Composable
private fun RealTimeArrivalDisplay(
    displayText: String,
    delaySeconds: Int,
    scheduledTime: java.time.LocalTime?,
    countdown: Int,
    isCompact: Boolean,
    showLiveBadges: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Big real-time ETA
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = displayText,
                    fontSize = if (isCompact) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // LIVE badge - only if feed is fresh
                if (showLiveBadges && !isCompact) {
                    Text(
                        text = "LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Scheduled baseline time (gray microcopy) - with fixed math
            if (scheduledTime != null && !isCompact && showLiveBadges) {
                // Fix: Use proper rounding and coerceAtLeast(0) as per notes
                val scheduledCountdown = (countdown - (delaySeconds / 60.0)).roundToInt().coerceAtLeast(0)
                val scheduledText = when {
                    scheduledCountdown < 1 -> "Scheduled: Due"
                    scheduledCountdown == 1 -> "Scheduled: 1 min"
                    scheduledCountdown < 60 -> "Scheduled: $scheduledCountdown mins"
                    else -> "Scheduled: ${scheduledTime.format(DateTimeFormatter.ofPattern("h:mm a"))}"
                }

                Text(
                    text = scheduledText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // If feed is stale, show warning instead of scheduled time
            if (!showLiveBadges && !isCompact) {
                Text(
                    text = "Real-time temporarily unavailable",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Delay badge (right side) - only if feed is fresh
        if (showLiveBadges) {
            DelayBadge(delaySeconds = delaySeconds, isCompact = isCompact)
        }
    }
}

@Composable
private fun StaticArrivalDisplay(
    displayText: String,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "📅",
                fontSize = if (isCompact) 14.sp else 16.sp
            )
            Text(
                text = displayText,
                fontSize = if (isCompact) 14.sp else 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (!isCompact) {
            Text(
                text = "Scheduled",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun DelayBadge(
    delaySeconds: Int,
    isCompact: Boolean
) {
    // Fix: Use proper rounding instead of truncation as per notes
    val delayMinutes = (delaySeconds / 60.0).roundToInt()

    // Determine badge appearance based on delay
    val (badgeText, badgeColor, textColor) = when {
        delayMinutes > 1 -> {
            Triple(
                "$delayMinutes min late",
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.onError
            )
        }
        delayMinutes < -1 -> {
            Triple(
                "${-delayMinutes} min early",
                Color(0xFF4CAF50), // Green
                Color.White
            )
        }
        delayMinutes == 1 -> {
            Triple(
                "1 min late",
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.onError
            )
        }
        delayMinutes == -1 -> {
            Triple(
                "1 min early",
                Color(0xFF4CAF50),
                Color.White
            )
        }
        delaySeconds > 30 -> {
            Triple(
                "Delayed",
                Color(0xFFFF9800), // Orange
                Color.White
            )
        }
        delaySeconds < -30 -> {
            Triple(
                "Early",
                Color(0xFF81C784), // Light green
                Color.White
            )
        }
        else -> {
            // Fix: Use neutral surface/outline style for better contrast (per notes)
            Triple(
                "On time",
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Text(
        text = badgeText,
        fontSize = if (isCompact) 10.sp else 11.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = Modifier
            .background(
                color = badgeColor,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

