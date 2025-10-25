package com.kiz.transitapp.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey
    val route_id: String,
    val agency_id: String,
    val route_short_name: String,
    val route_long_name: String,
    val route_desc: String? = null,
    val route_type: Int,
    val route_url: String? = null,
    val route_color: String? = null,
    val route_text_color: String? = null,
    val route_sort_order: Int? = null
)
