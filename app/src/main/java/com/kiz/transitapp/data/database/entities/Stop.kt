package com.kiz.transitapp.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stops")
data class Stop(
    @PrimaryKey
    val stop_id: String,
    val stop_code: String? = null,
    val stop_name: String,
    val stop_desc: String? = null,
    val stop_lat: Double,
    val stop_lon: Double,
    val zone_id: String? = null,
    val stop_url: String? = null,
    val location_type: Int? = null,
    val parent_station: String? = null,
    val stop_timezone: String? = null,
    val wheelchair_boarding: Int? = null,
    val platform_code: String? = null
)
