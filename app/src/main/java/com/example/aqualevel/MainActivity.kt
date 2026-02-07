package com.example.aqualevel

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.work.*
import com.google.firebase.firestore.*
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
    private lateinit var percentage: TextView
    private lateinit var waterLevel: FrameLayout
    private lateinit var tankContainer: FrameLayout
    private lateinit var userNameTextView: TextView

    private lateinit var viewModel: ReadingViewModel

    private var value: Double = 0.0
    private var listener: ListenerRegistration? = null

    private val db = FirebaseFirestore.getInstance()
    private val docRef = db.collection("sensorData").document("esp32_01")

    // ---------- CALIBRATION CONSTANTS (Default) ----------
    private var emptyDistance = 130.0
    private var fullDistance = 20.0
    private var tankVolume = 1000.0
    // ------------------------------------------

    override fun onStart() {
        super.onStart()
        loadCalibrationSettings()
        setupFirebaseListener()
        startBubbleAnimation()
    }

    private fun loadCalibrationSettings() {
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        emptyDistance = sharedPref.getFloat("empty_distance", 130.0f).toDouble()
        fullDistance = sharedPref.getFloat("full_distance", 20.0f).toDouble()
        tankVolume = sharedPref.getInt("tank_volume", 1000).toDouble()
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

            viewModel.addReading(Readings(level = distance, timestamp = System.currentTimeMillis()))
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
        stopBubbleAnimation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        percentage = findViewById(R.id.percentage)
        waterLevel = findViewById(R.id.waterLevel)
        tankContainer = findViewById(R.id.tankContainer)
        
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
            val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            AppCompatDelegate.setDefaultNightMode(if (isDark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)
            updateThemeIcon()
        }

        viewModel.weeklyUsage.observe(this) { days ->
            val bars = listOf(mondayBar, tuesdayBar, wedBar, thursdayBar, fridayBar, saturdayBar, sundayBar)
            val maxBarHeight = dpToPx(this, 160)
            val totalDistRange = emptyDistance - fullDistance

            bars.forEach { bar ->
                val params = bar.layoutParams
                params.height = dpToPx(this, 8)
                bar.layoutParams = params
            }

            days.take(7).forEachIndexed { index, usage ->
                val usedDist = (usage.maxLevel - usage.minLevel).coerceAtLeast(0.0)
                val normalized = (usedDist / totalDistRange).coerceIn(0.0, 1.0)
                val barHeight = (maxBarHeight * normalized).toInt().coerceAtLeast(dpToPx(this, 8))

                val barIndex = 6 - index
                if (barIndex in bars.indices) {
                    val params = bars[barIndex].layoutParams
                    params.height = barHeight
                    bars[barIndex].layoutParams = params
                }
            }
        }

        scheduleBackgroundWorker()

        buttonCheck.setOnClickListener {
            it.animate().rotationBy(360f).setDuration(500).start()
            db.collection("sensorCommands").document("esp32_01").update("refresh", true)
        }

        analyticsButton.setOnClickListener {
            applyClickAnimation(it) {
                startActivity(Intent(this, Analytics::class.java))
            }
        }

        settingsButton.setOnClickListener {
            applyClickAnimation(it) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        checkUserPersistentData()
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
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
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
                getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE).edit()
                    .putString("user_name", name).apply()
                userNameTextView.text = name
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun updateThemeIcon() {
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
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
}
