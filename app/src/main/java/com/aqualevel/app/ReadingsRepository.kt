package com.aqualevel.app

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class ReadingsRepository(private val readingsDao: ReadingsDao) {

    private val db = AquaSupabase.firestore
    private val authRepository = AuthRepository()

    val allReadings: LiveData<List<Readings>> = readingsDao.fetchALL()
    val hourlyReadings: Flow<List<HourlyReadingEntity>> = readingsDao.getHourlyReadings()
    val dailyUsages: Flow<List<DailyUsageEntity>> = readingsDao.getLast7DaysDailyUsage()

    fun realtimeReadingsFlow(deviceId: String): Flow<SupabaseReading> = callbackFlow {
        if (deviceId.isBlank()) {
            close()
            return@callbackFlow
        }
        var registration: ListenerRegistration? = null

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                authRepository.ensureSignedIn().getOrThrow()
                registration = db.collection("devices")
                    .document(deviceId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }

                        val reading = snapshot?.takeIf { it.exists() }?.let {
                            val distance = it.getDouble("distance") ?: it.getDouble("distanceCm") ?: 0.0
                            val timestampSeconds = it.getLong("timestamp")
                            val recordedAt = timestampSeconds?.let { seconds -> Instant.ofEpochSecond(seconds).toString() }
                            SupabaseReading(
                                deviceId = deviceId,
                                distanceCm = distance,
                                recordedAt = recordedAt,
                                status = it.getString("status")
                            )
                        }

                        if (reading != null) {
                            trySendBlocking(reading)
                        }
                    }
            } catch (e: Exception) {
                close(e)
            }
        }

        awaitClose {
            registration?.remove()
        }
    }

    suspend fun getRecentReadings(deviceId: String, limit: Int = 100): List<SupabaseReading> {
        if (deviceId.isBlank()) return emptyList()
        return runCatching {
            authRepository.ensureSignedIn().getOrThrow()
            val snapshot = db.collection("devices").document(deviceId).get().await()
            if (!snapshot.exists()) return@runCatching emptyList<SupabaseReading>()

            val distance = snapshot.getDouble("distance") ?: snapshot.getDouble("distanceCm") ?: 0.0
            val timestampSeconds = snapshot.getLong("timestamp")
            val recordedAt = timestampSeconds?.let { seconds -> Instant.ofEpochSecond(seconds).toString() }

            listOf(
                SupabaseReading(
                    deviceId = deviceId,
                    distanceCm = distance,
                    recordedAt = recordedAt,
                    status = snapshot.getString("status")
                )
            )
        }.getOrElse { emptyList() }
    }

    suspend fun getTodayHourlyReadings(deviceId: String): List<SupabaseHourlyReading> {
        if (deviceId.isBlank()) return emptyList()
        val today = LocalDate.now().toString()
        return runCatching {
            authRepository.ensureSignedIn().getOrThrow()
            db.collection("devices")
                .document(deviceId)
                .collection("hourly")
                .whereEqualTo("date", today)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    // Hour from the 'hour' field, or parse from doc ID (e.g. "2026-07-12_13")
                    val hour = doc.getLong("hour")?.toInt()
                        ?: doc.id.substringAfterLast("_").toIntOrNull()
                        ?: return@mapNotNull null
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val distance = doc.getDouble("distance") ?: doc.getDouble("avgDistance") ?: 0.0
                    SupabaseHourlyReading(
                        deviceId = deviceId,
                        date = doc.getString("date") ?: today,
                        hour = hour,
                        distance = distance,
                        timestamp = timestamp
                    )
                }
                .sortedBy { it.hour }
        }.getOrElse { emptyList() }
    }

    suspend fun getWeeklyUsage(deviceId: String): List<SupabaseDailyUsage> {
        if (deviceId.isBlank()) return emptyList()
        val sevenDaysAgo = LocalDate.now().minusDays(7).toString()
        return runCatching {
            authRepository.ensureSignedIn().getOrThrow()
            db.collection("devices")
                .document(deviceId)
                .collection("daily")
                .whereGreaterThanOrEqualTo("date", sevenDaysAgo)
                .orderBy("date", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val date = doc.getString("date") ?: doc.id
                    SupabaseDailyUsage(
                        deviceId = deviceId,
                        date = date,
                        totalDistance = doc.getDouble("totalDrop") ?: doc.getDouble("totalDistance") ?: doc.getDouble("distance") ?: 0.0,
                        readingCount = (doc.getLong("readingCount") ?: 0L).toInt(),
                        lastUpdatedMs = doc.getLong("lastUpdatedMs") ?: 0L
                    )
                }
        }.getOrElse { emptyList() }
    }

    suspend fun syncDeviceData(context: Context, deviceId: String) {
        if (deviceId.isBlank()) return
        try {
            clearLocalData()

            val recent = getRecentReadings(deviceId)
            val rawReadings = recent.map {
                Readings(
                    level = it.distanceCm,
                    timestamp = parseIsoTimestamp(it.recordedAt)
                )
            }
            readingsDao.insertAll(rawReadings)

            val hourly = getTodayHourlyReadings(deviceId)
            val hourlyEntities = hourly.map {
                HourlyReadingEntity(
                    hour = it.hour.toString().padStart(2, '0'),
                    distance = it.distance,
                    timestamp = if (it.timestamp > 0) it.timestamp * 1000 else getHourlyTimestamp(it.date, it.hour)
                )
            }
            readingsDao.insertHourlyReadings(hourlyEntities)

            val weekly = getWeeklyUsage(deviceId)
            val dailyEntities = weekly.map {
                DailyUsageEntity(
                    date = it.date,
                    totalDistance = it.totalDistance,
                    readingCount = it.readingCount
                )
            }
            readingsDao.insertDailyUsages(dailyEntities)

            Log.d("ReadingsRepository", "Successfully synced all data for device $deviceId")
        } catch (e: Exception) {
            Log.e("ReadingsRepository", "Error syncing data for device $deviceId", e)
        }
    }

    suspend fun clearLocalData() {
        readingsDao.clearHourlyReadings()
        readingsDao.clearDailyUsage()
        readingsDao.clearReadings()
    }

    private fun parseIsoTimestamp(isoString: String?): Long {
        if (isoString == null) return System.currentTimeMillis()
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            try {
                isoString.toLong() * 1000L
            } catch (_: Exception) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    sdf.parse(isoString)?.time ?: System.currentTimeMillis()
                } catch (_: Exception) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        sdf.parse(isoString)?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                }
            }
        }
    }

    private fun getHourlyTimestamp(dateStr: String, hour: Int): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH", Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse("$dateStr $hour")?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    suspend fun insert(readings: Readings) {
        readingsDao.insert(readings)
    }

    suspend fun getAllReadingsSync(): List<Readings> {
        return readingsDao.getAllReadingsSync()
    }

    suspend fun insertAll(readingsList: List<Readings>) {
        readingsDao.insertAll(readingsList)
    }
}
