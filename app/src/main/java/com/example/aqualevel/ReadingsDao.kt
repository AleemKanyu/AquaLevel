package com.example.aqualevel

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ReadingsDao {
    @Insert
    suspend fun insert(readings: Readings)

    @Query("SELECT * FROM readings ORDER BY timestamp DESC")
    fun fetchALL(): LiveData<List<Readings>>

    @Query("SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as day, MIN(level) as minLevel, MAX(level) as maxLevel FROM readings GROUP BY day ORDER BY day DESC LIMIT 7")
    fun getLast7DaysUsage(): LiveData<List<DailyUsage>>
}
