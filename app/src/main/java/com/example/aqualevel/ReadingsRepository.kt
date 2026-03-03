package com.example.aqualevel

import android.util.Log
import androidx.lifecycle.LiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository class that abstracts access to the data sources (Firestore and Room).
 * It provides a clean API for data access and maintains Room as the single source of truth.
 */
class ReadingsRepository(private val readingsDao: ReadingsDao) {

    private val db = FirebaseFirestore.getInstance()
    private val sensorDataRef = db.collection("sensorData").document("esp32_01")

    /**
     * LiveData of all readings from Room.
     */
    val allReadings: LiveData<List<Readings>> = readingsDao.fetchALL()

    /**
     * Flow of hourly readings from Room.
     */
    val hourlyReadings: Flow<List<HourlyReadingEntity>> = readingsDao.getHourlyReadings()

    /**
     * Flow of last 7 days usage from Room.
     */
    val dailyUsages: Flow<List<DailyUsageEntity>> = readingsDao.getLast7DaysDailyUsage()

    /**
     * Starts listening to Firestore updates and syncs them to Room.
     */
    fun startSyncing() {
        listenToCurrentReading()
        listenToHourlyReadings()
        fetchInitialDailyHistory()
    }

    /**
     * Listens to the main document for real-time tanker updates.
     */
    private fun listenToCurrentReading() {
        sensorDataRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("ReadingsRepository", "Listen to current failed.", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val distance = snapshot.getDouble("distance") ?: 0.0
                val timestamp = snapshot.getLong("timestamp") ?: System.currentTimeMillis()
                
                CoroutineScope(Dispatchers.IO).launch {
                    // This updates the 'readings' table which HomeFragment will now observe for the tanker level
                    readingsDao.insert(Readings(level = distance, timestamp = timestamp))
                }
            }
        }
    }

    private fun listenToHourlyReadings() {
        sensorDataRef.collection("hourly_current")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ReadingsRepository", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val hourlyList = snapshots.documents.mapNotNull { doc ->
                        val distance = doc.getDouble("distance") ?: 0.0
                        val timestamp = doc.getLong("timestamp") ?: 0L
                        HourlyReadingEntity(hour = doc.id, distance = distance, timestamp = timestamp)
                    }
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        readingsDao.insertHourlyReadings(hourlyList)
                        updateDailyUsageFromHourly(hourlyList)
                    }
                }
            }
    }

    private suspend fun updateDailyUsageFromHourly(hourlyList: List<HourlyReadingEntity>) {
        if (hourlyList.isEmpty()) return

        val sorted = hourlyList.sortedBy { it.hour.toIntOrNull() ?: 0 }
        var totalUsageDist = 0.0

        for (i in 1 until sorted.size) {
            val diff = sorted[i].distance - sorted[i - 1].distance
            if (diff > 0) {
                totalUsageDist += diff
            }
        }

        // hourly_current always holds today's data, so use today's local date directly.
        // (ESP32 timestamps are Unix seconds; Date() needs ms — guard against that too.)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStr = sdf.format(Date()) // today

        val dailyEntry = DailyUsageEntity(
            date = dateStr,
            totalDistance = totalUsageDist,
            readingCount = hourlyList.size
        )

        // 1. Update local Room database
        readingsDao.insertDailyUsages(listOf(dailyEntry))

        // 2. Write back to Firebase so the web dashboard can read it
        val firestoreData = hashMapOf(
            "date" to dateStr,
            "totalDistance" to totalUsageDist,
            "readingCount" to hourlyList.size,
            "lastUpdatedMs" to System.currentTimeMillis()
        )
        try {
            sensorDataRef.collection("daily").document(dateStr).set(firestoreData).await()
            Log.d("ReadingsRepository", "Daily usage written to Firebase: $dateStr -> $totalUsageDist cm")
        } catch (e: Exception) {
            Log.w("ReadingsRepository", "Failed to write daily usage to Firebase", e)
        }
    }


    private fun fetchInitialDailyHistory() {
        sensorDataRef.collection("daily")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(7)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ReadingsRepository", "Daily history listener failed.", e)
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val dailyList = snapshots.documents.mapNotNull { doc ->
                        val totalDistance = doc.getDouble("totalDistance") ?: 0.0
                        val readingCount = doc.getLong("readingCount")?.toInt() ?: 0
                        val date = doc.getString("date") ?: doc.id
                        DailyUsageEntity(date = date, totalDistance = totalDistance, readingCount = readingCount)
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        readingsDao.insertDailyUsages(dailyList)
                        Log.d("ReadingsRepository", "Daily history synced: ${dailyList.size} entries")
                    }
                }
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
