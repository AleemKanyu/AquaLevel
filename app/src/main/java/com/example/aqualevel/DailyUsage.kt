package com.example.aqualevel

data class DailyUsage(
    val day: String,      // e.g. "2026-02-08"
    val minLevel: Double,
    val maxLevel: Double
)
