package com.example.aqualevel

import androidx.lifecycle.LiveData

class ReadingsRepository (private val readingsDao: ReadingsDao){
    val allReadings: LiveData<List<Readings>> = readingsDao.fetchALL()
    val last7DaysUsage: LiveData<List<DailyUsage>> = readingsDao.getLast7DaysUsage()


    suspend fun insert(readings: Readings){
        readingsDao.insert(readings)
    }

}