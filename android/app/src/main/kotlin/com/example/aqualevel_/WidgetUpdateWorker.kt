package com.example.aqualevel_

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class WidgetUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        private const val PREF_NAME = "FlutterSharedPreferences"
        private const val PREF_EMPTY_DISTANCE = "flutter.pref_empty_distance"
        private const val PREF_FULL_DISTANCE = "flutter.pref_full_distance"
        private const val PREF_TANK_VOLUME = "flutter.pref_tank_volume"
        private const val PREF_LAST_PERCENTAGE = "flutter.widget_last_percentage"
        private const val PREF_LAST_LITERS = "flutter.widget_last_liters"
        
        // Flutter stores doubles as Long bits internally
        private fun getDoubleFromPrefs(prefs: android.content.SharedPreferences, key: String, default: Double): Double {
            return try {
                val longBits = prefs.getLong(key, java.lang.Double.doubleToRawLongBits(default))
                java.lang.Double.longBitsToDouble(longBits)
            } catch (e: ClassCastException) {
                try {
                    prefs.getString(key, default.toString())?.toDoubleOrNull() ?: default
                } catch (e2: Exception) {
                    default
                }
            }
        }
        
        private fun getIntFromPrefs(prefs: android.content.SharedPreferences, key: String, default: Int): Int {
            return try {
                // Flutter stores int as Long
                prefs.getLong(key, default.toLong()).toInt()
            } catch (e: ClassCastException) {
                try {
                    prefs.getInt(key, default)
                } catch (e2: ClassCastException) {
                    try {
                        prefs.getString(key, default.toString())?.toIntOrNull() ?: default
                    } catch (e3: Exception) {
                        default
                    }
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting widget update work")
        
        return try {
            // Initialize Firebase
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            
            val db = FirebaseFirestore.getInstance()
            
            // Fetch latest reading from Firestore
            val document = db.collection("sensor_data")
                .document("latest_reading")
                .get()
                .await()
            
            if (document.exists()) {
                val distance = document.getDouble("distance") ?: 0.0
                Log.d(TAG, "Got distance from Firestore: $distance")
                
                // Get calibration settings from SharedPreferences
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val emptyDistance = getDoubleFromPrefs(prefs, PREF_EMPTY_DISTANCE, 130.0)
                val fullDistance = getDoubleFromPrefs(prefs, PREF_FULL_DISTANCE, 20.0)
                val tankVolume = getIntFromPrefs(prefs, PREF_TANK_VOLUME, 1000)
                
                // Calculate percentage
                val percentage = calculatePercentage(distance, emptyDistance, fullDistance)
                val liters = (percentage / 100.0) * tankVolume
                
                // Save to SharedPreferences as Long (Flutter format)
                prefs.edit()
                    .putLong(PREF_LAST_PERCENTAGE, java.lang.Double.doubleToRawLongBits(percentage))
                    .putLong(PREF_LAST_LITERS, java.lang.Double.doubleToRawLongBits(liters))
                    .apply()
                
                // Update all widgets
                updateAllWidgets(percentage, liters)
                
                Log.d(TAG, "Widget updated: ${percentage.toInt()}%, ${liters.toInt()}L")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget: ${e.message}")
            Result.retry()
        }
    }

    private fun calculatePercentage(distance: Double, emptyDistance: Double, fullDistance: Double): Double {
        if (emptyDistance <= fullDistance) return 0.0
        val range = emptyDistance - fullDistance
        val currentLevel = emptyDistance - distance
        return ((currentLevel / range) * 100).coerceIn(0.0, 100.0)
    }

    private fun updateAllWidgets(percentage: Double, liters: Double) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, TankWidgetProvider::class.java)
        )
        
        for (widgetId in widgetIds) {
            val views = RemoteViews(context.packageName, R.layout.tank_widget_layout)
            
            views.setTextViewText(R.id.percentage_text, "${percentage.toInt()}%")
            views.setTextViewText(R.id.liters_text, "${liters.toInt()} L")
            views.setTextViewText(R.id.status_text, "Live")
            
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
