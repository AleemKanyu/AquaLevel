package com.example.aqualevel_

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class TankWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "TankWidgetProvider"
        private const val ACTION_REFRESH = "com.example.aqualevel_.ACTION_REFRESH_WIDGET"
        private const val WORK_NAME = "widget_update_work"
        private const val PREF_NAME = "FlutterSharedPreferences"
        private const val PREF_LAST_PERCENTAGE = "flutter.widget_last_percentage"
        private const val PREF_LAST_LITERS = "flutter.widget_last_liters"
        
        // Flutter shared_preferences stores doubles with this prefix
        private const val FLUTTER_DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
        
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TankWidgetProvider::class.java)
            )
            
            val intent = Intent(context, TankWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(intent)
        }
        
        // Flutter shared_preferences stores doubles as strings with a special prefix
        private fun getDoubleFromPrefs(prefs: android.content.SharedPreferences, key: String, default: Double): Double {
            val all = prefs.all
            val value = all[key]
            
            Log.d(TAG, "Getting key $key, raw value: $value (type: ${value?.javaClass?.simpleName})")
            
            return when (value) {
                is Double -> value
                is Float -> value.toDouble()
                is Long -> java.lang.Double.longBitsToDouble(value)
                is Int -> value.toDouble()
                is String -> {
                    // Flutter stores doubles as: "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu" + doubleValue
                    val cleanValue = if (value.startsWith(FLUTTER_DOUBLE_PREFIX)) {
                        value.removePrefix(FLUTTER_DOUBLE_PREFIX)
                    } else {
                        value
                    }
                    Log.d(TAG, "Parsing double from string: $cleanValue")
                    cleanValue.toDoubleOrNull() ?: default
                }
                null -> default
                else -> {
                    Log.w(TAG, "Unknown type for key $key: ${value.javaClass.simpleName}")
                    default
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled, scheduling periodic updates")
        schedulePeriodicUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "All widgets removed, cancelling periodic updates")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        
        // Schedule periodic updates if not already scheduled
        schedulePeriodicUpdates(context)
        
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_REFRESH) {
            Log.d(TAG, "Manual refresh requested")
            updateAllWidgets(context)
        }
    }

    private fun schedulePeriodicUpdates(context: Context) {
        // Schedule updates every 15 minutes (minimum for WorkManager)
        val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            15, TimeUnit.MINUTES
        ).build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        
        Log.d(TAG, "Periodic widget updates scheduled (every 15 minutes)")
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d(TAG, "Updating widget ID: $appWidgetId")
        
        val views = RemoteViews(context.packageName, R.layout.tank_widget_layout)
        
        // Set up click to open app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        }
        
        // Set up refresh intent (tap on status text to force refresh)
        val refreshIntent = Intent(context, TankWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 1, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.status_text, refreshPendingIntent)
        
        // Read data from SharedPreferences
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        val percentage = getDoubleFromPrefs(prefs, PREF_LAST_PERCENTAGE, -1.0)
        val liters = getDoubleFromPrefs(prefs, PREF_LAST_LITERS, -1.0)
        
        Log.d(TAG, "Setting widget values: percentage=$percentage, liters=$liters")
        
        if (percentage >= 0) {
            val percentageText = "${percentage.toInt()}%"
            val litersText = "${liters.toInt()} L"
            
            Log.d(TAG, "Displaying: $percentageText, $litersText")
            
            views.setTextViewText(R.id.percentage_text, percentageText)
            views.setTextViewText(R.id.liters_text, litersText)
            views.setTextViewText(R.id.status_text, "Tap to refresh")
        } else {
            Log.d(TAG, "No data available, showing defaults")
            views.setTextViewText(R.id.percentage_text, "--")
            views.setTextViewText(R.id.liters_text, "Open app")
            views.setTextViewText(R.id.status_text, "Tap to load")
        }
        
        // Actually update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "Widget $appWidgetId updated successfully")
    }
}
