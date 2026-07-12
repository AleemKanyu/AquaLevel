package com.aqualevel.app

import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class DeviceRepository {
    private val db = AquaSupabase.firestore
    private val authRepository = AuthRepository()

    /** Pair (or share) a device with the given userId (may be anonymous or empty). */
    suspend fun pairDevice(deviceId: String, userId: String, name: String, context: android.content.Context): Result<Device> {
        if (deviceId.isBlank()) {
            return Result.failure(IllegalArgumentException("Device ID cannot be empty."))
        }
        return runCatching {
            authRepository.ensureSignedIn().getOrThrow()
            val deviceRef = db.collection("devices").document(deviceId)
            val snapshot  = deviceRef.get().await()
            val existing  = snapshot.toObject(Device::class.java)?.copy(id = deviceId)

            val updated = (existing ?: Device(id = deviceId)).copy(
                id     = deviceId,
                userId = userId.ifEmpty { existing?.userId },
                name   = name,
                paired = true
            )
            deviceRef.set(updated, SetOptions.merge()).await()

            // Always persist locally so the app works without re-fetching Firestore
            context.getSharedPreferences("AquaLevelPrefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("selected_device_id", deviceId)
                .putString("selected_device_name", name)
                .apply()

            updated
        }
    }

    /**
     * Check whether the ESP32 has written its device registration doc to Firestore.
     * Returns null if the device is not found / not registered yet.
     */
    suspend fun getDeviceById(deviceId: String): Device? {
        if (deviceId.isBlank()) return null
        return runCatching {
            authRepository.ensureSignedIn().getOrThrow()
            val snapshot = db.collection("devices").document(deviceId).get().await()
            if (snapshot.exists()) {
                snapshot.toObject(Device::class.java)?.copy(id = deviceId)
            } else null
        }.getOrNull()
    }

    /**
     * Returns true when a Firestore document exists for this device ID
     * (meaning the ESP32 has booted and registered itself).
     */
    suspend fun checkDeviceExists(deviceId: String): Boolean {
        if (deviceId.isBlank()) return false
        return try {
            authRepository.ensureSignedIn().getOrThrow()
            db.collection("devices").document(deviceId).get().await().exists()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getUserDevices(userId: String): List<Device> = runCatching {
        authRepository.ensureSignedIn().getOrThrow()
        val owned = db.collection("devices")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Device::class.java)?.copy(id = it.id) }

        val shared = db.collection("devices")
            .whereArrayContains("sharedWith", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Device::class.java)?.copy(id = it.id) }

        (owned + shared).distinctBy { it.id }
    }.getOrElse { emptyList() }

    suspend fun updateCalibration(
        deviceId: String,
        dEmpty: Double,
        dFull: Double,
        capacityLiters: Double
    ) {
        authRepository.ensureSignedIn().getOrThrow()
        db.collection("devices")
            .document(deviceId)
            .update(
                mapOf(
                    "dEmpty"          to dEmpty,
                    "dFull"           to dFull,
                    "capacityLiters"  to capacityLiters
                )
            )
            .await()
    }
}
