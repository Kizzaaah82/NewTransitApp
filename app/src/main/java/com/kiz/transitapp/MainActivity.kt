package com.kiz.transitapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kiz.transitapp.data.gtfs.GtfsUpdateManager
import com.kiz.transitapp.ui.navigation.Screen
import com.kiz.transitapp.ui.navigation.bottomNavItems
import com.kiz.transitapp.ui.screens.*
import com.kiz.transitapp.ui.theme.NewTransitAppTheme
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiz.transitapp.ui.viewmodel.TransitViewModel
import com.kiz.transitapp.ui.utils.IconCache

class MainActivity : ComponentActivity() {

    internal lateinit var transitViewModel: TransitViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize IconCache for vehicle markers
        IconCache.initialize(this)

        // Initialize GTFS automatic updates
        val gtfsUpdateManager = GtfsUpdateManager(this)
        gtfsUpdateManager.scheduleDaily2AMUpdates()

        setContent {
            NewTransitAppTheme {
                NextStopApp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Notify ViewModel that app is paused to stop unnecessary updates
        if (::transitViewModel.isInitialized) {
            transitViewModel.onAppPaused()
        }
    }

    override fun onResume() {
        super.onResume()
        // Notify ViewModel that app is resumed to restart updates
        if (::transitViewModel.isInitialized) {
            transitViewModel.onAppResumed()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextStopApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Create shared ViewModel instance
    val sharedTransitViewModel: TransitViewModel = viewModel()

    // Store reference in MainActivity for lifecycle callbacks
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(sharedTransitViewModel) {
        if (context is MainActivity) {
            context.transitViewModel = sharedTransitViewModel
        }
    }

    // --- NEW: A list of routes that should show the main TopAppBar ---
    val topLevelScreenRoutes = bottomNavItems.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // --- MODIFIED: Conditionally show the TopAppBar ---
            // Only show the main app bar on top-level screens.
            // This prevents it from appearing on the TimetableScreen.
            if (currentDestination?.route in topLevelScreenRoutes) {
                TopAppBar(
                    title = { Text("Next stop: WHO KNOWS!") }
                )
            }
        },
        bottomBar = {
            // Conditionally show the bottom bar as well, if desired
            if (currentDestination?.route in topLevelScreenRoutes) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.route == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route, // Changed to start on Home Screen
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = sharedTransitViewModel, navController = navController)
            }
            composable(Screen.NearbyStops.route) {
                NearbyStopsScreen(viewModel = sharedTransitViewModel, navController = navController)
            }
            composable(
                Screen.Map.route,
                arguments = listOf(navArgument("stopId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val stopId = backStackEntry.arguments?.getString("stopId")
                MapScreen(navController = navController, initialStopId = stopId, viewModel = sharedTransitViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
            composable(Screen.WeeklySchedule.route) {
                WeeklyScheduleScreen()
            }

            // --- MODIFIED: The composable for TimetableScreen ---
            composable(
                route = Screen.Timetable.route,
                // Make sure arguments are defined so the route can be built correctly
                arguments = listOf(
                    navArgument("stopId") { type = NavType.StringType },
                    navArgument("routeId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val stopId = backStackEntry.arguments?.getString("stopId")
                val routeId = backStackEntry.arguments?.getString("routeId")
                if (stopId != null && routeId != null) {
                    // Pass the navController to the TimetableScreen
                    TimetableScreen(
                        navController = navController,
                        stopId = stopId,
                        routeId = routeId,
                        viewModel = sharedTransitViewModel
                    )
                }
            }
        }
    }
}