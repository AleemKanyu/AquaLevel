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
        listenToHourlyReadings()
        listenToDailyUsage()
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
                    }
                }
            }
    }

    private fun listenToDailyUsage() {
        sensorDataRef.collection("daily")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(7)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ReadingsRepository", "Listen failed.", e)
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
                    }
                }
            }
    }

    // Keep old methods if they are still needed for other parts of the app
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
