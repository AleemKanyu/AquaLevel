package com.example.aqualevel

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "readings")
data class Readings(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val level: Double,
    val timestamp: Long
)
