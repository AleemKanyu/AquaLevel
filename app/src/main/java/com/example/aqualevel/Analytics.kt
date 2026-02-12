package com.example.aqualevel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import java.time.Instant
import java.time.ZoneId
import java.util.*

/**
 * Activity responsible for displaying water level analytics and usage statistics.
 * This includes current tank level, daily usage, hourly average, and estimated time remaining.
 * It also features an hourly usage graph and theme toggling functionality.
 */
class Analytics : AppCompatActivity() {

    private lateinit var homeButton: FrameLayout
    private lateinit var settingsButton: FrameLayout

    private lateinit var percentageText: TextView
    private lateinit var dailyUsageText: TextView
    private lateinit var daysLeftText: TextView
    private lateinit var hourlyAvgText: TextView
    private lateinit var userNameTextView: TextView
    private lateinit var hourlyUsageGraph: UsageGraphView
    
    private lateinit var cardLevel: FrameLayout
    private lateinit var cardUsage: FrameLayout
    private lateinit var cardAvg: FrameLayout
    private lateinit var cardTime: FrameLayout

    // ---------- CALIBRATION CONSTANTS ----------
    /** Distance in cm from the sensor to the bottom of the tank when empty. */
    private var emptyDistance = 130.0
    /** Distance in cm from the sensor to the water surface when the tank is full. */
    private var fullDistance = 20.0
    /** Total capacity of the water tank in liters. */
    private var tankVolume = 2000.0 // Corrected to 2000L
    /** Multiplier applied to volume calculations for display purposes. */
    private val displayMultiplier = 1 
    // ------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        // Load theme preference before super.onCreate
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("is_dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analytics)

        loadCalibrationSettings()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userNameTextView = findViewById(R.id.userName)
        val savedName = sharedPref.getString("user_name", "User")
        userNameTextView.text = savedName

        homeButton = findViewById(R.id.homeButton)
        homeButton.setOnClickListener {
            finish()
        }

        settingsButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        percentageText = findViewById(R.id.percentage)
        dailyUsageText = findViewById(R.id.dailyUsageValue)
        daysLeftText = findViewById(R.id.daysLeftValue)
        hourlyAvgText = findViewById(R.id.hourlyAvgValue)
        hourlyUsageGraph = findViewById(R.id.hourlyUsageGraph)

        cardLevel = findViewById(R.id.cardLevel)
        cardUsage = findViewById(R.id.cardUsage)
        cardAvg = findViewById(R.id.cardAvg)
        cardTime = findViewById(R.id.cardTime)

        val cards = listOf(cardLevel, cardUsage, cardAvg, cardTime)
        cards.forEach { card ->
            card.setOnClickListener {
                performHapticFeedbackCommon(it)
                applyClickAnimation(it) {
                    // Just animation for now as requested
                }
            }
        }

        // Theme Toggle Setup
        themeToggle = findViewById(R.id.themeToggle)
        themeIcon = findViewById(R.id.themeIcon)
        updateThemeIcon()

        themeToggle.setOnClickListener {
            val isDark = sharedPref.getBoolean("is_dark_mode", false)
            val newMode = !isDark
            sharedPref.edit().putBoolean("is_dark_mode", newMode).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (newMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        val viewModel = ViewModelProvider(this)[ReadingViewModel::class.java]

        // -------- Top cards and Hourly Graph --------
        viewModel.allReadings.observe(this) { readings ->
            Log.d("Analytics", "All readings updated: ${readings.size} entries")
            if (readings.isEmpty()) return@observe

            // Current Level Calculation
            val latest = readings.first()
            val clampedLatest = latest.level.coerceIn(fullDistance, emptyDistance)
            val currentPercent = (((emptyDistance - clampedLatest) / (emptyDistance - fullDistance)) * 100).toInt()
            percentageText.text = currentPercent.toString()

            // Today's Readings Filtering
            val todayReadings = readings.filter {
                isSameDay(it.timestamp, System.currentTimeMillis())
            }

            // Daily Usage Calculation
            if (todayReadings.size >= 2) {
                val minDist = todayReadings.minOf { it.level }
                val maxDist = todayReadings.maxOf { it.level }
                
                val usedDist = (maxDist - minDist).coerceAtLeast(0.0)
                val totalDistRange = emptyDistance - fullDistance
                val usedVolume = (usedDist / totalDistRange) * tankVolume * displayMultiplier
                
                dailyUsageText.text = usedVolume.toInt().toString()
            } else {
                dailyUsageText.text = "0"
            }

            // Days Left and Hourly Average Calculation
            val avgDailyUsageVolume = calculateAverageDailyUsage(readings)
            val hourlyAvg = if (avgDailyUsageVolume > 0) avgDailyUsageVolume / 24 else 0.0
            hourlyAvgText.text = hourlyAvg.toInt().toString()

            if (avgDailyUsageVolume > 0) {
                val currentVolume = ((emptyDistance - clampedLatest) / (emptyDistance - fullDistance)) * tankVolume * displayMultiplier
                val daysLeft = (currentVolume / avgDailyUsageVolume).toInt()
                daysLeftText.text = daysLeft.toString()
            } else {
                daysLeftText.text = "--"
            }

            // Hourly Usage Graph Update
            updateHourlyGraph(todayReadings)
        }
    }

    /**
     * Updates the [UsageGraphView] with hourly consumption data for the current day.
     * 
     * @param todayReadings List of readings recorded on the current day.
     */
    private fun updateHourlyGraph(todayReadings: List<Readings>) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hourlyData = FloatArray(24) { 0f }
        
        val totalDistRange = emptyDistance - fullDistance
        if (totalDistRange <= 0) return

        val groupedByHour = todayReadings.groupBy {
            Instant.ofEpochMilli(it.timestamp)
                .atZone(ZoneId.systemDefault())
                .hour
        }

        for (hour in 0..currentHour) {
            val readingsInHour = groupedByHour[hour]
            if (readingsInHour != null && readingsInHour.size >= 2) {
                val minDist = readingsInHour.minOf { it.level }
                val maxDist = readingsInHour.maxOf { it.level }
                val usedDist = (maxDist - minDist).coerceAtLeast(0.0)
                val usedVolume = (usedDist / totalDistRange) * tankVolume * displayMultiplier
                hourlyData[hour] = usedVolume.toFloat()
            }
        }
        
        hourlyUsageGraph.setData(hourlyData.toList())
    }

    /**
     * Loads calibration values (empty distance, full distance, and tank volume) from SharedPreferences.
     */
    private fun loadCalibrationSettings() {
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
        fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
        tankVolume = sharedPref.getInt("tank_volume", 2000).toDouble()
    }

    private lateinit var themeToggle: FrameLayout
    private lateinit var themeIcon: ImageView

    /**
     * Updates the theme icon based on the current dark mode preference.
     */
    private fun updateThemeIcon() {
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        themeIcon.setImageResource(if (isDark) R.drawable.ic_sun else R.drawable.ic_moon)
    }

    /**
     * Checks if two timestamps represent the same calendar day.
     * 
     * @param t1 First timestamp in milliseconds.
     * @param t2 Second timestamp in milliseconds.
     * @return True if both timestamps are on the same day, false otherwise.
     */
    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val d1 = Instant.ofEpochMilli(t1).atZone(ZoneId.systemDefault()).toLocalDate()
        val d2 = Instant.ofEpochMilli(t2).atZone(ZoneId.systemDefault()).toLocalDate()
        return d1 == d2
    }

    /**
     * Calculates the average volume of water used per day based on historical readings.
     * 
     * @param readings The list of all historical [Readings].
     * @return The average daily usage in liters.
     */
    private fun calculateAverageDailyUsage(readings: List<Readings>): Double {
        val grouped = readings.groupBy {
            Instant.ofEpochMilli(it.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }

        val totalDistRange = emptyDistance - fullDistance
        val usages = grouped.values.mapNotNull { dayList ->
            if (dayList.size < 2) return@mapNotNull null
            val minDist = dayList.minOf { it.level }
            val maxDist = dayList.maxOf { it.level }
            val usedDist = (maxDist - minDist).coerceAtLeast(0.0)
            (usedDist / totalDistRange) * tankVolume * displayMultiplier
        }

        return if (usages.isNotEmpty()) usages.average() else 0.0
    }

    /**
     * Performs a standard haptic feedback effect.
     */
    private fun performHapticFeedbackCommon(view: View) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(50)
            }
        }
    }

    /**
     * Applies a scale animation to a view when clicked.
     */
    private fun applyClickAnimation(view: View, onAnimationEnd: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction { onAnimationEnd() }
                    .start()
            }
            .start()
    }
}
