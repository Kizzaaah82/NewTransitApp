# NewTransitApp

A modern Android transit application built with Jetpack Compose, providing real-time bus tracking, route information, and service alerts.

## Features

- 🗺️ **Interactive Map** - View all transit routes and stops on an interactive Google Maps interface
- 🚌 **Live Bus Tracking** - Real-time vehicle positions with 20-second refresh intervals
- ⏰ **Arrival Times** - Real-time and scheduled arrival predictions for all stops
- ⭐ **Favorites** - Save your frequently used stops and routes for quick access
- 📋 **Timetables** - Comprehensive schedule information for all routes
- ⚠️ **Service Alerts** - Official transit agency alerts for detours, delays, and service changes
- 🎨 **Route Colors** - Color-coded routes matching official transit agency branding
- 📍 **Location Services** - Automatic location detection and "My Location" button
- 🔔 **Smart Filtering** - Filter buses and stops by route for easier navigation

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Maps**: Google Maps SDK for Android
- **Networking**: Retrofit, OkHttp
- **Data**: GTFS (General Transit Feed Specification) and GTFS-Realtime
- **Async**: Kotlin Coroutines and Flow
- **Dependency Injection**: Manual DI with ViewModels

## Project Structure

```
app/src/main/java/com/kiz/transitapp/
├── data/              # Data layer (repositories, network, models)
├── ui/
│   ├── components/    # Reusable UI components
│   ├── navigation/    # Navigation graph and routes
│   ├── screens/       # Screen composables (Map, Favorites, Timetable)
│   ├── viewmodel/     # ViewModels for state management
│   └── utils/         # UI utilities (IconCache, etc.)
└── MainActivity.kt    # App entry point
```

## Key Features Implementation

### Real-Time Bus Tracking
- Vehicle positions update every 20 seconds from GTFS-Realtime feed
- Optimized marker rendering with icon caching to prevent UI lag
- Smart vehicle filtering based on selected route
- Late-night handling for vehicles with unknown route assignments

### Optimized Performance
- **Icon Caching**: Bus and stop icons are pre-cached to avoid expensive bitmap operations
- **Optimized Transit Data**: Route polylines and stop mappings are pre-computed
- **Efficient Recomposition**: Strategic use of `remember`, `key()`, and state isolation
- **Lazy Loading**: Large lists use LazyColumn for efficient scrolling

### Service Alerts
- Real-time alerts from transit agency GTFS-Realtime feed
- Categorized by type (Detour, Delay, Stop Closed, etc.)
- Integrated into route selection UI with warning icons
- Detailed alert descriptions and affected routes

## Setup

1. Clone the repository
2. Open the project in Android Studio
3. Add your Google Maps API key to `local.properties`:
   ```
   MAPS_API_KEY=your_api_key_here
   ```
4. Sync Gradle and build the project

## Requirements

- Android Studio Hedgehog or later
- Minimum SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- Kotlin 1.9+

## Build

```bash
./gradlew assembleDebug
```

## License

This project is open source and available under the MIT License.

## Acknowledgments

- Transit data provided by local transit agency GTFS feeds
- Maps powered by Google Maps Platform
- Built with Jetpack Compose and Material 3 Design

