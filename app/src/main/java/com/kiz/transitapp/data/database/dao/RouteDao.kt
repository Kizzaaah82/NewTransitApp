package com.kiz.transitapp.data.database.dao

import androidx.room.*
import com.kiz.transitapp.data.database.entities.Route
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes")
    fun getAllRoutes(): Flow<List<Route>>

    @Query("SELECT * FROM routes WHERE route_id = :routeId")
    suspend fun getRouteById(routeId: String): Route?

    @Query("SELECT * FROM routes ORDER BY route_short_name")
    suspend fun getRoutesOrderedByShortName(): List<Route>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<Route>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: Route)

    @Query("DELETE FROM routes")
    suspend fun deleteAll()
}
