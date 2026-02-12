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

    private lateinit var userNameTextView: TextView
    private lateinit var themeToggle: FrameLayout
    private lateinit var themeIcon: ImageView

    private lateinit var sharedPref: SharedPreferences
    private lateinit var viewModel: ReadingViewModel
    
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
        checkUserPersistentData()

        themeToggle = findViewById(R.id.themeToggle)
        themeIcon = findViewById(R.id.themeIcon)
        updateThemeIcon()

        themeToggle.setOnClickListener {
            performHapticFeedbackCommon(it)
            val isDark = sharedPref.getBoolean("is_dark_mode", false)
            val newMode = !isDark
            sharedPref.edit().putBoolean("is_dark_mode", newMode).apply()
            
            AppCompatDelegate.setDefaultNightMode(
                if (newMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        setupNavigation(savedInstanceState == null)
        scheduleBackgroundWorker()
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
            performHapticFeedbackCommon(it)
            applyNavbarClickAnimation(it) {
                if (viewPager.currentItem == 1) {
                    // If already on Home, perform refresh
                    performRefreshVibration()
                    imgHome.animate().rotationBy(360f).setDuration(500).start()
                    db.collection("sensorCommands").document("esp32_01").update("refresh", true)
                } else {
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

    private fun updateNavbarUI(position: Int) {
        val icons = listOf(imgAnalytics, imgHome, imgSettings)
        val containers = listOf(navAnalytics, navHome, navSettings)

        // Reset all icons to inactive state and ensure zero rotation
        icons.forEach { 
            it.animate().cancel()
            it.rotation = 0f
            it.setPadding(dpToPx(this, 16), dpToPx(this, 16), dpToPx(this, 16), dpToPx(this, 16))
            it.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.duo_text_secondary)
            )
        }

        // Toggle Home icon vs Refresh icon
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
            android.graphics.Color.WHITE
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(50)
            }
        }
    }

    private fun performRefreshVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(30, 70, 30, 70, 30, 70, 30, 70, 30, 70)
                val amplitudes = intArrayOf(
                    VibrationEffect.DEFAULT_AMPLITUDE, 0,
                    VibrationEffect.DEFAULT_AMPLITUDE, 0,
                    VibrationEffect.DEFAULT_AMPLITUDE, 0,
                    VibrationEffect.DEFAULT_AMPLITUDE, 0,
                    VibrationEffect.DEFAULT_AMPLITUDE, 0
                )
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 30, 70, 30, 70, 30, 70, 30, 70, 30, 70), -1)
            }
        }
    }

    private fun applyNavbarClickAnimation(view: View, action: () -> Unit) {
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        }.start()
        action()
    }

    private fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
