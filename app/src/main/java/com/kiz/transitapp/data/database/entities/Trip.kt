package com.kiz.transitapp.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    indices = [
        Index(value = ["route_id"]),
        Index(value = ["service_id"]),
        Index(value = ["route_id", "service_id"])
    ]
)
data class Trip(
    @PrimaryKey
    val trip_id: String,
    val route_id: String,
    val service_id: String,
    val trip_headsign: String? = null,
    val trip_short_name: String? = null,
    val direction_id: Int? = null,
    val block_id: String? = null,
    val shape_id: String? = null,
    val wheelchair_accessible: Int? = null,
    val bikes_allowed: Int? = null
)
