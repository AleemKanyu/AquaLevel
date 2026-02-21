package com.example.aqualevel

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.lifecycle.asFlow
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.work.OneTimeWorkRequestBuilder // Correct import
import androidx.work.WorkManager // Correct import

/**
 * A WorkManager Worker to fetch the latest water level data and update the App Widget.
 */
class WaterLevelUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "WaterLevelUpdateWorker"

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val database = ReadingsDatabase.getInstance(applicationContext)
                val readingsDao = database.getReadingsDao()
                val repository = ReadingsRepository(readingsDao)

                // Get the latest reading
                // Using LiveData.asFlow().first() to get the current value without observing forever
                val latestReadingsList = repository.allReadings.asFlow().first()
                val latestReading = latestReadingsList.firstOrNull()

                if (latestReading == null) {
                    Log.d(TAG, "No readings found in the database.")
                    // Even if no readings, attempt to update the widget to show defaults
                    updateWidgetUI()
                    return@withContext Result.success()
                }

                val prefs = applicationContext.getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()

                // Default calibration values from SharedPreferences
                val emptyDistance = prefs.getFloat("empty_distance", 130.0f).toDouble()
                val fullDistance = prefs.getFloat("full_distance", 20.0f).toDouble()
                val tankVolume = prefs.getInt("tank_volume", 2000).toDouble()

                val currentLevelDistance = latestReading.level

                // Calculate percentage
                val percentage: Int
                val volume: Int

                if (emptyDistance == fullDistance) {
                    percentage = 0 // Avoid division by zero
                    volume = 0
                } else {
                    val rawPercentage = ((emptyDistance - currentLevelDistance) / (emptyDistance - fullDistance)) * 100
                    percentage = rawPercentage.roundToInt().coerceIn(0, 100) // Clamp between 0 and 100
                    volume = ((percentage / 100.0) * tankVolume).roundToInt()
                }

                editor.putInt("last_percentage", percentage)
                editor.putInt("last_volume", volume)
                editor.putLong("last_update_timestamp", latestReading.timestamp)
                editor.apply()

                Log.d(TAG, "Worker updated prefs: percentage=$percentage, volume=$volume, timestamp=${latestReading.timestamp}")

                // Trigger widget update
                updateWidgetUI()

                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating water level widget", e)
                Result.failure()
            }
        }
    }

    private fun updateWidgetUI() {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val thisAppWidget = ComponentName(applicationContext.packageName, WaterLevelWidget::class.java.name)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
        if (appWidgetIds.isNotEmpty()) {
            Log.d(TAG, "Triggering widget update for ${appWidgetIds.size} widgets.")
            WaterLevelWidget().onUpdate(applicationContext, appWidgetManager, appWidgetIds)
        } else {
            Log.d(TAG, "No active widgets to update.")
        }
    }

    companion object {
        // Define a tag for this worker
        const val WORK_NAME = "WaterLevelUpdateWorker"

        // Helper to enqueue a one-time work request for immediate update
        fun enqueueOnce(context: Context) {
            val oneTimeRequest = OneTimeWorkRequestBuilder<WaterLevelUpdateWorker>()
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueue(oneTimeRequest)
        }
    }
}
