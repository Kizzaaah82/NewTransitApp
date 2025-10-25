package com.kiz.transitapp.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stop_times",
    indices = [
        Index(value = ["stop_id"]),
        Index(value = ["trip_id"]),
        Index(value = ["stop_id", "arrival_time"]),
        Index(value = ["arrival_time"])
    ]
)
data class StopTime(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trip_id: String,
    val arrival_time: String,
    val departure_time: String,
    val stop_id: String,
    val stop_sequence: Int,
    val stop_headsign: String? = null,
    val pickup_type: Int? = null,
    val drop_off_type: Int? = null,
    val shape_dist_traveled: Double? = null,
    val timepoint: Int? = null
)
