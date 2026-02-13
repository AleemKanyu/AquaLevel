package com.example.aqualevel

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import java.time.Instant
import java.time.ZoneId
import java.util.*

class AnalyticsFragment : Fragment() {

    private lateinit var percentageText: TextView
    private lateinit var dailyUsageText: TextView
    private lateinit var daysLeftText: TextView
    private lateinit var hourlyAvgText: TextView
    private lateinit var hourlyUsageGraph: UsageGraphView
    
    private lateinit var cardLevel: FrameLayout
    private lateinit var cardUsage: FrameLayout
    private lateinit var cardAvg: FrameLayout
    private lateinit var cardTime: FrameLayout

    private var emptyDistance = 130.0
    private var fullDistance = 20.0
    private var tankVolume = 2000.0
    private var volumeUnit = "L"
    private val displayMultiplier = 1 

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String? ->
        if (key == "volume_unit" || key == "full_distance" || key == "empty_distance" || key == "tank_volume") {
            loadCalibrationSettings()
            // Force refresh UI values
            val viewModel = ViewModelProvider(requireActivity())[ReadingViewModel::class.java]
            viewModel.allReadings.value?.let { readings ->
                // This will trigger the observer logic again
                viewModel.refreshAllReadings() 
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sharedPref = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        loadCalibrationSettings()

        percentageText = view.findViewById(R.id.percentage)
        dailyUsageText = view.findViewById(R.id.dailyUsageValue)
        daysLeftText = view.findViewById(R.id.daysLeftValue)
        hourlyAvgText = view.findViewById(R.id.hourlyAvgValue)
        hourlyUsageGraph = view.findViewById(R.id.hourlyUsageGraph)

        cardLevel = view.findViewById(R.id.cardLevel)
        cardUsage = view.findViewById(R.id.cardUsage)
        cardAvg = view.findViewById(R.id.cardAvg)
        cardTime = view.findViewById(R.id.cardTime)

        val cards = listOf(cardLevel, cardUsage, cardAvg, cardTime)
        cards.forEach { card ->
            card.setOnClickListener {
                performHapticFeedbackCommon(it)
                applyClickAnimation(it) { }
            }
        }

        // Initialize Background Animations
        val bgLevel: CardBackgroundView = view.findViewById(R.id.bgLevel)
        val bgUsage: CardBackgroundView = view.findViewById(R.id.bgUsage)
        val bgAvg: CardBackgroundView = view.findViewById(R.id.bgAvg)
        val bgTime: CardBackgroundView = view.findViewById(R.id.bgTime)

        bgLevel.setRole(
            CardBackgroundView.Role.TANK_LEVEL,
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_purple),
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_purple_shadow)
        )
        bgUsage.setRole(
            CardBackgroundView.Role.DAILY_USAGE,
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_teal),
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_teal_shadow)
        )
        bgAvg.setRole(
            CardBackgroundView.Role.HOURLY_AVG,
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_lime),
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_lime_shadow)
        )
        bgTime.setRole(
            CardBackgroundView.Role.EST_TIME,
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_blue),
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_blue_shadow)
        )

        // Entry animation for cards
        val cardViews = listOf(
            view.findViewById<View>(R.id.hourlyUsageGraph).parent as View,
            view.findViewById<View>(R.id.cardLevel),
            view.findViewById<View>(R.id.cardUsage),
            view.findViewById<View>(R.id.cardAvg),
            view.findViewById<View>(R.id.cardTime)
        )
        
        cardViews.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 50f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay(index * 100L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        val viewModel = ViewModelProvider(requireActivity())[ReadingViewModel::class.java]

        val updateUI = { readings: List<Readings> ->
            if (readings.isNotEmpty()) {
                val latest = readings.first()
                val clampedLatest = latest.level.coerceIn(fullDistance, emptyDistance)
                val currentPercent = (((emptyDistance - clampedLatest) / (emptyDistance - fullDistance)) * 100).toInt()
                percentageText.text = currentPercent.toString()

                val todayReadings = readings.filter { isSameDay(it.timestamp, System.currentTimeMillis()) }

                if (todayReadings.size >= 2) {
                    val minDist = todayReadings.minOf { it.level }
                    val maxDist = todayReadings.maxOf { it.level }
                    val usedDist = (maxDist - minDist).coerceAtLeast(0.0)
                    val totalDistRange = emptyDistance - fullDistance
                    val usedVolume = (usedDist / totalDistRange) * tankVolume * displayMultiplier
                    if (volumeUnit == "gal") {
                        val galVal = usedVolume * 0.264172
                        dailyUsageText.text = galVal.toInt().toString()
                        view.findViewById<TextView>(R.id.dailyUsageUnit).text = "gal"
                    } else {
                        dailyUsageText.text = usedVolume.toInt().toString()
                        view.findViewById<TextView>(R.id.dailyUsageUnit).text = "L"
                    }
                } else {
                    dailyUsageText.text = "0"
                }

                val avgDailyUsageVolume = calculateAverageDailyUsage(readings)
                val hourlyAvg = if (avgDailyUsageVolume > 0) avgDailyUsageVolume / 24 else 0.0
                
                if (volumeUnit == "gal") {
                    val galVal = hourlyAvg * 0.264172
                    hourlyAvgText.text = galVal.toInt().toString()
                    view.findViewById<TextView>(R.id.hourlyAvgUnit).text = "gal/h"
                } else {
                    hourlyAvgText.text = hourlyAvg.toInt().toString()
                    view.findViewById<TextView>(R.id.hourlyAvgUnit).text = "L/h"
                }

                if (avgDailyUsageVolume > 0) {
                    val currentVolume = ((emptyDistance - clampedLatest) / (emptyDistance - fullDistance)) * tankVolume * displayMultiplier
                    val daysLeft = (currentVolume / avgDailyUsageVolume).toInt()
                    daysLeftText.text = daysLeft.toString()
                } else {
                    daysLeftText.text = "--"
                }

                updateHourlyGraph(todayReadings)
            }
        }

        viewModel.allReadings.observe(viewLifecycleOwner) { readings ->
            updateUI(readings)
        }

        viewModel.refreshTrigger.observe(viewLifecycleOwner) {
            viewModel.allReadings.value?.let { updateUI(it) }
        }
    }

    private fun updateHourlyGraph(todayReadings: List<Readings>) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hourlyData = FloatArray(24) { 0f }
        val totalDistRange = emptyDistance - fullDistance
        if (totalDistRange <= 0) return

        val groupedByHour = todayReadings.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).hour
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
        val sharedPref = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
        fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
        tankVolume = sharedPref.getInt("tank_volume", 2000).toDouble()
        volumeUnit = sharedPref.getString("volume_unit", "L") ?: "L"
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val d1 = Instant.ofEpochMilli(t1).atZone(ZoneId.systemDefault()).toLocalDate()
        val d2 = Instant.ofEpochMilli(t2).atZone(ZoneId.systemDefault()).toLocalDate()
        return d1 == d2
    }

    private fun calculateAverageDailyUsage(readings: List<Readings>): Double {
        val grouped = readings.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
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

    override fun onDestroyView() {
        super.onDestroyView()
        val sharedPref = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        sharedPref.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun performHapticFeedbackCommon(view: View) {
        val sharedPref = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        if (!sharedPref.getBoolean("vibration_enabled", true)) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (vibrator.hasVibrator()) vibrator.vibrate(50)
        }
    }

    private fun applyClickAnimation(view: View, onAnimationEnd: () -> Unit) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction { onAnimationEnd() }.start()
        }.start()
    }
}
