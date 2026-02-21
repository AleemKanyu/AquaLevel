package com.example.aqualevel

import androidx.lifecycle.LiveData

/**
 * Repository class that abstracts access to the data sources (Room database).
 * It provides a clean API for data access to the rest of the application.
 */
class ReadingsRepository (private val readingsDao: ReadingsDao){
    /** LiveData list of all water level readings. */
    val allReadings: LiveData<List<Readings>> = readingsDao.fetchALL()
    /** LiveData list of daily usage statistics for the last 7 days. */
    val last7DaysUsage: LiveData<List<DailyUsage>> = readingsDao.getLast7DaysUsage()

    /**
     * Inserts a new reading into the database.
     * @param readings The [Readings] object to insert.
     */
    suspend fun insert(readings: Readings){
        readingsDao.insert(readings)
    }

    /**
     * Inserts a list of readings into the database.
     * @param readingsList The list of [Readings] objects to insert.
     */
    suspend fun insertAll(readingsList: List<Readings>) {
        readingsDao.insertAll(readingsList)
    }

    /**
     * Synchronously fetches all readings from the database.
     * Useful for background tasks or data export.
     */
    suspend fun getAllReadingsSync(): List<Readings> {
        return readingsDao.getAllReadingsSync()
    }
}
