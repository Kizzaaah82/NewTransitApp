package com.kiz.transitapp.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.kiz.transitapp.data.database.dao.*
import com.kiz.transitapp.data.database.entities.*

@Database(
    entities = [
        Agency::class,
        Route::class,
        Stop::class,
        Trip::class,
        StopTime::class,
        Shape::class,
        Calendar::class,
        CalendarDate::class
    ],
    version = 2, // PERFORMANCE: Incremented version for new indexes
    exportSchema = false
)
abstract class GTFSDatabase : RoomDatabase() {
    abstract fun agencyDao(): AgencyDao
    abstract fun routeDao(): RouteDao
    abstract fun stopDao(): StopDao
    abstract fun tripDao(): TripDao
    abstract fun stopTimeDao(): StopTimeDao
    abstract fun shapeDao(): ShapeDao
    abstract fun calendarDao(): CalendarDao
    abstract fun calendarDateDao(): CalendarDateDao

    companion object {
        @Volatile
        private var INSTANCE: GTFSDatabase? = null

        fun getDatabase(context: Context): GTFSDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GTFSDatabase::class.java,
                    "gtfs_database"
                )
                .fallbackToDestructiveMigration() // PERFORMANCE: Allow rebuild with indexes
                // PERFORMANCE: Enable parallel query execution
                .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
