package com.example.aqualevel

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single water level reading from the sensor.
 * This is a Room entity used to persist data locally.
 *
 * @property id Unique identifier for the reading (auto-generated).
 * @property level The distance from the sensor to the water surface in cm.
 * @property timestamp The time when the reading was recorded (in milliseconds).
 */
@Entity(tableName = "readings")
data class Readings(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val level: Double,
    val timestamp: Long
)
