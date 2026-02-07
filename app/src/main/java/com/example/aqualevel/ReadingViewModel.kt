package com.example.aqualevel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ReadingViewModel(application: Application) : AndroidViewModel(application) {

    val allReadings: LiveData<List<Readings>>
    val weeklyUsage: LiveData<List<DailyUsage>>

    private val repository: ReadingsRepository

    init {
        val dao = ReadingsDatabase.getInstance(application).getReadingsDao()
        repository = ReadingsRepository(dao)

        allReadings = repository.allReadings
        weeklyUsage = repository.last7DaysUsage
    }

    fun addReading(readings: Readings) = viewModelScope.launch {
        repository.insert(readings)
    }
}
