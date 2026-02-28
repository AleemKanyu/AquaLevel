package com.example.aqualevel

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Room database for storing water level readings.
 * Uses a singleton pattern to ensure only one instance of the database exists.
 */
@Database(
    entities = [Readings::class, HourlyReadingEntity::class, DailyUsageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ReadingsDatabase : RoomDatabase() {
    /**
     * Provides the DAO for interacting with the readings table.
     */
    abstract fun getReadingsDao(): ReadingsDao

    companion object {
        @Volatile private var INSTANCE: ReadingsDatabase? = null

        /**
         * Returns the singleton instance of [ReadingsDatabase].
         * @param context The application context.
         */
        fun getInstance(context: Context): ReadingsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReadingsDatabase::class.java,
                    "readings_db"
                )
                .fallbackToDestructiveMigration() // For development simplicity, or use migrations for production
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
