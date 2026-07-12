package com.aqualevel.app

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the readings table.
 * Defines all database interactions (insert, query) for water level data.
 */
@Dao
interface ReadingsDao {
    // --- Old Readings (Keep for compatibility if needed, or remove if strictly following new requirements) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(readings: Readings)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readingsList: List<Readings>)

    @Query("SELECT * FROM readings ORDER BY timestamp DESC")
    fun fetchALL(): LiveData<List<Readings>>

    @Query("SELECT * FROM readings ORDER BY timestamp DESC")
    suspend fun getAllReadingsSync(): List<Readings>

    @Query("SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as day, MIN(level) as minLevel, MAX(level) as maxLevel FROM readings GROUP BY day ORDER BY day DESC LIMIT 7")
    fun getLast7DaysUsage(): LiveData<List<DailyUsage>>

    @Query("DELETE FROM readings")
    suspend fun clearReadings()


    // --- New Hourly Reading Logic ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHourlyReadings(readings: List<HourlyReadingEntity>)

    @Query("SELECT * FROM hourly_readings ORDER BY hour ASC")
    fun getHourlyReadings(): Flow<List<HourlyReadingEntity>>

    @Query("DELETE FROM hourly_readings")
    suspend fun clearHourlyReadings()

    // --- New Daily Usage Logic ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyUsages(usages: List<DailyUsageEntity>)

    @Query("SELECT * FROM daily_usage ORDER BY date DESC LIMIT 7")
    fun getLast7DaysDailyUsage(): Flow<List<DailyUsageEntity>>
    
    @Query("DELETE FROM daily_usage")
    suspend fun clearDailyUsage()
}
