package com.example.aqualevel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Implementation of App Widget functionality.
 */
class WaterLevelWidget : AppWidgetProvider() {
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
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    try {
        val prefs = context.getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
    
        // Default calibration values if not set
    val emptyDistance = prefs.getFloat("empty_distance", 130.0f).toDouble()
    val fullDistance = prefs.getFloat("full_distance", 20.0f).toDouble()
    val tankVolume = prefs.getInt("tank_volume", 2000).toDouble()
    
    // Get last known distance (if available, we might need to store this in prefs too, 
    // currently MainActivity stores 'last_notified_level' but not exact distance.
    // However, MainActivity calculates and displays. 
    // We need 'distance' or 'percent' stored in Prefs to be accessible here.
    // Let's assume we will update MainActivity to store 'last_percentage' and 'last_volume'
    
    val percentage = prefs.getInt("last_percentage", 0)
    val volume = prefs.getInt("last_volume", 0)
    val timestamp = prefs.getLong("last_update_timestamp", System.currentTimeMillis())

    val views = RemoteViews(context.packageName, R.layout.widget_water_level)
    
    // Update UI
    views.setTextViewText(R.id.widget_percentage, percentage.toString())
    views.setTextViewText(R.id.widget_capacity, "$volume L")
    
    // Programmatically tint the icon for compatibility
    views.setInt(R.id.widget_icon, "setColorFilter", android.graphics.Color.parseColor("#1CB0F6")) // duo_blue
    
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    views.setTextViewText(R.id.widget_timestamp, sdf.format(Date(timestamp)))

    // Click on widget opens MainActivity
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
