package com.example.aqualevel

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking

class WaterLevelWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val firestore = FirebaseFirestore.getInstance()
        val docRef = firestore.collection("sensorData").document("esp32_01")

        val snapshot = try {
            Tasks.await(docRef.get())
        } catch (e: Exception) {
            return Result.retry()
        }

        if (!snapshot.exists()) return Result.success()

        val distance = snapshot.getDouble("distance") ?: return Result.success()
        val timestamp = snapshot.getLong("timestamp") ?: return Result.success()

        val nowSeconds = System.currentTimeMillis() / 1000
        val ageSeconds = nowSeconds - timestamp

        // If data is fresh (less than 10 mins old), process it
        if (ageSeconds <= 600) {
            val db = ReadingsDatabase.getInstance(applicationContext)
            val dao = db.getReadingsDao()

            val reading = Readings(
                level = distance,
                timestamp = System.currentTimeMillis()
            )

            runBlocking {
                dao.insert(reading)
            }

            // --- Notification Logic for Background ---
            val sharedPref = applicationContext.getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
            val emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
            val fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
            
            val clampedDistance = distance.coerceIn(fullDistance, emptyDistance)
            val percent = (((emptyDistance - clampedDistance) / (emptyDistance - fullDistance)) * 100.0).toInt()
            
            val notificationHelper = NotificationHelper(applicationContext)
            val lastNotified = sharedPref.getInt("last_notified_level", -1)

            if (percent >= 100 && lastNotified != 100) {
                notificationHelper.sendNotification("Tank Full!", "Your water tank is now 100% full.", 1001)
                sharedPref.edit().putInt("last_notified_level", 100).apply()
            } else if (percent <= 30 && lastNotified != 30) {
                notificationHelper.sendNotification("Low Water Level", "Warning: Tank level is at ${percent}%.", 1002)
                sharedPref.edit().putInt("last_notified_level", 30).apply()
            } else if (percent in 31..99) {
                sharedPref.edit().putInt("last_notified_level", -1).apply()
            }
        }

        return Result.success()
    }
}
