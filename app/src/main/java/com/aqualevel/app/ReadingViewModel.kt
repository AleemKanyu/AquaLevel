package com.aqualevel.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/**
 * ViewModel for managing and providing water level reading data to the UI.
 * It interacts with the [ReadingsRepository] to fetch and persist data from Supabase.
 */
class ReadingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ReadingsRepository
    private val authRepository = AuthRepository()
    private val deviceRepository = DeviceRepository()

    /** 
     * LiveData containing hourly readings from Room.
     */
    val hourlyReadings: LiveData<List<HourlyReadingEntity>>

    /** 
     * LiveData containing all readings from Room.
     */
    val allReadings: LiveData<List<Readings>>

    /** 
     * LiveData containing daily usage for the last 7 days from Room.
     */
    val weeklyUsage: LiveData<List<DailyUsageEntity>>

    private val _selectedDeviceId = MutableStateFlow<String?>(null)
    val selectedDeviceId: StateFlow<String?> = _selectedDeviceId

    private val _selectedDeviceName = MutableStateFlow("My Tank")
    val selectedDeviceName: StateFlow<String> = _selectedDeviceName

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _refreshTrigger = androidx.lifecycle.MutableLiveData<Unit>()
    val refreshTrigger: LiveData<Unit> = _refreshTrigger

    private var realtimeJob: kotlinx.coroutines.Job? = null

    init {
        val dao = ReadingsDatabase.getInstance(application).getReadingsDao()
        repository = ReadingsRepository(dao)

        // Single source of truth: Room flows converted to LiveData
        hourlyReadings = repository.hourlyReadings.asLiveData()
        weeklyUsage = repository.dailyUsages.asLiveData()
        allReadings = repository.allReadings

        loadUserDevices()
    }

    /**
     * Loads ALL paired devices from SharedPreferences.
     * Each paired device is stored as "deviceId|deviceName" in the "paired_devices" StringSet.
     */
    fun loadUserDevices() {
        viewModelScope.launch {
            val sharedPref = getApplication<Application>().getSharedPreferences(
                "AquaLevelPrefs", android.content.Context.MODE_PRIVATE
            )

            // Load all paired devices from the StringSet
            val pairedSet = sharedPref.getStringSet("paired_devices", emptySet()) ?: emptySet()
            val pairedDevices = pairedSet.mapNotNull { entry ->
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    Device(id = parts[0], name = parts[1], paired = true)
                } else null
            }.sortedBy { it.name }

            // Migrate: if we have a single saved device but no paired_devices set, add it
            val savedDeviceId = sharedPref.getString("selected_device_id", null)
            if (pairedDevices.isEmpty() && savedDeviceId != null) {
                val savedName = sharedPref.getString("selected_device_name", "My Tank") ?: "My Tank"
                val migratedDevice = Device(id = savedDeviceId, name = savedName, paired = true)
                _devices.value = listOf(migratedDevice)
                addDeviceToPairedSet(savedDeviceId, savedName)
                selectDevice(savedDeviceId)
                return@launch
            }

            _devices.value = pairedDevices

            // Select the previously selected device, or first available
            val currentSelected = savedDeviceId ?: pairedDevices.firstOrNull()?.id
            if (currentSelected != null) {
                selectDevice(currentSelected)
            }
        }
    }

    /**
     * Adds a device to the persistent "paired_devices" StringSet.
     */
    fun addDeviceToPairedSet(deviceId: String, deviceName: String) {
        val sharedPref = getApplication<Application>().getSharedPreferences(
            "AquaLevelPrefs", android.content.Context.MODE_PRIVATE
        )
        val existing = sharedPref.getStringSet("paired_devices", emptySet())?.toMutableSet() ?: mutableSetOf()
        // Remove old entry for this device ID if name changed
        existing.removeAll { it.startsWith("$deviceId|") }
        existing.add("$deviceId|$deviceName")
        sharedPref.edit().putStringSet("paired_devices", existing).apply()
    }

    fun selectDevice(deviceId: String) {
        _selectedDeviceId.value = deviceId
        
        // Find the device name
        val device = _devices.value.find { it.id == deviceId }
        val deviceName = device?.name ?: "My Tank"
        _selectedDeviceName.value = deviceName
        
        // Save selected device ID to SharedPreferences
        val sharedPref = getApplication<Application>().getSharedPreferences("AquaLevelPrefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit()
            .putString("selected_device_id", deviceId)
            .putString("selected_device_name", deviceName)
            .apply()
        
        // Sync historical data from Supabase
        viewModelScope.launch {
            repository.syncDeviceData(getApplication(), deviceId)
        }
        
        // Start real-time updates
        startRealtimeUpdates(deviceId)
    }

    private fun startRealtimeUpdates(deviceId: String) {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            var delayMs = 2000L
            while (isActive) {
                try {
                    repository.realtimeReadingsFlow(deviceId).collect { reading ->
                        // Reset backoff delay on successful emission
                        delayMs = 2000L
                        // Insert the new raw reading directly into Room DB
                        // LiveData observers will fire immediately — no network roundtrip needed
                        repository.insert(
                            Readings(
                                level = reading.distanceCm,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("ReadingViewModel", "Realtime updates failed for device $deviceId, retrying in ${delayMs}ms", e)
                    kotlinx.coroutines.delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(30000L) // Exponential backoff up to 30s
                }
            }
        }
    }

    /** Triggers a refresh if needed (e.g., manual pull-to-refresh) */
    fun refreshAllReadings() {
        _refreshTrigger.value = Unit
        val deviceId = _selectedDeviceId.value
        if (deviceId != null) {
            viewModelScope.launch {
                repository.syncDeviceData(getApplication(), deviceId)
            }
        }
    }

    /**
     * Adds a new water level reading (kept for compatibility).
     */
    fun addReading(readings: Readings) = viewModelScope.launch {
        repository.insert(readings)
    }
}

