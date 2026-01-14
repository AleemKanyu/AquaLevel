package com.example.aqualevel_

import android.app.*
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.*
import androidx.work.WorkManager
import com.google.firebase.firestore.*
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {

    private lateinit var button: ImageView
    private lateinit var capacityValue: TextView
    private lateinit var percentage: TextView
    private lateinit var waterLevel: FrameLayout
    private lateinit var tankContainer: FrameLayout

    private var value :Double=0.00
    private var todayKey: String=""

    private lateinit var listener: ListenerRegistration

    private val db = FirebaseFirestore.getInstance()
    private val docRef =
        db.collection("sensorData").document("esp32_01")



    private val waterReading = mutableMapOf<String, Any>()

    override fun onStart() {
        super.onStart()

        listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("Firestore", "Listen failed", error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {

                val distance = snapshot.getDouble("distance") ?: return@addSnapshotListener

                // Convert distance → litres
                value = (100 - distance) * 10

                // Update text
                capacityValue.text = "${value.toInt()} litres"

                // Update UI based on NEW value
                val percent = (value / 1000) * 100
                percentage.text = "${percent.toInt()}%"

                val params = waterLevel.layoutParams
                params.height = dpToPx(
                    this,
                    ((320 * percent) / 100).toInt()
                )
                waterLevel.layoutParams = params

                updateWaterCorners(percent)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        listener.remove()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets

        }

        button = findViewById(R.id.buttonCheck)
        capacityValue = findViewById(R.id.capacityValue)
        percentage = findViewById(R.id.percentage)
        waterLevel = findViewById(R.id.waterLevel)
        tankContainer = findViewById(R.id.tankContainer)

        startBubbleAnimation()
        scheduleBackgroundWorker()

        button.setOnClickListener {
            val percent = (value / 1000) * 100

            percentage.text = "${percent.toInt()}%"

            val params = waterLevel.layoutParams
            params.height = dpToPx(this, ((320 * percent) / 100).toInt())
            waterLevel.layoutParams = params

            updateWaterCorners(percent)

          todayKey = java.text.SimpleDateFormat(
                "yyyy-MM-dd",
                java.util.Locale.getDefault()
            ).format(java.util.Date())



        }
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "water_alerts",
                "Water Level Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Alerts when water level is low"

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendData() {

    }

    private fun updateWaterCorners(percent: Double) {
        val drawable = waterLevel.background as GradientDrawable
        val bottom = dpToPx(this, 20).toFloat()
        val top = when {
            percent < 30 -> 0f
            percent < 80 -> dpToPx(this, 12).toFloat()
            else -> dpToPx(this, 20).toFloat()
        }

        drawable.cornerRadii = floatArrayOf(
            top, top, top, top,
            bottom, bottom, bottom, bottom
        )
    }

    private fun startBubbleAnimation() {
        tankContainer.post {
            val handler = Handler(mainLooper)
            val runnable = object : Runnable {
                override fun run() {
                    spawnBubble(this@MainActivity, waterLevel)
                    handler.postDelayed(this, (400..900).random().toLong())
                }
            }
            handler.post(runnable)
        }
    }

    private fun scheduleBackgroundWorker() {
        val workRequest =
            PeriodicWorkRequestBuilder<WaterLevelWorker>(1, TimeUnit.MINUTES)
                .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "water_level_monitor",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }
}

fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}
fun spawnBubble(
    context: Context,
    waterLevel: FrameLayout
) {
    val bubbleSizeDp = (6..12).random()
    val bubbleSizePx = dpToPx(context, bubbleSizeDp)

    val bubble = View(context)
    bubble.layoutParams = FrameLayout.LayoutParams(
        bubbleSizePx,
        bubbleSizePx
    ).apply {
        gravity = Gravity.BOTTOM
        leftMargin = (20..160).random()
        bottomMargin = 10
    }

    val drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0x66FFFFFF)
    }

    bubble.background = drawable
    waterLevel.addView(bubble)

    val riseDistance = waterLevel.height

    bubble.animate()
        .translationY(-riseDistance.toFloat())
        .alpha(0f)
        .setDuration((3000..6000).random().toLong())
        .withEndAction {
            waterLevel.removeView(bubble)
        }
        .start()
}
