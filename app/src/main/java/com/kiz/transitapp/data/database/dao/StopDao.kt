package com.kiz.transitapp.data.database.dao

import androidx.room.*
import com.kiz.transitapp.data.database.entities.Stop
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {
    @Query("SELECT * FROM stops")
    fun getAllStops(): Flow<List<Stop>>

    @Query("SELECT * FROM stops WHERE stop_id = :stopId")
    suspend fun getStopById(stopId: String): Stop?

    @Query("SELECT * FROM stops WHERE stop_name LIKE '%' || :query || '%'")
    suspend fun searchStops(query: String): List<Stop>

    @Query("SELECT * FROM stops WHERE stop_id IN (:stopIds)")
    suspend fun getStopsByIds(stopIds: List<String>): List<Stop>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<Stop>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stop: Stop)

    @Query("DELETE FROM stops")
    suspend fun deleteAll()
}
