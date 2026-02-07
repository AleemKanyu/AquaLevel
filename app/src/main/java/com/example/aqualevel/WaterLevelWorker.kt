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

        val docRef = firestore
            .collection("sensorData")
            .document("esp32_01")

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

        if (ageSeconds > 600) return Result.success()

        val db = ReadingsDatabase.getInstance(applicationContext)
        val dao = db.getReadingsDao()

        val reading = Readings(
            level = distance,
            timestamp = System.currentTimeMillis()
        )

        runBlocking {
            dao.insert(reading)
        }

        return Result.success()
    }
}
