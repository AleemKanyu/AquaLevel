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

        val diffMillis = System.currentTimeMillis() - timestamp
        val ageSeconds = diffMillis / 1000

        // Parse preferences
        val sharedPref = applicationContext.getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
        val fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
        val tankVolume = sharedPref.getInt("tank_volume", 2000).toDouble()

        val clampedDistance = distance.coerceIn(fullDistance, emptyDistance)
        val percent = (((emptyDistance - clampedDistance) / (emptyDistance - fullDistance)) * 100.0).toInt()
        val volume = (percent / 100.0) * tankVolume

        // Always update Widget Data regardless of age, so user sees the last known state & time
        sharedPref.edit()
            .putInt("last_percentage", percent)
            .putInt("last_volume", volume.toInt())
            .putLong("last_update_timestamp", timestamp) // Use the data's timestamp, not current time, to show accuracy
            .apply()

        val intent = android.content.Intent(applicationContext, WaterLevelWidget::class.java)
        intent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = android.appwidget.AppWidgetManager.getInstance(applicationContext).getAppWidgetIds(android.content.ComponentName(applicationContext, WaterLevelWidget::class.java))
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        applicationContext.sendBroadcast(intent)

        // Only process historical data insertion and notifications if data is fresh (e.g., < 10 mins old)
        if (ageSeconds <= 600) {
            val db = ReadingsDatabase.getInstance(applicationContext)
            val dao = db.getReadingsDao()

            // Check if we should insert? Ideally we don't want to insert duplicates.
            // But the worker runs periodically (15m). If the sensor data hasn't changed timestamp, we shouldn't insert.
            // For now, let's keep the user's original logic but just fix the time check.
            
            // Note: Preventing duplicate insertions would be better, but let's stick to the fix first.
            val reading = Readings(
                level = distance,
                timestamp = System.currentTimeMillis()
            )

            runBlocking {
                dao.insert(reading)
            }
            
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
