package com.kiz.transitapp.data.database.dao

import androidx.room.*
import com.kiz.transitapp.data.database.entities.Trip

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE route_id = :routeId")
    suspend fun getTripsByRoute(routeId: String): List<Trip>

    @Query("SELECT * FROM trips WHERE trip_id = :tripId")
    suspend fun getTripById(tripId: String): Trip?

    @Query("SELECT * FROM trips WHERE service_id = :serviceId")
    suspend fun getTripsByService(serviceId: String): List<Trip>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<Trip>)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}
