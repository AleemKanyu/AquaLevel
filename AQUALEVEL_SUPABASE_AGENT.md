# AQUALEVEL_SUPABASE_AGENT.md
# Agent Task: Migrate AquaLevel from per-user Firestore → Centralized Supabase Backend

## Context
AquaLevel is an Android water monitoring app (Kotlin + Jetpack Compose, MVVM).
Currently reads from the user's own Firestore project (`sensorData/esp32_01`).
Goal: Move all data to a single centralized Supabase project owned by the developer,
so users just install the app, register, and go — no Firebase setup required.

ESP32 devices are **pre-flashed** by the developer with a unique `device_id` (UUID)
and a shared `INGEST_SECRET` key. Users pair their device via QR code scan in the app.

---

## 1. Dependencies — Add to `app/build.gradle.kts`

```kotlin
// Supabase BOM
implementation(platform("io.github.jan-tennert.supabase:bom:2.6.1"))
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.github.jan-tennert.supabase:auth-kt")
implementation("io.github.jan-tennert.supabase:realtime-kt")
implementation("io.github.jan-tennert.supabase:storage-kt")

// Ktor engine (required by Supabase SDK)
implementation("io.ktor:ktor-client-android:2.3.12")

// QR code scanning for device pairing
implementation("com.google.mlkit:barcode-scanning:17.3.0")
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
implementation("androidx.camera:camera-view:1.3.4")
```

---

## 2. Supabase Client Singleton — `SupabaseClient.kt`

Create file: `app/src/main/java/com/example/aqualevel/SupabaseClient.kt`

```kotlin
package com.example.aqualevel

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.realtime.Realtime

object AquaSupabase {
    val client = createSupabaseClient(
        supabaseUrl = "https://delpozytgraqfxvyitmu.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRlbHBvenl0Z3JhcWZ4dnlpdG11Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk2MTk0NzcsImV4cCI6MjA5NTE5NTQ3N30.2NYLpJvlnajgSIbGuNf6hahA9Kan4BacT3r8Mv3XElE"
    ) {
        install(Postgrest)
        install(Auth)
        install(Realtime)
    }
}
```

Configured with your active project URL and anon public key.

---

## 3. Data Models — `SupabaseModels.kt`

Create file: `app/src/main/java/com/example/aqualevel/SupabaseModels.kt`

```kotlin
package com.example.aqualevel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,                          // UUID — the flashed device_id
    @SerialName("user_id") val userId: String? = null,
    val name: String = "My Tank",
    @SerialName("capacity_liters") val capacityLiters: Double = 0.0,
    @SerialName("d_empty") val dEmpty: Double = 0.0,
    @SerialName("d_full") val dFull: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Reading(
    val id: Long = 0,
    @SerialName("device_id") val deviceId: String,
    @SerialName("distance_cm") val distanceCm: Double,
    @SerialName("recorded_at") val recordedAt: String? = null
)

@Serializable
data class HourlyReading(
    @SerialName("device_id") val deviceId: String,
    val date: String,           // "YYYY-MM-DD"
    val hour: Int,              // 0-23
    @SerialName("avg_distance") val avgDistance: Double
)

@Serializable
data class DailyUsage(
    @SerialName("device_id") val deviceId: String,
    val date: String,           // "YYYY-MM-DD"
    @SerialName("consumption_liters") val consumptionLiters: Double
)
```

---

## 4. Auth Repository — `AuthRepository.kt`

Create file: `app/src/main/java/com/example/aqualevel/AuthRepository.kt`

```kotlin
package com.example.aqualevel

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class AuthRepository {
    private val auth = AquaSupabase.client.auth

    suspend fun register(email: String, password: String): Result<Unit> = runCatching {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun logout() = auth.signOut()

    fun currentUserId(): String? = auth.currentUserOrNull()?.id

    fun isLoggedIn(): Boolean = auth.currentUserOrNull() != null
}
```

---

## 5. Device Repository — `DeviceRepository.kt`

Create file: `app/src/main/java/com/example/aqualevel/DeviceRepository.kt`

```kotlin
package com.example.aqualevel

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

class DeviceRepository {
    private val db = AquaSupabase.client.postgrest

    // Called when user scans QR and pairs their device
    suspend fun pairDevice(deviceId: String, userId: String, name: String): Result<Device> =
        runCatching {
            // Check device exists and is unpaired
            val existing = db.from("devices")
                .select { filter { eq("id", deviceId) } }
                .decodeSingle<Device>()

            if (existing.userId != null) error("Device already paired to another account")

            // Link device to this user
            db.from("devices")
                .update({ set("user_id", userId); set("name", name) }) {
                    filter { eq("id", deviceId) }
                }
                .decodeSingle<Device>()
        }

    suspend fun getUserDevices(userId: String): List<Device> =
        db.from("devices")
            .select { filter { eq("user_id", userId) } }
            .decodeList<Device>()

    suspend fun updateCalibration(
        deviceId: String,
        dEmpty: Double,
        dFull: Double,
        capacityLiters: Double
    ) {
        db.from("devices").update({
            set("d_empty", dEmpty)
            set("d_full", dFull)
            set("capacity_liters", capacityLiters)
        }) { filter { eq("id", deviceId) } }
    }
}
```

---

## 6. Readings Repository — `ReadingsRepository.kt` (REPLACE existing)

This replaces the Firestore-based ReadingsRepository entirely.
Keep Room as local cache — only the remote source changes.

```kotlin
package com.example.aqualevel

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class ReadingsRepository(
    private val readingsDao: ReadingsDao  // existing Room DAO — keep as-is
) {
    private val db = AquaSupabase.client.postgrest
    private val realtime = AquaSupabase.client.realtime

    // Realtime stream for a specific device (replaces Firestore SnapshotListener)
    fun realtimeReadingsFlow(deviceId: String): Flow<Reading> =
        realtime.postgresChangeFlow<PostgresAction.Insert>("public") {
            table = "readings"
            filter = "device_id=eq.$deviceId"
        }.map { action ->
            action.decodeRecord<Reading>()
        }

    // Fetch last N readings for a device
    suspend fun getRecentReadings(deviceId: String, limit: Int = 100): List<Reading> =
        db.from("readings")
            .select {
                filter { eq("device_id", deviceId) }
                order("recorded_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<Reading>()

    // Fetch hourly readings for today
    suspend fun getTodayHourlyReadings(deviceId: String): List<HourlyReading> {
        val today = LocalDate.now().toString()
        return db.from("hourly_readings")
            .select {
                filter {
                    eq("device_id", deviceId)
                    eq("date", today)
                }
                order("hour", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }
            .decodeList<HourlyReading>()
    }

    // Fetch daily usage for last 7 days
    suspend fun getWeeklyUsage(deviceId: String): List<DailyUsage> {
        val sevenDaysAgo = LocalDate.now().minusDays(7).toString()
        return db.from("daily_usage")
            .select {
                filter {
                    eq("device_id", deviceId)
                    gte("date", sevenDaysAgo)
                }
                order("date", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }
            .decodeList<DailyUsage>()
    }
}
```

---

## 7. ViewModel Updates — `ReadingViewModel.kt`

Add a `selectedDeviceId: StateFlow<String?>` that drives all queries.
When user pairs or switches device, update this value and re-fetch.

```kotlin
// Add to existing ReadingViewModel

private val _selectedDeviceId = MutableStateFlow<String?>(null)
val selectedDeviceId: StateFlow<String?> = _selectedDeviceId

private val _devices = MutableStateFlow<List<Device>>(emptyList())
val devices: StateFlow<List<Device>> = _devices

fun loadUserDevices() {
    viewModelScope.launch {
        val uid = authRepository.currentUserId() ?: return@launch
        _devices.value = deviceRepository.getUserDevices(uid)
        // Auto-select first device
        _devices.value.firstOrNull()?.let { selectDevice(it.id) }
    }
}

fun selectDevice(deviceId: String) {
    _selectedDeviceId.value = deviceId
    loadReadings(deviceId)
    startRealtimeUpdates(deviceId)
}

private fun startRealtimeUpdates(deviceId: String) {
    viewModelScope.launch {
        readingsRepository.realtimeReadingsFlow(deviceId).collect { reading ->
            // Process new reading same as before (SpikeFilter, calibration, etc.)
            processNewReading(reading)
        }
    }
}
```

---

## 8. Auth UI — `LoginScreen.kt`

Create file: `app/src/main/java/com/example/aqualevel/LoginScreen.kt`

```kotlin
package com.example.aqualevel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AquaLevel", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.login(email, password, onLoginSuccess) },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is AuthUiState.Loading
        ) {
            if (uiState is AuthUiState.Loading) CircularProgressIndicator(Modifier.size(20.dp))
            else Text("Login")
        }

        TextButton(onClick = onNavigateToRegister) {
            Text("Don't have an account? Register")
        }

        if (uiState is AuthUiState.Error) {
            Text(
                (uiState as AuthUiState.Error).message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
```

Also create `RegisterScreen.kt` — identical structure, calls `viewModel.register(email, password, onSuccess)`.

---

## 9. Auth ViewModel — `AuthViewModel.kt`

Create file: `app/src/main/java/com/example/aqualevel/AuthViewModel.kt`

```kotlin
package com.example.aqualevel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aqualevel.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(private val authRepository: AuthRepository = AuthRepository()) : ViewModel() {
    val uiState: StateFlow<AuthUiState> get() = _uiState
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.login(email, password)
                .onSuccess { onSuccess() }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Login failed") }
        }
    }

    fun register(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.register(email, password)
                .onSuccess { onSuccess() }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Registration failed") }
        }
    }
}
```

---

## 10. Pairing Screen — `PairDeviceScreen.kt`

Create file: `app/src/main/java/com/example/aqualevel/PairDeviceScreen.kt`

This screen opens the camera, scans the QR on the ESP32 label (which encodes the device UUID),
then calls `deviceRepository.pairDevice(scannedId, userId, name)`.

```kotlin
package com.example.aqualevel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PairDeviceScreen(
    onPairSuccess: () -> Unit,
    viewModel: PairDeviceViewModel
) {
    var tankName by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Add Your Device", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // QR Scanner composable — uses CameraX + ML Kit
        // Calls viewModel.onQrScanned(deviceId) when a valid UUID is detected
        QrScannerView(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            onQrDetected = { viewModel.onQrScanned(it) }
        )

        Spacer(Modifier.height(16.dp))

        if (uiState is PairUiState.QrScanned) {
            Text("Device found: ${(uiState as PairUiState.QrScanned).deviceId}")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tankName,
                onValueChange = { tankName = it },
                label = { Text("Tank name (e.g. Rooftop Tank)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.pairDevice(tankName, onPairSuccess) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Pair Device") }
        }

        if (uiState is PairUiState.Error) {
            Text((uiState as PairUiState.Error).message, color = MaterialTheme.colorScheme.error)
        }
    }
}
```

---

## 11. Supabase Edge Function — `ingest/index.ts`

This is the only endpoint the ESP32 ever calls. Deploy via Supabase CLI.
File location in your Supabase project: `supabase/functions/ingest/index.ts`

```typescript
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const INGEST_SECRET = Deno.env.get("INGEST_SECRET")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

serve(async (req) => {
    // Validate shared secret from ESP32
    const secret = req.headers.get("x-ingest-secret");
    if (secret !== INGEST_SECRET) {
        return new Response("Unauthorized", { status: 401 });
    }

    const { device_id, distance_cm } = await req.json();

    if (!device_id || typeof distance_cm !== "number") {
        return new Response("Bad Request", { status: 400 });
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

    // Verify device exists in DB (was registered by developer before shipping)
    const { data: device, error: devErr } = await supabase
        .from("devices")
        .select("id")
        .eq("id", device_id)
        .single();

    if (devErr || !device) {
        return new Response("Device not registered", { status: 404 });
    }

    // Insert raw reading
    const { error } = await supabase
        .from("readings")
        .insert({ device_id, distance_cm });

    if (error) return new Response(error.message, { status: 500 });

    // Update hourly_readings (upsert current hour's average)
    const now = new Date();
    const date = now.toISOString().split("T")[0];
    const hour = now.getUTCHours();

    await supabase.from("hourly_readings").upsert(
        { device_id, date, hour, avg_distance: distance_cm },
        { onConflict: "device_id,date,hour", ignoreDuplicates: false }
    );

    return new Response(JSON.stringify({ ok: true }), {
        headers: { "Content-Type": "application/json" },
    });
});
```

---

## 12. ESP32 Firmware Change

Only one change needed in the existing firmware sketch.
Replace whatever Firestore POST logic exists with:

```cpp
// In your existing WiFi POST function:
const char* SUPABASE_FUNCTION_URL = "https://delpozytgraqfxvyitmu.supabase.co/functions/v1/ingest";
const char* DEVICE_ID = "esp32_01";  // unique per device (standard sample ID)
const char* INGEST_SECRET = "aqualevel_ingest_secret_2026";     // same across all devices

// POST payload:
// {"device_id": "...", "distance_cm": 42.5}
// Header: x-ingest-secret: aqualevel_ingest_secret_2026
```

Use `HTTPClient` (Arduino ESP32 library) — same as before, just different URL and headers.

---

## Navigation Graph Changes

Update `nav_graph.xml` or Compose NavHost to add these destinations:

```
LoginScreen  →  (if no account)  →  RegisterScreen
     ↓
HomeScreen (if logged in, device paired)
     ↓
     ↗  PairDeviceScreen  (if no device linked yet)
```

On app start: check `authRepository.isLoggedIn()` — if false, navigate to LoginScreen.
After login: check `deviceRepository.getUserDevices(uid)` — if empty, navigate to PairDeviceScreen.

---

## Files to CREATE
- `SupabaseClient.kt`
- `SupabaseModels.kt`
- `AuthRepository.kt`
- `DeviceRepository.kt`
- `AuthViewModel.kt`
- `PairDeviceViewModel.kt`
- `LoginScreen.kt`
- `RegisterScreen.kt`
- `PairDeviceScreen.kt`
- `QrScannerView.kt` (CameraX + ML Kit composable)
- `supabase/functions/ingest/index.ts`

## Files to MODIFY
- `ReadingsRepository.kt` — replace Firestore logic with Supabase SDK calls (Section 6)
- `ReadingViewModel.kt` — add device selection + auth state (Section 7)
- `NavGraph` — add auth and pairing destinations (Section above)
- `app/build.gradle.kts` — add dependencies (Section 1)
- `AndroidManifest.xml` — add CAMERA permission for QR scanner

## Files to DELETE / IGNORE
- Any `google-services.json` Firebase config (no longer needed)
- Any Firestore dependency in `build.gradle.kts`
