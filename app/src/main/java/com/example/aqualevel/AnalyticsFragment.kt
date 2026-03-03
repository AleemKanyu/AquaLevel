package com.example.aqualevel

import android.content.Context
import android.content.SharedPreferences
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import java.util.*
import java.text.SimpleDateFormat

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
    
    private lateinit var viewModel: ReadingViewModel
    private lateinit var sharedPref: SharedPreferences

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String? ->
        if (key == "volume_unit" || key == "full_distance" || key == "empty_distance" || key == "tank_volume") {
            loadCalibrationSettings()
            refreshUI()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPref = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
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

        setupBackgrounds(view)
        animateEntry(view)

        viewModel = ViewModelProvider(requireActivity())[ReadingViewModel::class.java]
        observeData()
    }

    private fun setupBackgrounds(view: View) {
        val bgLevel: CardBackgroundView = view.findViewById(R.id.bgLevel)
        val bgUsage: CardBackgroundView = view.findViewById(R.id.bgUsage)
        val bgAvg: CardBackgroundView = view.findViewById(R.id.bgAvg)
        val bgTime: CardBackgroundView = view.findViewById(R.id.bgTime)

        bgLevel.setRole(CardBackgroundView.Role.TANK_LEVEL, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_purple), androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_purple_shadow))
        bgUsage.setRole(CardBackgroundView.Role.DAILY_USAGE, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_teal), androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_teal_shadow))
        bgAvg.setRole(CardBackgroundView.Role.HOURLY_AVG, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_lime), androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_lime_shadow))
        bgTime.setRole(CardBackgroundView.Role.EST_TIME, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_blue), androidx.core.content.ContextCompat.getColor(requireContext(), R.color.duo_blue_shadow))
    }

    private fun animateEntry(view: View) {
        val cardViews = listOf(
            view.findViewById<View>(R.id.hourlyUsageGraph).parent as View,
            cardLevel, cardUsage, cardAvg, cardTime
        )
        cardViews.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 50f
            card.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(index * 100L).start()
        }
    }

    private fun observeData() {
        viewModel.hourlyReadings.observe(viewLifecycleOwner) { readings ->
            updateUI(readings)
        }
    }

    private fun updateUI(readings: List<HourlyReadingEntity>) {
        if (readings.isEmpty()) return
        
        // 1. Current Percentage
        val latest = readings.maxByOrNull { it.timestamp } ?: return
        val clampedLatest = latest.distance.coerceIn(fullDistance, emptyDistance)
        val currentPercent = (((emptyDistance - clampedLatest) / (emptyDistance - fullDistance)) * 100).toInt()
        percentageText.animateCountUp(currentPercent, 0, 1000)

        val totalDistRange = emptyDistance - fullDistance
        if (totalDistRange <= 0) return

        // 2. Calculate Hourly Usage for Today (for the graph)
        val hourlyUsage = FloatArray(24) { 0f }
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(cal.time)
        
        // We use the hourly current cache
        val sortedReadings = readings.sortedBy { it.hour.toIntOrNull() ?: 0 }
        var todayTotalUsageDist = 0.0
        
        for (i in 1 until sortedReadings.size) {
            val prev = sortedReadings[i - 1]
            val curr = sortedReadings[i]
            
            val currHour = curr.hour.toIntOrNull() ?: continue
            
            val diff = curr.distance - prev.distance
            if (diff > 0) {
                val usedVol = (diff / totalDistRange) * tankVolume
                hourlyUsage[currHour] = usedVol.toFloat()
                todayTotalUsageDist += diff
            }
        }

        // Trim to current hour + 1 so future hours are never shown
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val trimmedUsage = hourlyUsage.take(currentHour + 1)   // indices 0..currentHour

        hourlyUsageGraph.setData(trimmedUsage.toList(), currentHour)
        
        val todayUsageVol = (todayTotalUsageDist / totalDistRange) * tankVolume
        
        // Show TODAY'S usage in the "Daily Usage" card
        if (volumeUnit == "gal") {
            dailyUsageText.animateCountUp((todayUsageVol * 0.264172).toInt(), 0, 1000)
        } else {
            dailyUsageText.animateCountUp(todayUsageVol.toInt(), 0, 1000)
        }

        // 3. Logic for Average and Estimated Time Left
        viewModel.weeklyUsage.observe(viewLifecycleOwner) { dailyList ->
            if (dailyList.isNotEmpty()) {
                // Use a moving average of the last few days for better accuracy
                val movingAvgDays = 3
                val relevantList = if (dailyList.size >= movingAvgDays) dailyList.take(movingAvgDays) else dailyList
                
                val historicalDistSum = relevantList.sumOf { it.totalDistance }
                val avgDailyDist = historicalDistSum / relevantList.size
                
                // Weighting today's usage if it's significant
                val weightedAvgDailyDist = if (todayTotalUsageDist > avgDailyDist * 0.5) {
                    (avgDailyDist * 0.7) + (todayTotalUsageDist * 0.3)
                } else {
                    avgDailyDist
                }
                
                val avgDailyVol = (weightedAvgDailyDist / totalDistRange) * tankVolume

                // Hourly Average stat (Today's performance)
                val currentHour2 = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1
                val hourlyAvgVol = todayUsageVol / currentHour2
                
                if (volumeUnit == "gal") {
                    hourlyAvgText.animateCountUp((hourlyAvgVol * 0.264172).toInt(), 0, 1000)
                } else {
                    hourlyAvgText.animateCountUp(hourlyAvgVol.toInt(), 0, 1000)
                }

                // --- Improved Estimated Time to Refill Algorithm ---
                val currentVolume = ((emptyDistance - clampedLatest) / totalDistRange) * tankVolume
                
                // If usage is zero, we assume a very small default usage to avoid infinity
                val effectiveDailyUsage = if (avgDailyVol > 0) avgDailyVol else (tankVolume * 0.05) // Assume 5% daily usage if no data
                
                val totalDaysLeft = currentVolume / effectiveDailyUsage
                
                // Change label to "Time to Refill"
                (daysLeftText.parent as? ViewGroup)?.let { parent ->
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChildAt(i) as? TextView
                        if (child?.text == "Est. \nTime") child.text = "Time to \nRefill"
                    }
                }

                if (totalDaysLeft < 1) {
                    val hoursLeft = (totalDaysLeft * 24).toInt().coerceAtLeast(1)
                    daysLeftText.animateCountUp(hoursLeft, 0, 1000)
                    updateUnitLabel("Hours")
                } else {
                    val days = totalDaysLeft.toInt()
                    daysLeftText.animateCountUp(days, 0, 1000)
                    updateUnitLabel("Days")
                }
            }
        }
    }

    private fun updateUnitLabel(newUnit: String) {
        (daysLeftText.parent as? ViewGroup)?.let { parent ->
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i) as? TextView
                if (child?.text == "Days" || child?.text == "Hours") {
                    child.text = newUnit
                }
            }
        }
    }

    private fun refreshUI() {
        viewModel.hourlyReadings.value?.let { updateUI(it) }
    }

    private fun loadCalibrationSettings() {
        emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
        fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
        tankVolume = sharedPref.getInt("tank_volume", 2000).toDouble()
        volumeUnit = sharedPref.getString("volume_unit", "L") ?: "L"
    }

    private fun performHapticFeedbackCommon(view: View) {
        if (!sharedPref.getBoolean("vibration_enabled", true)) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun applyClickAnimation(view: View, onAnimationEnd: () -> Unit) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction { onAnimationEnd() }.start()
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sharedPref.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}
