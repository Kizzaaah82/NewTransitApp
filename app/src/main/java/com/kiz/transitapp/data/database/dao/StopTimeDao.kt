package com.kiz.transitapp.data.database.dao

import androidx.room.*
import com.kiz.transitapp.data.database.entities.StopTime
import kotlinx.coroutines.flow.Flow

data class ArrivalTimeResult(
    val routeId: String,
    val arrivalTime: String,
    val tripId: String,
    val serviceId: String
)

@Dao
interface StopTimeDao {
    @Query("SELECT * FROM stop_times WHERE stop_id = :stopId ORDER BY departure_time")
    suspend fun getStopTimesByStop(stopId: String): List<StopTime>

    @Query("SELECT * FROM stop_times WHERE trip_id = :tripId ORDER BY stop_sequence")
    suspend fun getStopTimesByTrip(tripId: String): List<StopTime>

    @Query("""
        SELECT st.* FROM stop_times st 
        INNER JOIN trips t ON st.trip_id = t.trip_id 
        WHERE st.stop_id = :stopId AND t.route_id = :routeId 
        ORDER BY st.departure_time
    """)
    suspend fun getStopTimesForStopAndRoute(stopId: String, routeId: String): List<StopTime>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stopTimes: List<StopTime>)

    @Query("DELETE FROM stop_times")
    suspend fun deleteAll()

    @Query("""
        SELECT t.route_id as routeId, st.arrival_time as arrivalTime, st.trip_id as tripId, t.service_id as serviceId
        FROM stop_times st 
        INNER JOIN trips t ON st.trip_id = t.trip_id 
        WHERE st.stop_id = :stopId 
        AND t.service_id IN (:activeServiceIds)
        AND st.arrival_time >= :currentTime
        ORDER BY st.arrival_time
        LIMIT :limit
    """)
    suspend fun getUpcomingArrivals(
        stopId: String,
        activeServiceIds: List<String>,
        currentTime: String,
        limit: Int = 20
    ): List<ArrivalTimeResult>

    @Query("""
        SELECT t.route_id as routeId, st.arrival_time as arrivalTime, st.trip_id as tripId, t.service_id as serviceId
        FROM stop_times st 
        INNER JOIN trips t ON st.trip_id = t.trip_id 
        WHERE st.stop_id = :stopId 
        AND t.service_id IN (:activeServiceIds)
        AND st.arrival_time >= :currentTime
        ORDER BY st.arrival_time
        LIMIT 20
    """)
    fun getUpcomingArrivalsFlow(
        stopId: String,
        activeServiceIds: List<String>,
        currentTime: String
    ): Flow<List<ArrivalTimeResult>>

    @Query("SELECT COUNT(*) FROM stop_times WHERE stop_id = :stopId")
    suspend fun getArrivalCountForStop(stopId: String): Int
}
