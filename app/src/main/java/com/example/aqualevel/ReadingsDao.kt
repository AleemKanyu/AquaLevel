package com.example.aqualevel

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * Data Access Object for the readings table.
 * Defines all database interactions (insert, query) for [Readings] data.
 */
@Dao
interface ReadingsDao {
    /**
     * Inserts a single reading into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(readings: Readings)

    /**
     * Inserts multiple readings into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readingsList: List<Readings>)

    /**
     * Fetches all readings from the database, ordered by timestamp in descending order.
     * @return A [LiveData] list of all [Readings].
     */
    @Query("SELECT * FROM readings ORDER BY timestamp DESC")
    fun fetchALL(): LiveData<List<Readings>>

    /**
     * Synchronous fetch of all readings for export purposes.
     */
    @Query("SELECT * FROM readings ORDER BY timestamp DESC")
    suspend fun getAllReadingsSync(): List<Readings>

    /**
     * Calculates the daily usage for the last 7 days.
     * It groups readings by day and finds the min and max levels for each day.
     * @return A [LiveData] list of [DailyUsage] objects for the last 7 days.
     */
    @Query("SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as day, MIN(level) as minLevel, MAX(level) as maxLevel FROM readings GROUP BY day ORDER BY day DESC LIMIT 7")
    fun getLast7DaysUsage(): LiveData<List<DailyUsage>>
}
