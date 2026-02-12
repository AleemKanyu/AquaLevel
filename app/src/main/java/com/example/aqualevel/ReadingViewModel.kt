package com.example.aqualevel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel for managing and providing water level reading data to the UI.
 * It interacts with the [ReadingsRepository] to fetch and persist data.
 */
class ReadingViewModel(application: Application) : AndroidViewModel(application) {

    /** LiveData containing all readings, observed by UI components. */
    val allReadings: LiveData<List<Readings>>
    /** LiveData containing summarized usage data for the last 7 days. */
    val weeklyUsage: LiveData<List<DailyUsage>>

    private val repository: ReadingsRepository

    init {
        val dao = ReadingsDatabase.getInstance(application).getReadingsDao()
        repository = ReadingsRepository(dao)

        allReadings = repository.allReadings
        weeklyUsage = repository.last7DaysUsage
    }

    /**
     * Adds a new water level reading to the database.
     * @param readings The [Readings] object to insert.
     */
    fun addReading(readings: Readings) = viewModelScope.launch {
        repository.insert(readings)
    }
}
