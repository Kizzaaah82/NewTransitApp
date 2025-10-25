package com.kiz.transitapp.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kiz.transitapp.data.database.entities.Calendar

@Dao
interface CalendarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calendars: List<Calendar>)

    @Query("DELETE FROM calendar")
    suspend fun deleteAll()
}

