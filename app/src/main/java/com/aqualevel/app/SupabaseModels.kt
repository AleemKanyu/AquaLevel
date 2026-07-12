package com.aqualevel.app

data class Device(
    val id: String = "",
    val userId: String? = null,
    val name: String = "My Tank",
    val capacityLiters: Double = 0.0,
    val dEmpty: Double = 0.0,
    val dFull: Double = 0.0,
    val createdAt: String? = null,
    val sharedWith: List<String> = emptyList(),
    val paired: Boolean = false,
    val status: String = "offline",
    val firmware: String = ""
) {
    /** Deep-link URL encoded in the physical QR sticker on this ESP32. */
    val qrUrl: String get() = "aqualevel://pair?id=$id"
}

data class SupabaseReading(
    val id: Long = 0,
    val deviceId: String = "",
    val distanceCm: Double = 0.0,
    val recordedAt: String? = null,
    val status: String? = null
)

data class SupabaseHourlyReading(
    val deviceId: String = "",
    val date: String = "",
    val hour: Int = 0,
    val distance: Double = 0.0,
    val timestamp: Long = 0L
)

data class SupabaseDailyUsage(
    val deviceId: String = "",
    val date: String = "",
    val totalDistance: Double = 0.0,
    val readingCount: Int = 0,
    val lastUpdatedMs: Long = 0L
)
