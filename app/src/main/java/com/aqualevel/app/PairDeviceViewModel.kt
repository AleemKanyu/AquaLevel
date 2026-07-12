package com.aqualevel.app

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class PairUiState {
    object Idle    : PairUiState()
    object Loading : PairUiState()
    object Success : PairUiState()
    /** Device found in Firestore via QR scan — waiting for user to confirm + name the tank */
    data class DeviceFound(val device: Device) : PairUiState()
    data class Error(val message: String)       : PairUiState()
}

class PairDeviceViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository,
    private val authRepository:   AuthRepository
) : AndroidViewModel(application) {

    /** Secondary constructor used by the default AndroidViewModelFactory (Compose viewModel()). */
    constructor(application: Application) : this(application, DeviceRepository(), AuthRepository())

    private val _uiState = MutableStateFlow<PairUiState>(PairUiState.Idle)
    val uiState: StateFlow<PairUiState> get() = _uiState

    /**
     * Called when the QR scanner detects an AquaLevel QR deep link.
     * Validates the device ID format, then queries Firestore to confirm the
     * ESP32 is registered and online.
     */
    fun onQrScanned(rawUrl: String) {
        val deviceId = extractDeviceId(rawUrl)
        if (deviceId == null) {
            _uiState.value = PairUiState.Error("Invalid QR code — not an AquaLevel device.")
            return
        }
        lookupDevice(deviceId)
    }

    /**
     * Decodes a QR code image from gallery Uri using ML Kit.
     */
    fun onQrImageSelected(context: Context, uri: android.net.Uri) {
        _uiState.value = PairUiState.Loading
        try {
            val image = com.google.mlkit.vision.common.InputImage.fromFilePath(context, uri)
            val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val qrCode = barcodes.firstOrNull { it.format == com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE }
                    val rawValue = qrCode?.rawValue
                    if (rawValue != null) {
                        onQrScanned(rawValue)
                    } else {
                        _uiState.value = PairUiState.Error("No valid QR code found in this image.")
                    }
                }
                .addOnFailureListener { e ->
                    _uiState.value = PairUiState.Error("Failed to parse image: ${e.localizedMessage}")
                }
        } catch (e: Exception) {
            _uiState.value = PairUiState.Error("Error loading image: ${e.localizedMessage}")
        }
    }

    /** Called when the user manually types or pastes a device ID. */
    fun onManualDeviceId(deviceId: String) {
        val trimmed = deviceId.trim().uppercase()
        if (!trimmed.matches(Regex("AQL-[A-F0-9]{8}"))) {
            _uiState.value = PairUiState.Error("Invalid device ID. Format: AQL-XXXXXXXX")
            return
        }
        lookupDevice(trimmed)
    }

    private fun lookupDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = PairUiState.Loading
            val device = deviceRepository.getDeviceById(deviceId)
            _uiState.value = if (device != null) {
                PairUiState.DeviceFound(device)
            } else {
                PairUiState.Error(
                    "Device \"$deviceId\" not found.\n" +
                    "Make sure the ESP32 is powered on and connected to WiFi."
                )
            }
        }
    }

    fun pairDevice(tankName: String, onPairSuccess: () -> Unit) {
        val state    = _uiState.value
        val deviceId = when (state) {
            is PairUiState.DeviceFound -> state.device.id
            else -> {
                _uiState.value = PairUiState.Error("No device selected.")
                return
            }
        }

        // Anonymous uid is fine; empty string means device won't be user-scoped in Firestore
        val userId = authRepository.currentUserId() ?: ""

        if (tankName.isBlank()) {
            _uiState.value = PairUiState.Error("Please enter a name for this tank.")
            return
        }

        val ctx = getApplication<Application>().applicationContext
        viewModelScope.launch {
            _uiState.value = PairUiState.Loading
            deviceRepository.pairDevice(deviceId, userId, tankName, ctx)
                .onSuccess {
                    // Also save to the paired_devices set for the device switcher
                    val prefs = ctx.getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
                    val existing = prefs.getStringSet("paired_devices", emptySet())?.toMutableSet() ?: mutableSetOf()
                    existing.removeAll { it.startsWith("$deviceId|") }
                    existing.add("$deviceId|$tankName")
                    prefs.edit().putStringSet("paired_devices", existing).apply()

                    _uiState.value = PairUiState.Success
                    onPairSuccess()
                }
                .onFailure {
                    _uiState.value = PairUiState.Error(it.message ?: "Pairing failed.")
                }
        }
    }

    /** Pre-load the screen with a device ID that arrived via deep link. */
    fun preloadDeviceId(deviceId: String) {
        if (_uiState.value == PairUiState.Idle) {
            lookupDevice(deviceId)
        }
    }

    fun reset() {
        _uiState.value = PairUiState.Idle
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun extractDeviceId(url: String): String? {
        // Accepts both "aqualevel://pair?id=AQL-XXXXXXXX" and bare "AQL-XXXXXXXX"
        val fromUrl = Regex("id=(AQL-[A-F0-9]{8})", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.get(1)?.uppercase()
        if (fromUrl != null) return fromUrl

        val bare = url.trim().uppercase()
        return if (bare.matches(Regex("AQL-[A-F0-9]{8}"))) bare else null
    }
}
