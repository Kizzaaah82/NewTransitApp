package com.kiz.transitapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object NearbyStops : Screen("nearby_stops", "Nearby", Icons.Default.NearMe)
    object Map : Screen("map?stopId={stopId}", "Map", Icons.Default.LocationOn) {
        fun createRoute(stopId: String? = null) = if (stopId != null) "map?stopId=$stopId" else "map"
    }
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object WeeklySchedule : Screen("weekly_schedule", "Weekly Schedule", Icons.Default.Settings)
    object Timetable : Screen("timetable/{stopId}/{routeId}", "Timetable", Icons.Default.Settings) {
        fun createRoute(stopId: String, routeId: String) = "timetable/$stopId/$routeId"
        fun createRouteWithReturn(stopId: String, routeId: String) = "timetable/$stopId/$routeId"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.NearbyStops,
    Screen.Map,
    Screen.Settings
)
