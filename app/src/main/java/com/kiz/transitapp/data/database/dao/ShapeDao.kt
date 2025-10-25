package com.kiz.transitapp.data.database.dao

import androidx.room.*
import com.kiz.transitapp.data.database.entities.Shape

@Dao
interface ShapeDao {
    @Query("SELECT * FROM shapes WHERE shape_id = :shapeId ORDER BY shape_pt_sequence")
    suspend fun getShapePoints(shapeId: String): List<Shape>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shapes: List<Shape>)

    @Query("DELETE FROM shapes")
    suspend fun deleteAll()
}
