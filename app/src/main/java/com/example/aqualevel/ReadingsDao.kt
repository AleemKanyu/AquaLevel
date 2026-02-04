package com.example.aqualevel

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update

interface readingsDao{
    @Insert
    fun insert(Readings: Readings)

    @Delete
    fun delete(Readings: Readings)
    @Update
    fun update(Readings: Readings)
}