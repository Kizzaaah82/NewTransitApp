package com.kiz.transitapp.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kiz.transitapp.data.database.entities.CalendarDate

@Dao
interface CalendarDateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calendarDates: List<CalendarDate>)

    @Query("DELETE FROM calendar_dates")
    suspend fun deleteAll()
}

