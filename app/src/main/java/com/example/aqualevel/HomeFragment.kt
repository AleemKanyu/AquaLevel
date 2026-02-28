package com.example.aqualevel

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import java.util.*

class HomeFragment : Fragment() {
    private lateinit var mondayBar: View
    private lateinit var tuesdayBar: View
    private lateinit var wedBar: View
    private lateinit var thursdayBar: View
    private lateinit var fridayBar: View
    private lateinit var saturdayBar: View
    private lateinit var sundayBar: View

    private lateinit var capacityValue: TextView
    private lateinit var dailyAvgValue: TextView
    private lateinit var percentage: TextView
    private lateinit var waterLevel: WaveView
    private lateinit var tankContainer: FrameLayout
    private lateinit var trendIndicator: ImageView
    private lateinit var confettiContainer: FrameLayout
    
    private var lastDisplayedValue: Double = 0.0
    private var lastDisplayedPercent: Int = 0

    private lateinit var notificationPermissionCard: View
    private lateinit var btnEnableNotifications: Button

    private lateinit var viewModel: ReadingViewModel
    private lateinit var sharedPref: SharedPreferences
    private lateinit var notificationHelper: NotificationHelper

    private var emptyDistance = 130.0
    private var fullDistance = 20.0
    private var tankVolume = 2000.0
    private var volumeUnit = "L"

    private var lastKnownPercentage = -1
    private var isFirstLoad = true

    private fun animateWaterLevel(from: Int, to: Int, duration: Long) {
        val animator = ValueAnimator.ofInt(from, to)
        animator.duration = duration
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            waterLevel.setWaterLevel(animatedValue)
        }
        animator.start()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(requireContext(), "Notifications enabled.", Toast.LENGTH_SHORT).show()
            notificationHelper.sendNotification("Notifications Enabled", "You will now receive water level alerts.", 999)
        }
        updateNotificationCardVisibility()
    }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "volume_unit" || key == "full_distance" || key == "empty_distance" || key == "tank_volume") {
            loadCalibrationSettings()
            refreshUI()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPref = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        
        notificationHelper = NotificationHelper(requireContext())
        viewModel = ViewModelProvider(requireActivity())[ReadingViewModel::class.java]

        capacityValue = view.findViewById(R.id.capacityValue)
        dailyAvgValue = view.findViewById(R.id.dailyAvgValue)
        percentage = view.findViewById(R.id.percentage)
        waterLevel = view.findViewById(R.id.waterLevel)
        
        val gyroEnabled = sharedPref.getBoolean("gyro_water_enabled", false)
        val disableAnimation = sharedPref.getBoolean("disable_animation", false)
        val performanceMode = sharedPref.getBoolean("performance_mode", false)
        
        waterLevel.setPerformanceConfiguration(disableAnimation, performanceMode)
        waterLevel.setGyroEnabled(gyroEnabled)

        tankContainer = view.findViewById(R.id.tankContainer)
        trendIndicator = view.findViewById(R.id.trendIndicator)
        confettiContainer = view.findViewById(R.id.confettiContainer)
        
        notificationPermissionCard = view.findViewById(R.id.notificationPermissionCard)
        btnEnableNotifications = view.findViewById(R.id.btnEnableNotifications)

        mondayBar = view.findViewById(R.id.mondayBar)
        tuesdayBar = view.findViewById(R.id.tuesdayBar)
        wedBar = view.findViewById(R.id.wedBar)
        thursdayBar = view.findViewById(R.id.thursdayBar)
        fridayBar = view.findViewById(R.id.fridayBar)
        saturdayBar = view.findViewById(R.id.saturdayBar)
        sundayBar = view.findViewById(R.id.sundayBar)

        btnEnableNotifications.setOnClickListener { handleNotificationButtonClick() }

        tankContainer.setOnClickListener {
            waterLevel.splash()
            performHapticFeedbackCommon(view)
        }

        loadCalibrationSettings()
        observeData()
        startBubbleAnimation()
    }

    private fun observeData() {
        viewModel.hourlyReadings.observe(viewLifecycleOwner) { readings ->
            updateCurrentLevel(readings)
        }

        viewModel.weeklyUsage.observe(viewLifecycleOwner) { usage ->
            updateWeeklyBars(usage)
        }
    }

    private fun updateCurrentLevel(readings: List<HourlyReadingEntity>) {
        if (readings.isEmpty()) return
        
        // Get the latest reading based on hour/timestamp
        val latest = readings.maxByOrNull { it.timestamp } ?: return
        val distance = latest.distance
        
        val clampedDistance = distance.coerceIn(fullDistance, emptyDistance)
        val percent = ((emptyDistance - clampedDistance) / (emptyDistance - fullDistance)) * 100.0
        val safePercent = percent.coerceIn(0.0, 100.0)
        val currentVolume = (safePercent / 100.0) * tankVolume
        
        if (volumeUnit == "gal") {
            val galVal = currentVolume * 0.264172
            val oldGalVal = lastDisplayedValue * 0.264172
            capacityValue.animateCountUp(galVal.toInt(), oldGalVal.toInt(), 1000, " gallons")
        } else {
            capacityValue.animateCountUp(currentVolume.toInt(), lastDisplayedValue.toInt(), 1000, " litres")
        }
        
        percentage.animateCountUp(safePercent.toInt(), lastDisplayedPercent, 1000, "%")
        
        lastDisplayedValue = currentVolume
        
        val disableAnimation = sharedPref.getBoolean("disable_animation", false)
        val performanceMode = sharedPref.getBoolean("performance_mode", false)

        if (disableAnimation || performanceMode) {
            waterLevel.setWaterLevel(safePercent.toInt())
        } else {
            if (isFirstLoad) {
                animateWaterLevel(0, safePercent.toInt(), 2000)
                isFirstLoad = false
            } else {
                animateWaterLevel(lastDisplayedPercent, safePercent.toInt(), 1000)
            }
        }
        
        lastDisplayedPercent = safePercent.toInt()

        val waveColor = if (safePercent <= 25) {
            ContextCompat.getColor(requireContext(), R.color.brand_warning)
        } else {
            ContextCompat.getColor(requireContext(), R.color.duo_blue)
        }
        waterLevel.setWaveColor(waveColor)

        if (lastKnownPercentage != -1 && safePercent > lastKnownPercentage + 10) {
            onRefillDetected()
        }
        if (lastKnownPercentage != -1) {
            updateTrendIndicator(safePercent.toInt(), lastKnownPercentage)
        }
        lastKnownPercentage = safePercent.toInt()

        checkAndSendNotifications(safePercent.toInt())
        updateWidget()
    }

    private fun updateWeeklyBars(usage: List<DailyUsageEntity>) {
        val bars = listOf(mondayBar, tuesdayBar, wedBar, thursdayBar, fridayBar, saturdayBar, sundayBar)
        val maxBarHeight = dpToPx(requireContext(), 160)

        // Reset bars
        bars.forEach { bar ->
            val params = bar.layoutParams
            params.height = dpToPx(requireContext(), 8)
            bar.layoutParams = params
        }

        if (usage.isEmpty()) {
            dailyAvgValue.text = if (volumeUnit == "gal") "0 gal" else "0 L"
            return
        }

        // Sort by date ascending to map to bars correctly if needed, 
        // but Requirement says retrieve last 7 and sort ascending.
        val sortedUsage = usage.sortedBy { it.date }
        
        // Simple mapping: the last 7 days from Firestore. 
        // We'll map them to the bars from right to left (today is the last bar).
        val barCount = bars.size
        val dataCount = sortedUsage.size
        
        var totalUsage = 0.0
        
        for (i in 0 until dataCount) {
            if (i >= barCount) break
            val daily = sortedUsage[dataCount - 1 - i] // Get from latest
            val barIndex = barCount - 1 - i
            
            // Usage calculated by ESP32: totalDistance
            // We need to convert distance change to volume
            val totalDistRange = emptyDistance - fullDistance
            val usedVolume = (daily.totalDistance / totalDistRange) * tankVolume
            totalUsage += usedVolume

            val normalized = (usedVolume / tankVolume).coerceIn(0.0, 1.0)
            val barHeight = (maxBarHeight * normalized).toInt().coerceAtLeast(dpToPx(requireContext(), 8))
            
            val params = bars[barIndex].layoutParams
            params.height = barHeight
            bars[barIndex].layoutParams = params
        }

        val avgVolume = totalUsage / dataCount
        if (volumeUnit == "gal") {
            val calVal = avgVolume * 0.264172
            dailyAvgValue.text = "${calVal.toInt()} gal"
        } else {
            dailyAvgValue.text = "${avgVolume.toInt()} L"
        }
    }

    private fun refreshUI() {
        viewModel.hourlyReadings.value?.let { updateCurrentLevel(it) }
        viewModel.weeklyUsage.value?.let { updateWeeklyBars(it) }
    }

    private fun updateWidget() {
        val widgetIntent = Intent(requireContext(), WaterLevelWidget::class.java)
        widgetIntent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(requireContext())
        val componentName = android.content.ComponentName(requireContext(), WaterLevelWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        widgetIntent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        requireContext().sendBroadcast(widgetIntent)
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

    private fun checkAndSendNotifications(percent: Int) {
        val lastNotified = sharedPref.getInt("last_notified_level", -1)
        if (percent >= 100 && lastNotified != 100) {
            notificationHelper.sendNotification("Tank Full!", "Your water tank is now 100% full.", 1001)
            sharedPref.edit().putInt("last_notified_level", 100).apply()
        } else if (percent <= 30 && lastNotified != 30) {
            notificationHelper.sendNotification("Low Water Level", "Warning: Tank level is at ${percent}%.", 1002)
            sharedPref.edit().putInt("last_notified_level", 30).apply()
        } else if (percent in 31..99) {
            sharedPref.edit().putInt("last_notified_level", -1).apply()
        }
    }

    private fun onRefillDetected() {
        view?.let { performHapticFeedbackCommon(it) }
        percentage.animate().scaleX(1.4f).scaleY(1.4f).setDuration(300).withEndAction {
            percentage.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
        }.start()
        Toast.makeText(requireContext(), "Refill Detected! \uD83D\uDCA7", Toast.LENGTH_SHORT).show()
        startConfetti()
    }

    private fun updateTrendIndicator(current: Int, previous: Int) {
        if (current == previous) {
            trendIndicator.visibility = View.INVISIBLE
            return
        }
        trendIndicator.visibility = View.VISIBLE
        trendIndicator.animate().cancel()
        if (current > previous) {
            trendIndicator.rotation = 180f
            trendIndicator.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.duo_lime)
            trendIndicator.animate().translationY(-10f).setDuration(300).withEndAction { 
                trendIndicator.animate().translationY(0f).setDuration(300).start() 
            }.start()
        } else {
            trendIndicator.rotation = 0f
            trendIndicator.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.brand_warning)
            trendIndicator.animate().translationY(10f).setDuration(300).withEndAction { 
                trendIndicator.animate().translationY(0f).setDuration(300).start() 
            }.start()
        }
    }

    private fun startConfetti() {
        if (!isAdded) return
        val colors = listOf(0xFF4CB5F9.toInt(), 0xFF58CC02.toInt(), 0xFFFFD700.toInt(), 0xFFFF4B4B.toInt())
        for (i in 0..30) {
            val confetti = View(requireContext())
            val size = dpToPx(requireContext(), (4..8).random())
            confetti.layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                leftMargin = (-300..300).random()
            }
            confetti.background = GradientDrawable().apply {
                shape = if (i % 2 == 0) GradientDrawable.RECTANGLE else GradientDrawable.OVAL
                setColor(colors.random())
            }
            confettiContainer.addView(confetti)
            confetti.animate()
                .translationY(confettiContainer.height.toFloat() + 200)
                .rotation((0..360).random().toFloat())
                .setDuration((1500..3000).random().toLong())
                .withEndAction { confettiContainer.removeView(confetti) }
                .start()
        }
    }

    private fun startBubbleAnimation() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isAdded) {
                    spawnBubble()
                    handler.postDelayed(this, (400..900).random().toLong())
                }
            }
        }
        handler.post(runnable)
    }

    private fun spawnBubble() {
        val bubbleSizePx = dpToPx(requireContext(), (6..12).random())
        val bubble = View(requireContext())
        val maxLeft = waterLevel.width - bubbleSizePx
        val leftMargin = if (maxLeft > 0) (0..maxLeft).random() else 0
        bubble.layoutParams = FrameLayout.LayoutParams(bubbleSizePx, bubbleSizePx).apply {
            gravity = Gravity.BOTTOM
            this.leftMargin = leftMargin
            bottomMargin = 10
        }
        bubble.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x66FFFFFF)
        }
        waterLevel.addView(bubble)
        bubble.animate()
            .translationY(-waterLevel.height.toFloat())
            .alpha(0f)
            .setDuration((3000..6000).random().toLong())
            .withEndAction { waterLevel.removeView(bubble) }
            .start()
    }

    private fun handleNotificationButtonClick() {
        if (NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) {
            notificationHelper.sendNotification("Manual Test", "Notifications are currently enabled!", 999)
        } else {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
            startActivity(intent)
        }
    }

    private fun updateNotificationCardVisibility() {
        notificationPermissionCard.visibility = if (NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) View.GONE else View.VISIBLE
    }

    private fun loadCalibrationSettings() {
        emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
        fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
        tankVolume = sharedPref.getInt("tank_volume", 2000).toDouble()
        volumeUnit = sharedPref.getString("volume_unit", "L") ?: "L"
    }

    private fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        sharedPref.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}
