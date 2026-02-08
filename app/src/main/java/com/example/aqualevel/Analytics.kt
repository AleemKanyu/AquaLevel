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

class Analytics : AppCompatActivity() {

    private lateinit var homeButton: FrameLayout
    private lateinit var settingsButton: FrameLayout

    private lateinit var percentageText: TextView
    private lateinit var dailyUsageText: TextView
    private lateinit var daysLeftText: TextView
    private lateinit var hourlyAvgText: TextView
    private lateinit var userNameTextView: TextView
    private lateinit var hourlyUsageGraph: UsageGraphView

    // ---------- CALIBRATION CONSTANTS ----------
    private var emptyDistance = 130.0
    private var fullDistance = 20.0
    private var tankVolume = 2000.0 // Corrected to 2000L
    private val displayMultiplier = 1 // Multiplier set to 1 since tankVolume is now 2000L
    // ------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
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

        // Theme Toggle Setup
        themeToggle = findViewById(R.id.themeToggle)
        themeIcon = findViewById(R.id.themeIcon)
        updateThemeIcon()

        themeToggle.setOnClickListener {
            val isDark = sharedPref.getBoolean("is_dark_mode", false)
            sharedPref.edit().putBoolean("is_dark_mode", !isDark).apply()
            recreate()
        }

        val viewModel = ViewModelProvider(this)[ReadingViewModel::class.java]

        // -------- Top cards and Hourly Graph --------
        viewModel.allReadings.observe(this) { readings ->
            Log.d("Analytics", "All readings updated: ${readings.size} entries")
            if (readings.isEmpty()) return@observe

            // Current Level
            val latest = readings.first()
            val clampedLatest = latest.level.coerceIn(fullDistance, emptyDistance)
            val currentPercent = (((emptyDistance - clampedLatest) / (emptyDistance - fullDistance)) * 100).toInt()
            percentageText.text = currentPercent.toString()

            // Today's Readings
            val todayReadings = readings.filter {
                isSameDay(it.timestamp, System.currentTimeMillis())
            }

            // Daily Usage
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

            // Days Left and Hourly Average
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

            // Hourly Usage Graph
            updateHourlyGraph(todayReadings)
        }
    }

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

    private fun loadCalibrationSettings() {
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
        fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
        tankVolume = sharedPref.getInt("tank_volume", 2000).toDouble()
    }

    private lateinit var themeToggle: FrameLayout
    private lateinit var themeIcon: ImageView

    private fun updateThemeIcon() {
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        themeIcon.setImageResource(if (isDark) R.drawable.ic_sun else R.drawable.ic_moon)
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val d1 = Instant.ofEpochMilli(t1).atZone(ZoneId.systemDefault()).toLocalDate()
        val d2 = Instant.ofEpochMilli(t2).atZone(ZoneId.systemDefault()).toLocalDate()
        return d1 == d2
    }

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
}
