package com.kiz.transitapp.workers

import android.content.Context
import androidx.work.*
import com.kiz.transitapp.data.database.GTFSDatabase
import com.kiz.transitapp.data.database.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.BufferedReader
import java.io.StringReader

class GTFSSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = GTFSDatabase.getDatabase(context)

    private val gtfsApi = Retrofit.Builder()
        .baseUrl("https://raw.githubusercontent.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GTFSApi::class.java)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setProgress(Data.Builder().putString("status", "Starting GTFS sync...").build())

            // Download and parse each GTFS file
            syncAgencies()
            syncRoutes()
            syncStops()
            syncTrips()
            syncStopTimes()
            syncShapes()
            syncCalendar()
            syncCalendarDates()

            setProgress(Data.Builder().putString("status", "GTFS sync completed").build())
            Result.success()
        } catch (e: Exception) {
            setProgress(Data.Builder().putString("status", "GTFS sync failed: ${e.message}").build())
            Result.failure()
        }
    }

    private suspend fun syncAgencies() {
        setProgress(Data.Builder().putString("status", "Syncing agencies...").build())
        val response = gtfsApi.downloadFile("Kizzaaah82/gtfs/main/agency.txt")
        if (response.isSuccessful) {
            response.body()?.let { csvContent ->
                val agencies = parseAgencyCsv(csvContent)
                database.agencyDao().deleteAll()
                database.agencyDao().insertAll(agencies)
            }
        }
    }

    private suspend fun syncRoutes() {
        setProgress(Data.Builder().putString("status", "Syncing routes...").build())
        val response = gtfsApi.downloadFile("Kizzaaah82/gtfs/main/routes.txt")
        if (response.isSuccessful) {
            response.body()?.let { csvContent ->
                val routes = parseRoutesCsv(csvContent)
                database.routeDao().deleteAll()
                database.routeDao().insertAll(routes)
            }
        }
    }

    private suspend fun syncStops() {
        setProgress(Data.Builder().putString("status", "Syncing stops...").build())
        val response = gtfsApi.downloadFile("Kizzaaah82/gtfs/main/stops.txt")
        if (response.isSuccessful) {
            response.body()?.let { csvContent ->
                val stops = parseStopsCsv(csvContent)
                database.stopDao().deleteAll()
                database.stopDao().insertAll(stops)
            }
        }
    }

    private suspend fun syncTrips() {
        setProgress(Data.Builder().putString("status", "Syncing trips...").build())
        val response = gtfsApi.downloadFile("Kizzaaah82/gtfs/main/trips.txt")
        if (response.isSuccessful) {
            response.body()?.let { csvContent ->
                val trips = parseTripsCsv(csvContent)
                database.tripDao().deleteAll()
                database.tripDao().insertAll(trips)
            }
        }
    }

    private suspend fun syncStopTimes() {
        setProgress(Data.Builder().putString("status", "Syncing stop times...").build())
        val response = gtfsApi.downloadFile("Kizzaaah82/gtfs/main/stop_times.txt")
        if (response.isSuccessful) {
            response.body()?.let { csvContent ->
                val stopTimes = parseStopTimesCsv(csvContent)
                database.stopTimeDao().deleteAll()
                database.stopTimeDao().insertAll(stopTimes)
            }
        }
    }

    private suspend fun syncShapes() {
        setProgress(Data.Builder().putString("status", "Syncing shapes...").build())
        val response = gtfsApi.downloadFile("Kizzaaah82/gtfs/main/shapes.txt")
        if (response.isSuccessful) {
            response.body()?.let { csvContent ->
                val shapes = parseShapesCsv(csvContent)
                database.shapeDao().deleteAll()
                database.shapeDao().insertAll(shapes)
            }
        }
    }

    private suspend fun syncCalendar() {
        setProgress(Data.Builder().putString("status", "Syncing calendar...").build())
        val response = gtfsApi.downloadFile("Kizzaaah82/gtfs/main/calendar.txt")
        if (response.isSuccessful) {
            response.body()?.let { csvContent ->
                val calendars = parseCalendarCsv(csvContent)
                database.calendarDao().deleteAll()
                database.calendarDao().insertAll(calendars)
            }
        }
    }

    private suspend fun syncCalendarDates() {
        setProgress(Data.Builder().putString("status", "Syncing calendar dates...").build())
        val response = gtfsApi.downloadFile("Kizzaaah82/gtfs/main/calendar_dates.txt")
        if (response.isSuccessful) {
            response.body()?.let { csvContent ->
                val calendarDates = parseCalendarDatesCsv(csvContent)
                database.calendarDateDao().deleteAll()
                database.calendarDateDao().insertAll(calendarDates)
            }
        }
    }

    // CSV parsing functions
    private fun parseAgencyCsv(csvContent: String): List<Agency> {
        val lines = csvContent.split("\n")
        if (lines.isEmpty()) return emptyList()

        val header = lines[0].split(",")
        return lines.drop(1).mapNotNull { line ->
            if (line.trim().isEmpty()) return@mapNotNull null
            val values = parseCsvLine(line)
            if (values.size >= 4) {
                Agency(
                    agency_id = values[0],
                    agency_name = values[1],
                    agency_url = values[2],
                    agency_timezone = values[3],
                    agency_lang = values.getOrNull(4),
                    agency_phone = values.getOrNull(5),
                    agency_fare_url = values.getOrNull(6),
                    agency_email = values.getOrNull(7)
                )
            } else null
        }
    }

    private fun parseRoutesCsv(csvContent: String): List<Route> {
        val lines = csvContent.split("\n")
        if (lines.isEmpty()) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            if (line.trim().isEmpty()) return@mapNotNull null
            val values = parseCsvLine(line)
            if (values.size >= 6) {
                Route(
                    route_id = values[0],
                    agency_id = values[1],
                    route_short_name = values[2],
                    route_long_name = values[3],
                    route_desc = values.getOrNull(4),
                    route_type = values[5].toIntOrNull() ?: 0,
                    route_url = values.getOrNull(6),
                    route_color = values.getOrNull(7),
                    route_text_color = values.getOrNull(8),
                    route_sort_order = values.getOrNull(9)?.toIntOrNull()
                )
            } else null
        }
    }

    private fun parseStopsCsv(csvContent: String): List<Stop> {
        val lines = csvContent.split("\n")
        if (lines.isEmpty()) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            if (line.trim().isEmpty()) return@mapNotNull null
            val values = parseCsvLine(line)
            if (values.size >= 5) {
                Stop(
                    stop_id = values[0],
                    stop_code = values.getOrNull(1),
                    stop_name = values[2],
                    stop_desc = values.getOrNull(3),
                    stop_lat = values[4].toDoubleOrNull() ?: 0.0,
                    stop_lon = values[5].toDoubleOrNull() ?: 0.0,
                    zone_id = values.getOrNull(6),
                    stop_url = values.getOrNull(7),
                    location_type = values.getOrNull(8)?.toIntOrNull(),
                    parent_station = values.getOrNull(9),
                    stop_timezone = values.getOrNull(10),
                    wheelchair_boarding = values.getOrNull(11)?.toIntOrNull(),
                    platform_code = values.getOrNull(12)
                )
            } else null
        }
    }

    private fun parseTripsCsv(csvContent: String): List<Trip> {
        val lines = csvContent.split("\n")
        if (lines.isEmpty()) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            if (line.trim().isEmpty()) return@mapNotNull null
            val values = parseCsvLine(line)
            if (values.size >= 3) {
                Trip(
                    trip_id = values[2],
                    route_id = values[0],
                    service_id = values[1],
                    trip_headsign = values.getOrNull(3),
                    trip_short_name = values.getOrNull(4),
                    direction_id = values.getOrNull(5)?.toIntOrNull(),
                    block_id = values.getOrNull(6),
                    shape_id = values.getOrNull(7),
                    wheelchair_accessible = values.getOrNull(8)?.toIntOrNull(),
                    bikes_allowed = values.getOrNull(9)?.toIntOrNull()
                )
            } else null
        }
    }

    private fun parseStopTimesCsv(csvContent: String): List<StopTime> {
        val lines = csvContent.split("\n")
        if (lines.isEmpty()) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            if (line.trim().isEmpty()) return@mapNotNull null
            val values = parseCsvLine(line)
            if (values.size >= 5) {
                StopTime(
                    trip_id = values[0],
                    arrival_time = values[1],
                    departure_time = values[2],
                    stop_id = values[3],
                    stop_sequence = values[4].toIntOrNull() ?: 0,
                    stop_headsign = values.getOrNull(5),
                    pickup_type = values.getOrNull(6)?.toIntOrNull(),
                    drop_off_type = values.getOrNull(7)?.toIntOrNull(),
                    shape_dist_traveled = values.getOrNull(8)?.toDoubleOrNull(),
                    timepoint = values.getOrNull(9)?.toIntOrNull()
                )
            } else null
        }
    }

    private fun parseShapesCsv(csvContent: String): List<Shape> {
        val lines = csvContent.split("\n")
        if (lines.isEmpty()) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            if (line.trim().isEmpty()) return@mapNotNull null
            val values = parseCsvLine(line)
            if (values.size >= 4) {
                Shape(
                    shape_id = values[0],
                    shape_pt_lat = values[1].toDoubleOrNull() ?: 0.0,
                    shape_pt_lon = values[2].toDoubleOrNull() ?: 0.0,
                    shape_pt_sequence = values[3].toIntOrNull() ?: 0,
                    shape_dist_traveled = values.getOrNull(4)?.toDoubleOrNull()
                )
            } else null
        }
    }

    private fun parseCalendarCsv(csvContent: String): List<Calendar> {
        val lines = csvContent.split("\n")
        if (lines.isEmpty()) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            if (line.trim().isEmpty()) return@mapNotNull null
            val values = parseCsvLine(line)
            if (values.size >= 10) {
                Calendar(
                    service_id = values[0],
                    monday = values[1].toIntOrNull() ?: 0,
                    tuesday = values[2].toIntOrNull() ?: 0,
                    wednesday = values[3].toIntOrNull() ?: 0,
                    thursday = values[4].toIntOrNull() ?: 0,
                    friday = values[5].toIntOrNull() ?: 0,
                    saturday = values[6].toIntOrNull() ?: 0,
                    sunday = values[7].toIntOrNull() ?: 0,
                    start_date = values[8],
                    end_date = values[9]
                )
            } else null
        }
    }

    private fun parseCalendarDatesCsv(csvContent: String): List<CalendarDate> {
        val lines = csvContent.split("\n")
        if (lines.isEmpty()) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            if (line.trim().isEmpty()) return@mapNotNull null
            val values = parseCsvLine(line)
            if (values.size >= 3) {
                CalendarDate(
                    service_id = values[0],
                    date = values[1],
                    exception_type = values[2].toIntOrNull() ?: 0
                )
            } else null
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val reader = BufferedReader(StringReader(line))
        var inQuotes = false
        var currentField = StringBuilder()

        reader.forEachLine { char ->
            char.forEach { c ->
                when (c) {
                    '"' -> inQuotes = !inQuotes
                    ',' -> {
                        if (!inQuotes) {
                            result.add(currentField.toString().trim())
                            currentField.clear()
                        } else {
                            currentField.append(c)
                        }
                    }
                    else -> currentField.append(c)
                }
            }
        }
        result.add(currentField.toString().trim())
        return result
    }

    interface GTFSApi {
        @GET
        suspend fun downloadFile(@Url url: String): retrofit2.Response<String>
    }
}
