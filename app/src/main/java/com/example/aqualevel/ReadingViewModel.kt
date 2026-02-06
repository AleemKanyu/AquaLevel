package com.example.aqualevel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ReadingViewModel(application: Application): AndroidViewModel(application){
    val allReadings: LiveData<MutableList<Readings>>
    val repository: ReadingsRepository

    init {
        val dao= ReadingsDatabase.getInstance(application).getReadingsDao()
        repository= ReadingsRepository(dao)
        allReadings=repository.allReadings

    }
    fun deleteReading(readings:Readings)=viewModelScope.launch{
        repository.delete(readings)
    }
    fun updateReading(readings: Readings)=viewModelScope.launch {
        repository.update(readings)
    }
    fun addReading(readings: Readings)=viewModelScope.launch {
        repository.insert(readings)
    }

}