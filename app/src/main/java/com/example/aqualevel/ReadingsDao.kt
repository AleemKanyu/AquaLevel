package com.example.aqualevel

import androidx.lifecycle.LiveData
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

interface ReadingsDao{
    @Insert
    fun insert(Readings: Readings)

    @Delete
    fun delete(Readings: Readings)
    @Update
    fun update(Readings: Readings)
    @Query("DELETE FROM Readings")
    fun clearAll(Readings: Readings)

    @Query("SELECT * FROM Readings ORDER BY id ASC")
    fun fetchALL(): LiveData<MutableList<Readings>>
}