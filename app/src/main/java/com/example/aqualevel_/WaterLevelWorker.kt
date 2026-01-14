package com.example.aqualevel_

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore

/**
 * WaterLevelWorker
 *
 * This worker runs in the BACKGROUND.
 * It does NOT have UI access.
 * It is triggered periodically by WorkManager.
 */
class WaterLevelWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {



    /**
     * This function is called by Android when the worker runs.
     * Put ALL background logic here.
     */
    override fun doWork(): Result {


        // Get Firestore instance
        val db = FirebaseFirestore.getInstance()

        // Reference to the document where ESP32 writes data
        val docRef = db
            .collection("Current Water Level")
            .document("Current Reading")

        // Fetch data ONCE (no listeners in workers)
        val task = docRef.get()

        // Workers must finish synchronously,
        // so we wait until Firestore responds
        while (!task.isComplete) {
            Thread.sleep(50)
        }

        // If Firestore request failed, retry later
        if (!task.isSuccessful) {
            return Result.retry()
        }

        // Extract water level value
        val level = task.result?.getDouble("Water_Level")
            ?: return Result.success()

        // Threshold logic (CHANGE THIS VALUE AS NEEDED)
        if(level==9000.00){
            showNotification(level)
        }
        if(level <=400){
        }else  if (level < 200) {
            showNotification(level)
        }

        // Tell Android: task completed successfully
        return Result.success()
    }

    /**
     * Shows a system notification when water is low
     */
    private fun showNotification(level: Double) {

        val channelId = "water_level_alerts"

        // Android 8+ requires notification channels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Water Level Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )

            val manager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager

            manager.createNotificationChannel(channel)
        }

        // Build the notification
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // replace if needed
            .setContentTitle("Water Level Alert")
            .setContentText("Water level $level litres")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // Show the notification
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        manager.notify(1, notification)
    }
}
