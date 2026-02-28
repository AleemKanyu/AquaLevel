package com.example.aqualevel

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hourly_readings")
data class HourlyReadingEntity(
    @PrimaryKey val hour: String, // Document IDs: "00" to "23"
    val distance: Double,
    val timestamp: Long
)
