package com.example.aqualevel

import androidx.lifecycle.LiveData

class ReadingsRepository (private val readingsDao: ReadingsDao){
    val allReadings: LiveData<MutableList<Readings>> = readingsDao.fetchALL()

    suspend fun insert(readings: Readings){
        readingsDao.insert(readings)
    }
    suspend fun update(readings: Readings){
        readingsDao.update(readings)
    }
    suspend fun delete(readings: Readings){
        readingsDao.delete(readings)
    }
}