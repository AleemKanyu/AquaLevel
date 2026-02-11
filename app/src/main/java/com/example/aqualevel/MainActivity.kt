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
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import android.view.HapticFeedbackConstants
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.work.*
import com.google.firebase.firestore.*
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var mondayBar: View
    private lateinit var tuesdayBar: View
    private lateinit var wedBar: View
    private lateinit var thursdayBar: View
    private lateinit var fridayBar: View
    private lateinit var saturdayBar: View
    private lateinit var sundayBar: View

    private lateinit var analyticsButton: FrameLayout
    private lateinit var settingsButton: FrameLayout
    private lateinit var themeToggle: FrameLayout
    private lateinit var themeIcon: ImageView

    private lateinit var buttonCheck: ImageView
    private lateinit var capacityValue: TextView
    private lateinit var dailyAvgValue: TextView
    private lateinit var percentage: TextView
    private lateinit var waterLevel: FrameLayout
    private lateinit var tankContainer: FrameLayout
    private lateinit var userNameTextView: TextView

    private lateinit var notificationPermissionCard: View
    private lateinit var btnEnableNotifications: Button

    private lateinit var viewModel: ReadingViewModel
    private lateinit var sharedPref: SharedPreferences
    private lateinit var notificationHelper: NotificationHelper

    private var value: Double = 0.0
    private var listener: ListenerRegistration? = null

    private val db = FirebaseFirestore.getInstance()
    private val docRef = db.collection("sensorData").document("esp32_01")

    // ---------- CALIBRATION CONSTANTS (Default) ----------
    private var emptyDistance = 130.0
    private var fullDistance = 20.0
    private var tankVolume = 2000.0 // Total volume updated to 2000L
    // ------------------------------------------

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notifications enabled.", Toast.LENGTH_SHORT).show()
            notificationHelper.sendNotification("Notifications Enabled", "You will now receive water level alerts.", 999)
        }
        updateNotificationCardVisibility()
    }

    override fun onStart() {
        super.onStart()
        loadCalibrationSettings()
        setupFirebaseListener()
        startBubbleAnimation()
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
        stopBubbleAnimation()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationCardVisibility()
    }

    private fun updateNotificationCardVisibility() {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            notificationPermissionCard.visibility = View.GONE
        } else {
            notificationPermissionCard.visibility = View.VISIBLE
        }
    }

    private fun handleNotificationButtonClick() {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            notificationHelper.sendNotification("Manual Test", "Notifications are currently enabled!", 999)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Compatible redirection for Android 7 to 12
                val intent = Intent()
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    else -> {
                        // Android 7 (Nougat)
                        intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
                        intent.putExtra("app_package", packageName)
                        intent.putExtra("app_uid", applicationInfo.uid)
                    }
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    fallbackIntent.data = Uri.fromParts("package", packageName, null)
                    startActivity(fallbackIntent)
                }
                Toast.makeText(this, "Please enable notifications in settings", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadCalibrationSettings() {
        emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
        fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
        tankVolume = sharedPref.getInt("tank_volume", 2000).toDouble()
    }

    private fun setupFirebaseListener() {
        listener?.remove()
        listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val distance = snapshot.getDouble("distance") ?: return@addSnapshotListener
            
            val clampedDistance = distance.coerceIn(fullDistance, emptyDistance)
            val percent = ((emptyDistance - clampedDistance) / (emptyDistance - fullDistance)) * 100.0
            val safePercent = percent.coerceIn(0.0, 100.0)

            value = (safePercent / 100.0) * tankVolume

            capacityValue.text = "${value.toInt()} litres"
            percentage.text = "${safePercent.toInt()}%"

            val params = waterLevel.layoutParams
            params.height = dpToPx(this, ((280 * safePercent) / 100).toInt())
            waterLevel.layoutParams = params

            checkAndSendNotifications(safePercent.toInt())
            viewModel.addReading(Readings(level = distance, timestamp = System.currentTimeMillis()))

            // Update Widget Data
            sharedPref.edit()
                .putInt("last_percentage", safePercent.toInt())
                .putInt("last_volume", value.toInt())
                .putLong("last_update_timestamp", System.currentTimeMillis())
                .apply()
            
            val intent = Intent(this, WaterLevelWidget::class.java)
            intent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = android.appwidget.AppWidgetManager.getInstance(application).getAppWidgetIds(android.content.ComponentName(application, WaterLevelWidget::class.java))
            intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            sendBroadcast(intent)
        }
    }

    private fun checkAndSendNotifications(percent: Int) {
        val lastNotified = sharedPref.getInt("last_notified_level", -1)
        
        // Notify at 100%
        if (percent >= 100 && lastNotified != 100) {
            notificationHelper.sendNotification("Tank Full!", "Your water tank is now 100% full.", 1001)
            sharedPref.edit().putInt("last_notified_level", 100).apply()
        } 
        // Notify at or below 30%
        else if (percent <= 30 && lastNotified != 30) {
            notificationHelper.sendNotification("Low Water Level", "Warning: Tank level is at ${percent}%.", 1002)
            sharedPref.edit().putInt("last_notified_level", 30).apply()
        }
        // Reset state if level is between 31 and 99
        else if (percent in 31..99) {
            sharedPref.edit().putInt("last_notified_level", -1).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        notificationHelper = NotificationHelper(this)
        applyTheme()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[ReadingViewModel::class.java]

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        userNameTextView = findViewById(R.id.userName)
        analyticsButton = findViewById(R.id.analyticsButton)
        settingsButton = findViewById(R.id.settingsButton)
        buttonCheck = findViewById(R.id.buttonCheck)
        capacityValue = findViewById(R.id.capacityValue)
        dailyAvgValue = findViewById(R.id.dailyAvgValue)
        percentage = findViewById(R.id.percentage)
        waterLevel = findViewById(R.id.waterLevel)
        tankContainer = findViewById(R.id.tankContainer)
        
        notificationPermissionCard = findViewById(R.id.notificationPermissionCard)
        btnEnableNotifications = findViewById(R.id.btnEnableNotifications)

        mondayBar = findViewById(R.id.mondayBar)
        tuesdayBar = findViewById(R.id.tuesdayBar)
        wedBar = findViewById(R.id.wedBar)
        thursdayBar = findViewById(R.id.thursdayBar)
        fridayBar = findViewById(R.id.fridayBar)
        saturdayBar = findViewById(R.id.saturdayBar)
        sundayBar = findViewById(R.id.sundayBar)

        themeToggle = findViewById(R.id.themeToggle)
        themeIcon = findViewById(R.id.themeIcon)
        updateThemeIcon()

        themeToggle.setOnClickListener {
            performHapticFeedbackCommon(it)
            val isDark = sharedPref.getBoolean("is_dark_mode", false)
            val newMode = !isDark
            sharedPref.edit().putBoolean("is_dark_mode", newMode).apply()
            
            // Apply mode immediately to ensure recreate() picks it up
            AppCompatDelegate.setDefaultNightMode(
                if (newMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        btnEnableNotifications.setOnClickListener {
            performHapticFeedbackCommon(it)
            handleNotificationButtonClick()
        }

        viewModel.weeklyUsage.observe(this) { days ->
            val bars = listOf(mondayBar, tuesdayBar, wedBar, thursdayBar, fridayBar, saturdayBar, sundayBar)
            val maxBarHeight = dpToPx(this, 160)
            val totalDistRange = emptyDistance - fullDistance

            // Get current day of week (0-6 starting Monday)
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val currentDayIndex = when (dayOfWeek) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }

            bars.forEachIndexed { index, bar ->
                val params = bar.layoutParams
                params.height = dpToPx(this, 8)
                bar.layoutParams = params
                
                // Set default color for other days
                bar.backgroundTintList = ContextCompat.getColorStateList(this, if (index == currentDayIndex) R.color.blue_dark else R.color.blue_light)
            }

            var totalWeeklyUsedDist = 0.0
            var daysWithData = 0

            days.take(7).forEachIndexed { index, usage ->
                val usedDist = (usage.maxLevel - usage.minLevel).coerceAtLeast(0.0)
                
                if (usedDist > 0) {
                    totalWeeklyUsedDist += usedDist
                    daysWithData++
                }

                val normalized = (usedDist / totalDistRange).coerceIn(0.0, 1.0)
                val barHeight = (maxBarHeight * normalized).toInt().coerceAtLeast(dpToPx(this, 8))

                val barIndex = 6 - index
                if (barIndex in bars.indices) {
                    val params = bars[barIndex].layoutParams
                    params.height = barHeight
                    bars[barIndex].layoutParams = params
                }
            }

            // Calculate Daily Average
            if (daysWithData > 0) {
                val avgDist = totalWeeklyUsedDist / daysWithData
                val avgVolume = (avgDist / totalDistRange) * tankVolume
                dailyAvgValue.text = "${avgVolume.toInt()} L"
            } else {
                dailyAvgValue.text = "0 L"
            }
        }

        scheduleBackgroundWorker()

        buttonCheck.setOnClickListener {
            performHapticFeedbackCommon(it)
            it.animate().rotationBy(360f).setDuration(500).start()
            db.collection("sensorCommands").document("esp32_01").update("refresh", true)
        }

        analyticsButton.setOnClickListener {
            performHapticFeedbackCommon(it)
            applyClickAnimation(it) {
                startActivity(Intent(this, Analytics::class.java))
            }
        }

        settingsButton.setOnClickListener {
            performHapticFeedbackCommon(it)
            applyClickAnimation(it) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        checkUserPersistentData()
    }

    private fun applyTheme() {
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun applyClickAnimation(view: View, onAnimationEnd: () -> Unit) {
        view.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
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

    private fun checkUserPersistentData() {
        val savedName = sharedPref.getString("user_name", null)
        if (savedName == null) showNameInputDialog() else userNameTextView.text = savedName
    }

    private fun showNameInputDialog() {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_user_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.userNameInput)
        val saveButton = dialogView.findViewById<Button>(R.id.saveButton)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        saveButton.setOnClickListener {
            val name = editText.text.toString().trim()
            if (name.isNotEmpty()) {
                sharedPref.edit().putString("user_name", name).apply()
                userNameTextView.text = name
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun updateThemeIcon() {
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        themeIcon.setImageResource(if (isDark) R.drawable.ic_sun else R.drawable.ic_moon)
    }

    private var bubbleHandler: Handler? = null
    private var bubbleRunnable: Runnable? = null

    private fun startBubbleAnimation() {
        bubbleHandler = Handler(mainLooper)
        bubbleRunnable = object : Runnable {
            override fun run() {
                spawnBubble(this@MainActivity, waterLevel)
                bubbleHandler?.postDelayed(this, (400..900).random().toLong())
            }
        }
        bubbleHandler?.post(bubbleRunnable!!)
    }

    private fun stopBubbleAnimation() {
        bubbleHandler?.removeCallbacksAndMessages(null)
    }

    private fun scheduleBackgroundWorker() {
        val workRequest = PeriodicWorkRequestBuilder<WaterLevelWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("water_level_monitor", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    fun spawnBubble(context: Context, waterLevel: FrameLayout) {
        val bubbleSizePx = dpToPx(context, (6..12).random())
        val bubble = View(context)
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

    private fun performHapticFeedbackCommon(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
             // Fallback for older Android versions (like Android 7)
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(50) // Vibrate for 50ms
            }
        }
    }
}
