package com.example.aqualevel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel for managing and providing water level reading data to the UI.
 * It interacts with the [ReadingsRepository] to fetch and persist data.
 */
class ReadingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ReadingsRepository

    /** 
     * LiveData containing hourly readings from Room.
     * Maps HourlyReadingEntity to the legacy Readings format if necessary,
     * or provides them directly for the Hourly Graph.
     */
    val hourlyReadings: LiveData<List<HourlyReadingEntity>>

    /** 
     * LiveData containing all readings from Room.
     * Used for getting the real-time live distance.
     */
    val allReadings: LiveData<List<Readings>>

    /** 
     * LiveData containing daily usage for the last 7 days from Room.
     * Used for the Weekly Graph.
     */
    val weeklyUsage: LiveData<List<DailyUsageEntity>>

    private val _refreshTrigger = androidx.lifecycle.MutableLiveData<Unit>()
    val refreshTrigger: LiveData<Unit> = _refreshTrigger

    init {
        val dao = ReadingsDatabase.getInstance(application).getReadingsDao()
        repository = ReadingsRepository(dao)

        // Single source of truth: Room flows converted to LiveData
        hourlyReadings = repository.hourlyReadings.asLiveData()
        weeklyUsage = repository.dailyUsages.asLiveData()
        allReadings = repository.allReadings

        // Start real-time sync from Firestore to Room
        repository.startSyncing()
    }

    /** Triggers a refresh if needed (e.g., manual pull-to-refresh) */
    fun refreshAllReadings() {
        _refreshTrigger.value = Unit
        // Since we use snapshot listeners, data updates automatically.
        // But we can re-trigger sync if needed or just let Firestore handle it.
    }

    /**
     * Adds a new water level reading (kept for compatibility).
     */
    fun addReading(readings: Readings) = viewModelScope.launch {
        repository.insert(readings)
    }
}
