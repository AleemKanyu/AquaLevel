package com.example.aqualevel

import android.content.Context
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
    private val displayMultiplier = 1 

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

        val viewModel = ViewModelProvider(requireActivity())[ReadingViewModel::class.java]

        viewModel.allReadings.observe(viewLifecycleOwner) { readings ->
            if (readings.isEmpty()) return@observe

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
                dailyUsageText.text = usedVolume.toInt().toString()
            } else {
                dailyUsageText.text = "0"
            }

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

            updateHourlyGraph(todayReadings)
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

    private fun performHapticFeedbackCommon(view: View) {
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
