package com.example.aqualevel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.util.Log
import android.content.ComponentName // Added this import

/**
 * Implementation of the App Widget to display current water level on the home screen.
 * Updates are triggered periodically by the system or manually via broadcasts.
 */
class WaterLevelWidget : AppWidgetProvider() {

    private val TAG = "WaterLevelWidget"

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enqueue periodic work to update the widget
        Log.d(TAG, "onEnabled: Scheduling periodic work for water level updates.")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Require network for data fetching
            .build()

        val repeatingRequest = PeriodicWorkRequest.Builder(
            WaterLevelUpdateWorker::class.java,
            30, TimeUnit.MINUTES // Defined in appwidget_info.xml as 1800000ms = 30 minutes
        )
            .addTag(WaterLevelUpdateWorker.WORK_NAME) // Use the tag defined in the worker
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WaterLevelUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // Replace existing work if any
            repeatingRequest
        )
        
        // Also trigger an immediate update when the widget is first enabled/added
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context, WaterLevelWidget::class.java.name)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
        if (appWidgetIds.isNotEmpty()) {
            Log.d(TAG, "onEnabled: Triggering immediate widget update.")
            WaterLevelUpdateWorker.enqueueOnce(context) // Corrected call
        }
    }

    override fun onDisabled(context: Context) {
        // Cancel all work associated with this widget when the last instance is removed
        Log.d(TAG, "onDisabled: Cancelling periodic work for water level updates.")
        WorkManager.getInstance(context).cancelUniqueWork(WaterLevelUpdateWorker.WORK_NAME)
    }
}

/**
 * Updates the visual state of a specific App Widget.
 * Fetches the latest level and volume from SharedPreferences.
 * 
 * @param context The context used for resources and SharedPreferences.
 * @param appWidgetManager The manager used to push updates.
 * @param appWidgetId The ID of the widget being updated.
 */
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    try {
        val prefs = context.getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
    
        // Default calibration values
        val emptyDistance = prefs.getFloat("empty_distance", 130.0f).toDouble()
        val fullDistance = prefs.getFloat("full_distance", 20.0f).toDouble()
        val tankVolume = prefs.getInt("tank_volume", 2000).toDouble()
    
        val percentage = prefs.getInt("last_percentage", 0)
        val volume = prefs.getInt("last_volume", 0)
        val timestamp = prefs.getLong("last_update_timestamp", System.currentTimeMillis())

        // Widget Settings
        val showPercentage = prefs.getBoolean("widget_show_percentage", true)
        val showVolume = prefs.getBoolean("widget_show_volume", true)
        val showTimestamp = prefs.getBoolean("widget_show_timestamp", true)
        val widgetTheme = prefs.getString("widget_theme", "dark")

        val views = RemoteViews(context.packageName, R.layout.widget_water_level)
        
        // Apply Theme
        val isDarkTheme = widgetTheme == "dark"
        if (isDarkTheme) {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_simple_dark)
            
            // White/Light Text for Dark Theme
            views.setTextColor(R.id.widget_percentage, android.graphics.Color.WHITE)
            views.setTextColor(R.id.widget_percentage_symbol, android.graphics.Color.WHITE)
            views.setTextColor(R.id.widget_capacity, 0xFFDDDDDD.toInt()) // Light Grey
            views.setTextColor(R.id.widget_timestamp, 0xFFBBBBBB.toInt()) // Lighter Grey
             
        } else {
            // Light Theme
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_simple_light)
            
            // Dark Text for Light Theme
            val darkColor = androidx.core.content.ContextCompat.getColor(context, R.color.duo_text_primary)
            val secondaryColor = androidx.core.content.ContextCompat.getColor(context, R.color.duo_text_secondary)
            val actionColor = androidx.core.content.ContextCompat.getColor(context, R.color.duo_blue)

            views.setTextColor(R.id.widget_percentage, actionColor)
            views.setTextColor(R.id.widget_percentage_symbol, actionColor)
            views.setTextColor(R.id.widget_capacity, secondaryColor)
            views.setTextColor(R.id.widget_timestamp, secondaryColor)
        }
        
        // Update UI components
        views.setTextViewText(R.id.widget_percentage, percentage.toString())
        views.setTextViewText(R.id.widget_capacity, "$volume L")
        
        // Visibility Logic
        views.setViewVisibility(R.id.widget_percentage_container, if (showPercentage) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widget_capacity, if (showVolume) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.widget_timestamp, if (showTimestamp) android.view.View.VISIBLE else android.view.View.GONE)
        
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        views.setTextViewText(R.id.widget_timestamp, sdf.format(Date(timestamp)))

        // Click on widget opens MainActivity
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
