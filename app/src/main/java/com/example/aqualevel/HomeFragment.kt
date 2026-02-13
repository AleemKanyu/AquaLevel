package com.example.aqualevel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.*
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.util.*
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

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

    private var value: Double = 0.0
    private var listener: ListenerRegistration? = null

    private val db = FirebaseFirestore.getInstance()
    private val docRef = db.collection("sensorData").document("esp32_01")

    private var emptyDistance = 130.0
    private var fullDistance = 20.0
    private var tankVolume = 2000.0
    private var volumeUnit = "L"

    private var lastAvgDailyVolume = 0.0
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
            // Force refresh UI values
            viewModel.allReadings.value?.let { updateWeeklyBars(it) }
            setupFirebaseListener() // Refresh current value labels
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
        
        // Apply gyro water preference
        val prefs = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val gyroEnabled = prefs.getBoolean("gyro_water_enabled", false)
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

        btnEnableNotifications.setOnClickListener {
            handleNotificationButtonClick()
        }

        // Add polish to stats cards
        val statsContainer = view.findViewById<LinearLayout>(R.id.statsRow)
        for (i in 0 until statsContainer.childCount) {
            val child = statsContainer.getChildAt(i)
            if (child is LinearLayout) {
                child.setOnClickListener {
                    performHapticFeedbackCommon(it)
                    applyClickAnimation(it) {
                        // Just animation for now, can add navigation later
                    }
                }
            }
        }

        // Add rotate animation to sync icon (managed via imgHome in MainActivity)
        // Note: HomeFragment doesn't have direct access to the refresh button pulse logic 
        // as it's triggered from MainActivity's Refresh icon. 
        // But we can add it to any local refresh trigger if planned.

        // Authentic Features: Splash on Touch
        tankContainer.setOnClickListener {
            waterLevel.splash()
            performHapticFeedbackCommon(view)
        }

        viewModel.allReadings.observe(viewLifecycleOwner) { readings ->
            updateWeeklyBars(readings)
        }

        loadCalibrationSettings()
        setupFirebaseListener()
        setupInteractionListeners()
        startBubbleAnimation()
    }

    private fun performHapticFeedbackCommon(view: View) {
        val vibrationEnabled = sharedPref.getBoolean("vibration_enabled", true)
        if (!vibrationEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) vibrator.vibrate(50)
        }
    }

    private fun applyClickAnimation(view: View, onAnimationEnd: () -> Unit) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction { onAnimationEnd() }.start()
        }.start()
    }

    private fun updateWeeklyBars(allReadings: List<Readings>) {
        val bars = listOf(mondayBar, tuesdayBar, wedBar, thursdayBar, fridayBar, saturdayBar, sundayBar)
        val maxBarHeight = dpToPx(requireContext(), 160)
        val totalDistRange = emptyDistance - fullDistance

        val calendar = Calendar.getInstance()
        val currentDayIndex = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        // Initialize bars background and highlight current day
        bars.forEachIndexed { index, bar ->
            val params = bar.layoutParams
            params.height = dpToPx(requireContext(), 8)
            bar.layoutParams = params
            bar.backgroundTintList = ContextCompat.getColorStateList(requireContext(), 
                if (index == currentDayIndex) R.color.brand_primary_shadow else R.color.brand_primary)
        }

        var totalWeeklyUsedVol = 0.0
        var daysWithData = 0

        // Get start of the week (Monday) at midnight
        val monday = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Iterate through Monday to Sunday
        for (i in 0..6) {
            val dayCalendar = (monday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, i)
            }
            
            // Filter readings for this specific day
            val dayReadings = allReadings.filter { isSameDay(it.timestamp, dayCalendar.timeInMillis) }
            val dayVolume = calculateConsumption(dayReadings)

            if (dayVolume > 0) {
                totalWeeklyUsedVol += dayVolume
                daysWithData++
            }

            val normalized = (dayVolume / tankVolume).coerceIn(0.0, 1.0)
            val barHeight = (maxBarHeight * normalized).toInt().coerceAtLeast(dpToPx(requireContext(), 8))
            
            val params = bars[i].layoutParams
            params.height = barHeight
            bars[i].layoutParams = params
        }

        if (daysWithData > 0) {
            val avgVolume = totalWeeklyUsedVol / daysWithData
            lastAvgDailyVolume = avgVolume // Store for smart prediction
            if (volumeUnit == "gal") {
                val calVal = avgVolume * 0.264172
                dailyAvgValue.text = "${calVal.toInt()} gal"
            } else {
                dailyAvgValue.text = "${avgVolume.toInt()} L"
            }
        } else {
            dailyAvgValue.text = if (volumeUnit == "gal") "0 gal" else "0 L"
        }
    }

    private fun calculateConsumption(readings: List<Readings>): Double {
        if (readings.size < 2) return 0.0
        val sortedReadings = readings.sortedBy { it.timestamp }
        var totalUsedDist = 0.0
        val totalDistRange = emptyDistance - fullDistance
        if (totalDistRange <= 0) return 0.0

        for (i in 0 until sortedReadings.size - 1) {
            val r1 = sortedReadings[i]
            val r2 = sortedReadings[i + 1]
            // Sum only positive distance increases (water level drops)
            if (r2.level > r1.level) {
                totalUsedDist += (r2.level - r1.level)
            }
        }
        return (totalUsedDist / totalDistRange) * tankVolume
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val d1 = Instant.ofEpochMilli(t1).atZone(ZoneId.systemDefault()).toLocalDate()
        val d2 = Instant.ofEpochMilli(t2).atZone(ZoneId.systemDefault()).toLocalDate()
        return d1 == d2
    }

    private fun handleNotificationButtonClick() {
        if (NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) {
            notificationHelper.sendNotification("Manual Test", "Notifications are currently enabled!", 999)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val intent = Intent()
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    }
                    else -> {
                        intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
                        intent.putExtra("app_package", requireContext().packageName)
                        intent.putExtra("app_uid", requireContext().applicationInfo.uid)
                    }
                }
                startActivity(intent)
            }
        }
    }

    private fun updateNotificationCardVisibility() {
        if (NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) {
            notificationPermissionCard.visibility = View.GONE
        } else {
            notificationPermissionCard.visibility = View.VISIBLE
        }
    }

    private fun loadCalibrationSettings() {
        emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
        fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
        tankVolume = sharedPref.getInt("tank_volume", 2000).toDouble()
        volumeUnit = sharedPref.getString("volume_unit", "L") ?: "L"
    }

    private fun setupFirebaseListener() {
        listener?.remove()
        listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val distance = snapshot.getDouble("distance") ?: return@addSnapshotListener
            if (distance <= 0.0) return@addSnapshotListener
            
            val clampedDistance = distance.coerceIn(fullDistance, emptyDistance)
            val percent = ((emptyDistance - clampedDistance) / (emptyDistance - fullDistance)) * 100.0
            val safePercent = percent.coerceIn(0.0, 100.0)
            value = (safePercent / 100.0) * tankVolume
            
            if (volumeUnit == "gal") {
                val galVal = value * 0.264172
                val oldGalVal = lastDisplayedValue * 0.264172
                capacityValue.animateCountUp(galVal.toInt(), oldGalVal.toInt(), 1000, " gallons")
            } else {
                capacityValue.animateCountUp(value.toInt(), lastDisplayedValue.toInt(), 1000, " litres")
            }
            
            percentage.animateCountUp(safePercent.toInt(), lastDisplayedPercent, 1000, "%")
            
            lastDisplayedValue = value
            lastDisplayedPercent = safePercent.toInt()
            // Animate water level
            if (isFirstLoad) {
                // First load: Animate from 0 to current
                animateWaterLevel(0, safePercent.toInt(), 2000)
                isFirstLoad = false
            } else {
                // Update: Animate from last to current
                animateWaterLevel(lastDisplayedPercent, safePercent.toInt(), 1000)
            }
            
            // waterLevel.setWaterLevel(safePercent.toInt()) // Replaced by animation

            // Feature: Dynamic Wave Color based on level
            val waveColor = if (safePercent <= 25) {
                ContextCompat.getColor(requireContext(), R.color.brand_warning)
            } else {
                ContextCompat.getColor(requireContext(), R.color.duo_blue)
            }
            waterLevel.setWaveColor(waveColor)

            // Feature: Refill detection
            if (lastKnownPercentage != -1 && safePercent > lastKnownPercentage + 10) {
                onRefillDetected()
            }
            // Trend Indicator
            if (lastKnownPercentage != -1) {
                updateTrendIndicator(safePercent.toInt(), lastKnownPercentage)
            }
            lastKnownPercentage = safePercent.toInt()


            checkAndSendNotifications(safePercent.toInt())
            viewModel.addReading(Readings(level = distance, timestamp = System.currentTimeMillis()))

            sharedPref.edit()
                .putInt("last_percentage", safePercent.toInt())
                .putInt("last_volume", value.toInt())
                .putLong("last_update_timestamp", System.currentTimeMillis())
                .apply()

            val widgetIntent = Intent(requireContext(), WaterLevelWidget::class.java)
            widgetIntent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(requireContext())
            val componentName = android.content.ComponentName(requireContext(), WaterLevelWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            widgetIntent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            requireContext().sendBroadcast(widgetIntent)
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

    private var bubbleHandler: Handler? = null
    private var bubbleRunnable: Runnable? = null

    private fun startBubbleAnimation() {
        bubbleHandler = Handler(Looper.getMainLooper())
        bubbleRunnable = object : Runnable {
            override fun run() {
                spawnBubble()
                bubbleHandler?.postDelayed(this, (400..900).random().toLong())
            }
        }
        bubbleHandler?.post(bubbleRunnable!!)
    }

    private fun spawnBubble() {
        if (!isAdded) return
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

    override fun onDestroyView() {
        super.onDestroyView()
        sharedPref.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        bubbleHandler?.removeCallbacksAndMessages(null)
        listener?.remove()
    }

    private fun setupInteractionListeners() {
        // Haptic feedback for main usage card
        val usageCard = view?.findViewById<View>(R.id.usageCardContainer) ?: view?.findViewById<FrameLayout>(R.id.mondayBar)?.parent?.parent?.parent as? View
        usageCard?.setOnClickListener {
            performHapticFeedbackCommon(it)
        }

        // Haptic feedback for stats cards (using IDs if found, or positions)
        view?.findViewById<View>(R.id.capacityCard)?.setOnClickListener { performHapticFeedbackCommon(it) }
        view?.findViewById<View>(R.id.avgUsageCard)?.setOnClickListener { performHapticFeedbackCommon(it) }
    }


    private fun onRefillDetected() {
        view?.let { performHapticFeedbackCommon(it) }
        // Pulse animation on percentage text
        percentage.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .setDuration(300)
            .withEndAction {
                percentage.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
            }
            .start()
        
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
            // Filling up - Arrow Up (Greenish)
            trendIndicator.rotation = 180f
            trendIndicator.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.duo_lime)
            trendIndicator.animate().translationY(-10f).setDuration(300).withEndAction { 
                trendIndicator.animate().translationY(0f).setDuration(300).start() 
            }.start()
        } else {
            // Using water - Arrow Down (Reddish/Orange)
            trendIndicator.rotation = 0f
            trendIndicator.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.brand_warning)
            trendIndicator.animate().translationY(10f).setDuration(300).withEndAction { 
                trendIndicator.animate().translationY(0f).setDuration(300).start() 
            }.start()
        }
    }

    private fun startConfetti() {
        if (!isAdded) return
        val colors = listOf(
            0xFF4CB5F9.toInt(), // Blue
            0xFF58CC02.toInt(), // Green
            0xFFFFD700.toInt(), // Yellow
            0xFFFF4B4B.toInt()  // Red
        )
        
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

    private fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
