package com.example.aqualevel

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import java.time.Instant
import java.time.ZoneId

class Analytics : AppCompatActivity() {

    private lateinit var homeButton: FrameLayout

    private lateinit var mondayBar: View
    private lateinit var tuesdayBar: View
    private lateinit var wedBar: View
    private lateinit var thursdayBar: View
    private lateinit var fridayBar: View
    private lateinit var saturdayBar: View
    private lateinit var sundayBar: View

    private lateinit var percentageText: TextView
    private lateinit var dailyUsageText: TextView
    private lateinit var daysLeftText: TextView

    // ---------- CALIBRATION CONSTANTS (Matching MainActivity) ----------
    private val emptyDistance = 130.0
    private val fullDistance = 20.0
    private val tankVolume = 1000.0
    private val displayMultiplier = 2
    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analytics)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        homeButton = findViewById(R.id.homeButton)
        homeButton.setOnClickListener {
            finish() // Use finish() instead of starting a new MainActivity to avoid stacking activities
        }

        mondayBar = findViewById(R.id.mondayBar)
        tuesdayBar = findViewById(R.id.tuesdayBar)
        wedBar = findViewById(R.id.wedBar)
        thursdayBar = findViewById(R.id.thursdayBar)
        fridayBar = findViewById(R.id.fridayBar)
        saturdayBar = findViewById(R.id.saturdayBar)
        sundayBar = findViewById(R.id.sundayBar)

        percentageText = findViewById(R.id.percentage)
        dailyUsageText = findViewById(R.id.dailyUsageValue)
        daysLeftText = findViewById(R.id.daysLeftValue)

        val viewModel = ViewModelProvider(this)[ReadingViewModel::class.java]

        // -------- Weekly bars --------
        viewModel.weeklyUsage.observe(this) { days ->
            Log.d("Analytics", "Weekly usage updated: ${days.size} days")
            val bars = listOf(
                mondayBar, tuesdayBar, wedBar,
                thursdayBar, fridayBar, saturdayBar, sundayBar
            )

            val maxBarHeight = dpToPx(160)

            // Reset bars
            bars.forEach { bar ->
                val params = bar.layoutParams
                params.height = dpToPx(8)
                bar.layoutParams = params
            }

            days.take(7).forEachIndexed { index, usage ->
                // usage.minLevel and maxLevel are distances.
                // used distance = maxDist - minDist (e.g. 130 - 20 = 110 used)
                val usedDist = (usage.maxLevel - usage.minLevel).coerceAtLeast(0.0)
                val totalDistRange = emptyDistance - fullDistance
                val normalized = (usedDist / totalDistRange).coerceIn(0.0, 1.0)
                
                val barHeight = (maxBarHeight * normalized).toInt().coerceAtLeast(dpToPx(8))

                // The logic in MainActivity uses 6 - index, assuming days are DESC
                val barIndex = 6 - index
                if (barIndex in bars.indices) {
                    val params = bars[barIndex].layoutParams
                    params.height = barHeight
                    bars[barIndex].layoutParams = params
                }
            }
        }

        // -------- Top cards --------
        viewModel.allReadings.observe(this) { readings ->
            Log.d("Analytics", "All readings updated: ${readings.size} entries")
            if (readings.isEmpty()) return@observe

            val latest = readings.first()
            val clampedLatest = latest.level.coerceIn(fullDistance, emptyDistance)
            val currentPercent = (((emptyDistance - clampedLatest) / (emptyDistance - fullDistance)) * 100).toInt()
            percentageText.text = currentPercent.toString()

            val today = readings.filter {
                isSameDay(it.timestamp, System.currentTimeMillis())
            }

            if (today.size >= 2) {
                val minDist = today.minOf { it.level }
                val maxDist = today.maxOf { it.level }
                
                val usedDist = (maxDist - minDist).coerceAtLeast(0.0)
                val totalDistRange = emptyDistance - fullDistance
                val usedVolume = (usedDist / totalDistRange) * tankVolume * displayMultiplier
                
                dailyUsageText.text = usedVolume.toInt().toString()
            } else {
                dailyUsageText.text = "0"
            }

            val avgDailyUsageVolume = calculateAverageDailyUsage(readings)
            if (avgDailyUsageVolume > 0) {
                val currentVolume = ((emptyDistance - clampedLatest) / (emptyDistance - fullDistance)) * tankVolume * displayMultiplier
                val daysLeft = (currentVolume / avgDailyUsageVolume).toInt()
                daysLeftText.text = daysLeft.toString()
            } else {
                daysLeftText.text = "--"
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
