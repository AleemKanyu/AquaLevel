package com.aqualevel.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking

/**
 * Background worker that periodically fetches water level data from Supabase.
 * It updates the home screen widget and sends notifications if the level is critical.
 */
class WaterLevelWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    /**
     * Executes the background task of fetching data and processing alerts.
     */
    override fun doWork(): Result {
        val sharedPref = applicationContext.getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val deviceId = sharedPref.getString("selected_device_id", null) ?: return Result.success()

        val db = ReadingsDatabase.getInstance(applicationContext)
        val dao = db.getReadingsDao()
        val repository = ReadingsRepository(dao)

        try {
            runBlocking {
                repository.syncDeviceData(applicationContext, deviceId)
            }
        } catch (e: Exception) {
            return Result.retry()
        }

        val latestReadingList = try {
            runBlocking {
                repository.getRecentReadings(deviceId, limit = 1)
            }
        } catch (e: Exception) {
            return Result.retry()
        }

        val latestReading = latestReadingList.firstOrNull() ?: return Result.success()

        val distance = latestReading.distanceCm
        val timestamp = parseIsoTimestamp(latestReading.recordedAt)

        val diffMillis = System.currentTimeMillis() - timestamp
        val ageSeconds = diffMillis / 1000

        // Parse calibration preferences
        val emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
        val fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
        val tankVolume = sharedPref.getInt("tank_volume", 2000).toDouble()
        val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)
        val alertThreshold = sharedPref.getInt("alert_threshold", 30)

        val clampedDistance = distance.coerceIn(fullDistance, emptyDistance)
        val percent = (((emptyDistance - clampedDistance) / (emptyDistance - fullDistance)) * 100.0).toInt()
        val volume = (percent / 100.0) * tankVolume

        // Update Shared Preferences for the Widget
        sharedPref.edit()
            .putInt("last_percentage", percent)
            .putInt("last_volume", volume.toInt())
            .putLong("last_update_timestamp", timestamp) 
            .apply()

        // Trigger widget update broadcast
        val intent = android.content.Intent(applicationContext, WaterLevelWidget::class.java)
        intent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = android.appwidget.AppWidgetManager.getInstance(applicationContext).getAppWidgetIds(android.content.ComponentName(applicationContext, WaterLevelWidget::class.java))
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        applicationContext.sendBroadcast(intent)

        // Only process historical data and notifications if data is fresh (< 10 mins old)
        if (ageSeconds <= 600) {
            val reading = Readings(
                level = distance,
                timestamp = System.currentTimeMillis()
            )

            runBlocking {
                dao.insert(reading)
            }
            
            val notificationHelper = NotificationHelper(applicationContext)
            val lastNotified = sharedPref.getInt("last_notified_level", -1)

            // Alert logic
            if (notificationsEnabled) {
                if (percent >= 100 && lastNotified != 100) {
                    notificationHelper.sendNotification("Tank Full!", "Your water tank is now 100% full.", 1001)
                    sharedPref.edit().putInt("last_notified_level", 100).apply()
                } else if (percent <= alertThreshold && lastNotified != alertThreshold) {
                    notificationHelper.sendNotification("Low Water Level", "Warning: Tank level is at ${percent}%.", 1002)
                    sharedPref.edit().putInt("last_notified_level", alertThreshold).apply()
                } else if (percent > alertThreshold && percent < 100) {
                    sharedPref.edit().putInt("last_notified_level", -1).apply()
                }
            }
        }

        return Result.success()
    }

    private fun parseIsoTimestamp(isoString: String?): Long {
        if (isoString == null) return System.currentTimeMillis()
        return try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(isoString)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    sdf.parse(isoString)?.time ?: System.currentTimeMillis()
                } catch (e3: Exception) {
                    System.currentTimeMillis()
                }
            }
        }
    }
}
