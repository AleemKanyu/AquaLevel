package com.example.aqualevel

import android.content.Context
import android.content.SharedPreferences
import android.os.*
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import androidx.work.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var navHome: FrameLayout
    private lateinit var navAnalytics: FrameLayout
    private lateinit var navSettings: FrameLayout
    
    private lateinit var imgHome: ImageView
    private lateinit var imgAnalytics: ImageView
    private lateinit var imgSettings: ImageView
    private lateinit var navSelector: View

    private lateinit var tvWelcome: TextView
    private lateinit var userNameTextView: TextView
    private lateinit var themeToggle: FrameLayout
    private lateinit var themeIcon: ImageView

    private lateinit var sharedPref: SharedPreferences
    private lateinit var viewModel: ReadingViewModel
    private lateinit var updateManager: UpdateManager
    
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
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
        tvWelcome = findViewById(R.id.tvWelcome) // Initialize tvWelcome
        checkUserPersistentData()

        themeToggle = findViewById(R.id.themeToggle)
        themeIcon = findViewById(R.id.themeIcon)
        updateThemeIcon()

        themeToggle.setOnClickListener {
            performHapticFeedbackCommon(it)
            // Animate only the icon but don't wait for it
            applyPremiumPopAnimation(themeIcon) { } 
            
            // Execute logic immediately
            val isDark = sharedPref.getBoolean("is_dark_mode", false)
            val newMode = !isDark
            sharedPref.edit().putBoolean("is_dark_mode", newMode).apply()
            
            // Small delay to allow the ripple/touch feedback to be seen before recreation
            it.postDelayed({
                AppCompatDelegate.setDefaultNightMode(
                    if (newMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }, 50) 
        }

        setupNavigation(savedInstanceState == null)
        scheduleBackgroundWorker()
        
        updateManager = UpdateManager(this)
        checkForUpdates()
    }

    private fun checkForUpdates() {
        updateManager.checkForUpdates(0) { tag, url ->
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("New Update Available")
                .setMessage("A new version ($tag) is available on GitHub. Would you like to download it?")
                .setPositiveButton("Download") { _, _ -> updateManager.downloadAndInstall(url) }
                .setNegativeButton("Later") { _, _ ->
                    sharedPref.edit().putString("skipped_update_version", tag).apply()
                }
                .show()
        }
    }

    private fun setupNavigation(isFirstLaunch: Boolean) {
        viewPager = findViewById(R.id.viewPager)
        navHome = findViewById(R.id.navHome)
        navAnalytics = findViewById(R.id.navAnalytics)
        navSettings = findViewById(R.id.navSettings)
        
        imgHome = findViewById(R.id.imgHome)
        imgAnalytics = findViewById(R.id.imgAnalytics)
        imgSettings = findViewById(R.id.imgSettings)
        navSelector = findViewById(R.id.navSelector)

        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 2
        
        // Default to Home (Index 1) only on first launch
        if (isFirstLaunch) {
            viewPager.setCurrentItem(1, false)
            updateNavbarUI(1)
        } else {
            // ViewPager2 restores itself, but we need to sync the UI highlight
            viewPager.post {
                updateNavbarUI(viewPager.currentItem)
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavbarUI(position)
            }
        })

        navHome.setOnClickListener {
            if (viewPager.currentItem == 1) {
                // Already on Home — play the refresh animation
                applyRefreshAnimation(imgHome) {
                    // Refresh data
                }
                db.collection("sensorCommands").document("esp32_01").update("refresh", true)
            } else {
                performPremiumHaptic()
                applyPremiumPopAnimation(it) {
                    viewPager.currentItem = 1
                }
            }
        }
        navAnalytics.setOnClickListener { 
            performHapticFeedbackCommon(it)
            applyNavbarClickAnimation(it) {
                viewPager.currentItem = 0 
            }
        }
        navSettings.setOnClickListener { 
            performHapticFeedbackCommon(it)
            applyNavbarClickAnimation(it) {
                viewPager.currentItem = 2 
            }
        }
    }

    // ...

    private fun updateNavbarUI(position: Int) {
        val icons = listOf(imgAnalytics, imgHome, imgSettings)
        val containers = listOf(navAnalytics, navHome, navSettings)

        // Update Header Text based on position
        val userName = sharedPref.getString("user_name", "User") ?: "User"
        when (position) {
            0 -> { // Analytics
                tvWelcome.text = "Your"
                userNameTextView.text = "Analytics"
            }
            1 -> { // Home
                tvWelcome.text = "Welcome back,"
                userNameTextView.text = userName
            }
            2 -> { // Settings
                tvWelcome.text = "App"
                userNameTextView.text = "Settings"
            }
        }

        // Reset all icons to inactive state and ensure zero rotation
        icons.forEach { 
            it.animate().cancel()
            it.rotation = 0f
            it.setPadding(dpToPx(this, 16), dpToPx(this, 16), dpToPx(this, 16), dpToPx(this, 16))
            it.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.duo_text_secondary)
            )
        }

        // Home icon changes to refresh when on the home screen
        if (position == 1) {
            imgHome.setImageResource(R.drawable.ic_sync)
        } else {
            imgHome.setImageResource(R.drawable.ic_home)
        }

        val activeContainer = containers[position]
        val activeImg = icons[position]

        // Prepare selector for animation
        if (navSelector.visibility != View.VISIBLE) {
            navSelector.visibility = View.VISIBLE
            // Position immediately on first run
            activeContainer.post {
                navSelector.x = activeContainer.x
            }
        } else {
            // Animate selector to new position with "liquid" spring feel
            navSelector.animate()
                .x(activeContainer.x)
                .setDuration(400)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                .start()
        }

        // Highlight active icon
        activeImg.setPadding(dpToPx(this, 12), dpToPx(this, 12), dpToPx(this, 12), dpToPx(this, 12))
        activeImg.imageTintList = android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(this, R.color.white)
        )

        // Optional: Small scale animation on the icon for extra "pop"
        activeImg.scaleX = 0.8f
        activeImg.scaleY = 0.8f
        activeImg.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
    }

    private fun applyTheme() {
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun updateThemeIcon() {
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        themeIcon.setImageResource(if (isDark) R.drawable.ic_sun else R.drawable.ic_moon)
    }

    private fun checkUserPersistentData() {
        val savedName = sharedPref.getString("user_name", null)
        if (savedName == null) showNameInputDialog() else userNameTextView.text = savedName
    }

    private fun showNameInputDialog() {
        val inflater = android.view.LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_user_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.userNameInput)
        val saveButton = dialogView.findViewById<Button>(R.id.saveButton)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

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

    private fun scheduleBackgroundWorker() {
        val workRequest = PeriodicWorkRequestBuilder<WaterLevelWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("water_level_monitor", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun performHapticFeedbackCommon(view: View) {
        if (!sharedPref.getBoolean("vibration_enabled", true)) return
        
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    /** Water-drop style haptic: strong tap + two softer echoes */
    private fun performWaterDropHaptic() {
        if (!sharedPref.getBoolean("vibration_enabled", true)) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Pattern: strong hit, pause, medium echo, pause, light echo
            val timings   = longArrayOf(0, 60, 80, 30, 100, 15)
            val amplitudes = intArrayOf(0, 255, 0, 160, 0, 80)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 60, 80, 30, 100, 15), -1)
        }
    }

    private fun applyNavbarClickAnimation(view: View, action: () -> Unit) {
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        }.start()
        action()
    }

    /** Premium "Liquid Pop" animation */
    private fun applyPremiumPopAnimation(view: View, onEnd: (() -> Unit)? = null) {
        val originalScaleX = view.scaleX
        val originalScaleY = view.scaleY

        // Step 1: Squash & Anticipate (Quick dip)
        view.animate()
            .scaleX(originalScaleX * 1.15f)
            .scaleY(originalScaleY * 0.75f)
            .translationY(dpToPx(this, 4).toFloat())
            .setDuration(80)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                // Step 2: Elastic Pop Up
                view.animate()
                    .scaleX(originalScaleX * 0.85f)
                    .scaleY(originalScaleY * 1.35f)
                    .translationY(-dpToPx(this, 12).toFloat())
                    .setDuration(120)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withEndAction {
                        // Step 3: Soft Settle
                        view.animate()
                            .scaleX(originalScaleX)
                            .scaleY(originalScaleY)
                            .translationY(0f)
                            .setDuration(500)
                            .setInterpolator(android.view.animation.AnticipateOvershootInterpolator(2.0f))
                            .withEndAction { onEnd?.invoke() }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    /** Rotation "Refresh" animation with continuous vibration */
    private fun applyRefreshAnimation(view: View, onEnd: (() -> Unit)? = null) {
        view.animate().cancel()
        
        // Phase 1: Spin up and scale down
        view.animate()
            .rotationBy(360f * 2) // Rotate 720 degrees
            .scaleX(0.7f)
            .scaleY(0.7f)
            .setDuration(400)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                // Phase 2: Pop back to original size
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2.0f))
                    .withEndAction {
                        view.rotation = 0f
                        onEnd?.invoke()
                    }
                    .start()
            }
            .start()
            
        performRefreshHaptic()
    }

    private fun performRefreshHaptic() {
        if (!sharedPref.getBoolean("vibration_enabled", true)) return
        val vibrator = getVibrator() ?: return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // A ramping rumble followed by a crisp hit when it pops back
            val timings = longArrayOf(0, 50, 30, 50, 30, 50, 30, 50, 30, 150, 80)
            val amplitudes = intArrayOf(0, 60, 0, 80, 0, 120, 0, 160, 0, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 50, 30, 50, 30, 50, 30, 50, 30, 150, 80), -1)
        }
    }

    private fun performPremiumHaptic() {
        if (!sharedPref.getBoolean("vibration_enabled", true)) return
        val vibrator = getVibrator() ?: return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            vibrator.vibrate(VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.4f, 60)
                .compose())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Crisp hit + soft echo
            val timings = longArrayOf(0, 15, 60, 10)
            val amplitudes = intArrayOf(0, 255, 0, 100)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
