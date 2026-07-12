package com.aqualevel.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    @PrimaryKey val date: String, // Document ID format: "YYYY-MM-DD"
    val totalDistance: Double,
    val readingCount: Int
)
