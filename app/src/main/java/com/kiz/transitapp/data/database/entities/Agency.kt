package com.kiz.transitapp.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agency")
data class Agency(
    @PrimaryKey
    val agency_id: String,
    val agency_name: String,
    val agency_url: String,
    val agency_timezone: String,
    val agency_lang: String? = null,
    val agency_phone: String? = null,
    val agency_fare_url: String? = null,
    val agency_email: String? = null
)
