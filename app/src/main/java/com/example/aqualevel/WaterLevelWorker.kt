package com.example.aqualevel

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Background worker that:
 * - Fetches latest water level from Firestore
 * - Validates data freshness
 * - Performs logic checks (no notifications for now)
 *
 */
class WaterLevelWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {

        val db = FirebaseFirestore.getInstance()

        // Firestore document written by ESP32
        val docRef = db
            .collection("sensorData")
            .document("esp32_01")

        // Fetch document synchronously
        val snapshot = try {
            Tasks.await(docRef.get())
        } catch (e: Exception) {
            // Network / Firestore error → retry later
            return Result.retry()
        }

        if (!snapshot.exists()) {
            // Document missing → nothing to do
            return Result.success()
        }

        // Read fields safely
        val distance = snapshot.getDouble("distance") ?: return Result.success()
        val timestamp = snapshot.getLong("timestamp") ?: return Result.success()

        // ---- DATA FRESHNESS CHECK ----
        val nowSeconds = System.currentTimeMillis() / 1000
        val ageSeconds = nowSeconds - timestamp

        // Ignore stale data (older than 10 minutes)
        if (ageSeconds > 600) {
            return Result.success()
        }

        // ---- BUSINESS LOGIC PLACEHOLDER ----
        // alerts, analytics, sync, etc
        // Example:
        // if (distance < 200) { ... }

        // Worker completed successfully
        return Result.success()
    }
}
