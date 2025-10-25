package com.kiz.transitapp.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shapes")
data class Shape(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val shape_id: String,
    val shape_pt_lat: Double,
    val shape_pt_lon: Double,
    val shape_pt_sequence: Int,
    val shape_dist_traveled: Double? = null
)
