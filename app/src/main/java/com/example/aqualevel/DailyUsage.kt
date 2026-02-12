package com.example.aqualevel

/**
 * Data class representing the minimum and maximum water levels recorded on a specific day.
 * Used for calculating daily consumption.
 *
 * @property day The date string (e.g., "YYYY-MM-DD").
 * @property minLevel The lowest water level (highest distance from sensor) recorded that day.
 * @property maxLevel The highest water level (lowest distance from sensor) recorded that day.
 */
data class DailyUsage(
    val day: String,      // e.g. "2026-02-08"
    val minLevel: Double,
    val maxLevel: Double
)
