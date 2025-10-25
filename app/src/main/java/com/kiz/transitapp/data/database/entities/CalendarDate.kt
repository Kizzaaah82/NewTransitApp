package com.kiz.transitapp.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_dates")
data class CalendarDate(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val service_id: String,
    val date: String,
    val exception_type: Int
)

