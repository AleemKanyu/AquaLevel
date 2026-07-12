# AquaLevel - Developer & Claude Context Guide

Welcome to the **AquaLevel** codebase context guide. This document serves as a complete architectural, logical, and database reference. You can feed this entire markdown file directly to Claude (or other LLMs) to immediately provide the full context of the project, including the hardware-software integration, database structures, algorithms, and design choices.

---

## 1. System Overview

**AquaLevel** is an IoT-based smart water level monitoring system. It measures the water level of a tank in real time using an ultrasonic sensor connected to an ESP32 microcontroller, syncs the readings to Google Firebase Firestore, and visualizes them inside a premium, native Kotlin Android application.

### High-Level Architecture
```mermaid
graph TD
    subgraph Hardware Layer (ESP32)
        A[HC-SR04 Ultrasonic Sensor] -->|Distance measurements| B[ESP32 Microcontroller]
        B -->|WiFi HTTPS Post| C[Firebase Firestore]
    end

    subgraph Firebase Cloud (Firestore)
        C -->|Collection: sensorData| D[Live & Hourly/Daily History]
        E[Refresh Command] -->|Collection: sensorCommands| B
    end

    subgraph Android Application (Kotlin)
        C -->|SnapshotListeners| F[ReadingsRepository]
        F -->|Persist| G[Room SQLite DB]
        G --> H[ReadingViewModel]
        H -->|LiveData / Flow| I[UI Fragments: Home, Analytics, Settings]
        I -->|Triggers refresh| E
        J[WorkManager Sync Workers] -->|Query| G
        J -->|Update| K[App Widgets & Alerts]
    end
```

---

## 2. Technology Stack & Directory Structure

### Stack Summary
- **Hardware Firmware**: C++ (Arduino IDE) running on ESP32, using the `Firebase_ESP_Client` and standard `WiFi` libraries.
- **Android App**: Kotlin, built on SDK 34 (Android 14) down to minSDK 26 (Android 8.0).
- **Core Libraries**:
  - **Firebase Firestore**: Used as the real-time cloud datastore.
  - **Jetpack Room**: Local offline single-source-of-truth database.
  - **Jetpack WorkManager**: Background sync workers for home screen widgets and critical low-water push alerts.
  - **Jetpack Navigation & ViewPager2**: Dynamic three-tab dashboard layout.

### Directory Structure
```
AquaLevel/
├── Esp32Code/
│   ├── Esp32Code.ino          # Core ESP32 Firmware
│   └── secrets.h              # WiFi & Firebase API credentials (Git-ignored)
└── app/src/main/java/com/example/aqualevel/
    ├── MainActivity.kt        # Tab Host, Theme Engine & App Update Handler
    ├── MainPagerAdapter.kt    # ViewPager2 adapter binding tabs
    ├── ReadingViewModel.kt    # Exposes Room data streams to fragments
    │
    ├── HomeFragment.kt        # Primary dashboard (tank WaveView, weekly bars, sync action)
    ├── AnalyticsFragment.kt   # Analytical reports (hourly line graphs, refill estimations)
    ├── SettingsFragment.kt    # Calibrations, toggles, CSV backup tools
    │
    ├── ReadingsRepository.kt  # Firestore-to-Room sync manager (Snapshot Listeners)
    ├── ReadingsDatabase.kt    # Room database setup (v3, destructive migration fallback)
    ├── ReadingsDao.kt         # Queries for live, hourly, and daily metrics
    │
    ├── Readings.kt            # Live readings entity (auto-increment id)
    ├── HourlyReadingEntity.kt # Hourly cached entity mapped by hour string ("00"-"23")
    ├── DailyUsageEntity.kt    # Historical usage mapped by date string ("YYYY-MM-DD")
    ├── DailyUsage.kt          # Legacy metadata data-class
    │
    ├── WaveView.kt            # Custom 2D animated fluid wave with gyro-tilt logic
    ├── UsageGraphView.kt      # Custom animated line chart for hourly analytics
    ├── CardBackgroundView.kt  # Duolingo-style 3D card layout backgrounds
    ├── WaterDropIconView.kt   # Custom vector drop illustrations & animations
    │
    ├── WaterLevelWorker.kt    # Background daemon processing threshold push notifications
    ├── WaterLevelUpdateWorker.kt# Widget background sync updating widget preferences
    ├── WaterLevelWidget.kt    # Appwidget provider rendering customizable home widgets
    │
    ├── SpikeFilter.kt         # Outlier filtration utility for noisy sensor data
    ├── NotificationHelper.kt  # OS channels & local notifications factory
    ├── UpdateManager.kt       # GitHub Release OTA installation helper
    └── AnimationExtensions.kt # Shared animation extensions (e.g. number count-up)
```

---

## 3. Database Schema

### Cloud Database (Google Firestore)
The ESP32 firmware and Android application communicate via three primary document structures:

#### A. Real-Time Main Sensor Document
- **Document Path**: `sensorData/esp32_01`
- **Fields**:
  - `distance` (Double): Distance measured by the ultrasonic sensor in centimeters.
  - `timestamp` (Integer/Long): Unix timestamp (seconds) of the reading.
  - `status` (String): Always set to `"online"` during active broadcasts.

#### B. Hourly Log Sub-Collection
- **Document Path**: `sensorData/esp32_01/hourly_current/{hour_id}`
  - *Where `{hour_id}` ranges from `"00"` to `"23"` representing the 24 hours in a day.*
- **Fields**:
  - `distance` (Double): Distances at the start/duration of that specific hour.
  - `timestamp` (Integer/Long): Unix timestamp.
- *Cleanup Node*: ESP32 detects day-change events (`tm_mday`) and automatically deletes subsequent hours (e.g. hourly documents for the rest of today) to clear stale cache.

#### C. Historical Daily Sub-Collection
- **Document Path**: `sensorData/esp32_01/daily/{date_str}`
  - *Where `{date_str}` is in the format `"YYYY-MM-DD"`.*
- **Fields**:
  - `date` (String): Date key.
  - `totalDistance` (Double): Total drop (cm) measured for the day (proxy for consumption).
  - `readingCount` (Integer): Total samples captured.
  - `lastUpdatedMs` (Integer/Long): Unix timestamp (milliseconds).

#### D. Sync Command Document
- **Document Path**: `sensorCommands/esp32_01`
- **Fields**:
  - `refresh` (Boolean): Set to `true` by the Android app when the user performs a pull-to-refresh / clicks the active sync button. Cleared back to `false` by the ESP32 once it completes a forced measurement.

---

## 4. Local Database Schema (Android Jetpack Room)
Room is the offline single-source-of-truth. It stores data across three entities:

#### A. Live Readings Entity (`readings` Table)
```kotlin
@Entity(tableName = "readings")
data class Readings(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val level: Double,
    val timestamp: Long
)
```
- Holds raw, un-filtered distance readings used to render the live tank percentage.

#### B. Hourly Cache Entity (`hourly_readings` Table)
```kotlin
@Entity(tableName = "hourly_readings")
data class HourlyReadingEntity(
    @PrimaryKey val hour: String, // IDs: "00" to "23"
    val distance: Double,
    val timestamp: Long
)
```
- Mirrors the active day's hourly readings from Firestore for graph plotting and today's usage calculations.

#### C. Daily Historical Entity (`daily_usage` Table)
```kotlin
@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    @PrimaryKey val date: String, // Format: "YYYY-MM-DD"
    val totalDistance: Double,
    val readingCount: Int
)
```
- Stores weekly aggregated stats used to render the 7-day usage bar chart and moving averages.

---

## 5. Key Algorithms & Logical Formulas

### A. Water Tank Volume and Percentage Calibration
Sensor distances are calibrated in the `SettingsFragment` via three SharedPreferences values:
1. `full_distance` ($D_{\text{full}}$): Sensor distance when tank is 100% full (e.g. 20 cm from sensor).
2. `empty_distance` ($D_{\text{empty}}$): Sensor distance when tank is 0% full (e.g. 130 cm from sensor).
3. `tank_volume` ($V_{\text{tank}}$): Full capacity volume of the tank in Liters or Gallons (e.g. 2000L).

Given a raw sensor distance reading $D_{\text{raw}}$:
$$\text{Clamped Distance } D_{\text{clamped}} = \max(D_{\text{full}}, \min(D_{\text{raw}}, D_{\text{empty}}))$$

$$\text{Water Percentage } P = \frac{D_{\text{empty}} - D_{\text{clamped}}}{D_{\text{empty}} - D_{\text{full}}} \times 100$$

$$\text{Water Volume } V_{\text{current}} = \frac{P}{100.0} \times V_{\text{tank}}$$

*Conversion*: If the unit is configured as Gallons in SharedPreferences (`volume_unit = "gal"`), volume is multiplied by `0.264172` for display:
$$V_{\text{display}} = V_{\text{current}} \times 0.264172$$

---

### B. Raw Sensor Noise Filtration (`SpikeFilter.kt`)
The ultrasonic sensor suffers from acoustic reflection noise. To prevent visual fluctuations and incorrect usage calculations, a 3-point median-like clamping is applied to hourly arrays:
1. If the middle reading spikes by more than **20 cm** relative to both its predecessor and successor, it is replaced by the average of the two:
   $$\text{if } |D_i - D_{i-1}| > 20 \text{ and } |D_i - D_{i+1}| > 20 \implies D_i = \frac{D_{i-1} + D_{i+1}}{2}$$
2. The last live reading is capped to the previous reading if it fluctuates by more than **20 cm** in one single step.

---

### C. Daily Water Consumption Calculation
Consumption is derived by analyzing the drop in the water level throughout the day.
Instead of summing simple differences (which are affected by refills), the app tracks positive level drops (where distance increases because water goes down):
$$\text{Hourly Change } \Delta D = D_i - D_{i-1}$$
$$\text{If } \Delta D > 1.0 \text{ cm} \text{ (filtering out micro-jitters) } \implies \text{Daily Consumption Drop } += \Delta D$$
$$\text{Daily Consumption Volume } V_{\text{consumed}} = \frac{\text{Daily Consumption Drop}}{D_{\text{empty}} - D_{\text{full}}} \times V_{\text{tank}}$$

---

### D. Refill Estimation & Time to Empty Algorithm
In `AnalyticsFragment`, the app uses a weighted historical average to determine how many days (or hours) of water remain:
1. A **3-day moving average** of historical daily consumption drops is calculated from Room's `daily_usage` table.
2. If today's consumption is significant (exceeding 50% of the historical average), the app calculates a weighted daily usage:
   $$\text{Weighted Usage } U_{\text{weighted}} = (U_{\text{historical}} \times 0.7) + (U_{\text{today}} \times 0.3)$$
   Otherwise, the historical average is used directly.
3. If no historical data exists, a default consumption rate of **5% of the tank volume** per day is assumed.
4. The remaining time is calculated:
   $$\text{Days Left } T = \frac{V_{\text{current}}}{U_{\text{weighted}}}$$
   - If $T \ge 1$, the UI displays the remaining time in **Days**.
   - If $T < 1$, the UI converts the value to **Hours** ($T \times 24$, clamped to a minimum of 1 hour).

---

### E. Realistic Fluid Animation View (`WaveView.kt`)
`WaveView` is a custom Android drawing view that uses dual sine waves to create a natural, sloshing water look:
- **Dual Sine Wave Formulation**: Two overlapping offset paths are calculated inside `onDraw` using:
  $$y_1(x) = A \sin(\omega x + \phi_1) + k$$
  $$y_2(x) = A \cos(\omega x + \phi_2) + k$$
  - *Where $A$ is the wave amplitude (height), $\omega$ is the wave frequency, and $k$ represents the current water level height.*
- **Tilt Simulation (Gyroscope)**: If enabled, Android's `Sensor.TYPE_ACCELEROMETER` modifies the wave draw transformation matrix. As the physical device tilts, the coordinate canvas rotates symmetrically, making the water surface remain relative to gravity.
- **Micro-Animations**: Features custom animated bubbles rising from the bottom and temporary splashes triggered when tapping the tank card.

---

## 6. Critical Code Execution Flow & Implementation Details

### Firestore-to-Room Lifecycle
1. The app starts, and `MainActivity` instantiates `ReadingViewModel`.
2. `ReadingViewModel` starts `ReadingsRepository.startSyncing()`.
3. The repository registers three continuous SnapshotListeners to Firestore:
   - Live stream updates `readings` (triggers a wave animation update).
   - Hourly logs updates `hourly_readings` (re-plots today's line chart).
   - Daily history updates `daily_usage` (re-evaluates the weekly bars).
4. Room tables trigger active flows, causing ViewModels to post new LiveData arrays.
5. Fragments observe these LiveData arrays and animate count-ups and wave views.

### Background Synchronization (WorkManager)
To keep widgets up-to-date and send emergency notifications without draining the battery:
1. `MainActivity` enqueues a periodic request `water_level_monitor` running `WaterLevelWorker` every **15 minutes**.
2. If the user installs a home widget, the system triggers `WaterLevelWidget.onEnabled()`, scheduling `WaterLevelUpdateWorker` to run every **30 minutes**.
3. **Data Freshness Guard**: Background workers only store historical data points and process alert metrics if the Firebase timestamp is less than **10 minutes old** (guarding against offline sensors or server outages).
4. **Haptic Customization**: `MainActivity` features a premium "water-drop" tactile feedback simulation using a timed sequence:
   - Heavy vibration tick -> delay of 80ms -> soft vibration tick -> delay of 120ms -> micro-vibration tick.

---

## 7. How to Leverage This Context with Claude
When asking Claude to write code, modify styling, or build new features for **AquaLevel**, you can reference this sheet directly. Some excellent prompts you can use:

- **Adding Multi-Tank Support**:
  > *"Based on the `CLAUDE_CONTEXT.md` file, I want to expand AquaLevel to support multiple tanks. How should I update the Firestore collections schema, the local Room Database entities, and the repository snapshot listener setup to differentiate between tank IDs (e.g. `esp32_01`, `esp32_02`)?"*

- **Improving Predictive Algorithms**:
  > *"Let's write a smarter refill prediction algorithm in `AnalyticsFragment.kt`. Use seasonal weights (weekend vs. weekday) and log linear regressions instead of the simple 3-day weighted moving average described in `CLAUDE_CONTEXT.md`."*

- **Polishing UI / Custom Views**:
  > *"I want to add a third sine wave and floating leaf particle effects to `WaveView.kt`. Look at the wave math inside `CLAUDE_CONTEXT.md` and provide the modifications to `onDraw` and the animator code."*

- **Debugging CSV Tools**:
  > *"Examine `SettingsFragment.kt`'s CSV export background thread logic from the context guide. I want to build a validation parser that ensures imported files strictly conform to the Room schema before inserting records."*
