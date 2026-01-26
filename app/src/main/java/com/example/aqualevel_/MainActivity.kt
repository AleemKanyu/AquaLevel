package com.example.aqualevel_

import android.animation.ObjectAnimator
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.*
import com.google.firebase.firestore.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var button: ImageView
    private lateinit var capacityValue: TextView
    private lateinit var percentage: TextView
    private lateinit var waterLevel: FrameLayout
    private lateinit var tankContainer: FrameLayout

    private var value: Double = 0.0
    private var listener: ListenerRegistration? = null

    private val db = FirebaseFirestore.getInstance()
    private val docRef = db.collection("sensorData").document("esp32_01")

    // ---------- CALIBRATION CONSTANTS ----------
    private val emptyDistance = 130.0
    private val fullDistance = 30.0
    private val tankVolume = 1000.0
    // ------------------------------------------

    private var bubbleHandler: Handler? = null
    private var bubbleRunnable: Runnable? = null

    override fun onStart() {
        super.onStart()

        listener?.remove()

        listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val distance = snapshot.getDouble("distance") ?: return@addSnapshotListener

            val clampedDistance =
                distance.coerceIn(fullDistance, emptyDistance)

            val percent =
                ((emptyDistance - clampedDistance) /
                        (emptyDistance - fullDistance)) * 100.0

            val safePercent = percent.coerceIn(0.0, 100.0)

            value = (safePercent / 100.0) * tankVolume

            capacityValue.text = "${value.toInt() * 2} litres"
            percentage.text = "${safePercent.toInt()}%"

            val params = waterLevel.layoutParams
            params.height = dpToPx(
                this,
                ((280 * safePercent) / 100).toInt()
            )
            waterLevel.layoutParams = params


        }

        startBubbleAnimation()
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

        scheduleBackgroundWorker()

        button.setOnClickListener {
            db.collection("sensorCommands")
                .document("esp32_01")
                .set(mapOf("refresh" to true))
        }
    }

    // ---------------- BUBBLE ANIMATION ----------------

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
        bubbleHandler = null
        bubbleRunnable = null
    }

    // -------------------------------------------------

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

        val maxLeft = waterLevel.width - bubbleSizePx
        val leftMargin = if (maxLeft > 0) (0..maxLeft).random() else 0

        bubble.layoutParams = FrameLayout.LayoutParams(
            bubbleSizePx,
            bubbleSizePx
        ).apply {
            gravity = Gravity.BOTTOM
            this.leftMargin = leftMargin
            bottomMargin = 10
        }

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x66FFFFFF)
        }

        bubble.background = drawable
        waterLevel.addView(bubble)

        bubble.animate()
            .translationY(-waterLevel.height.toFloat())
            .alpha(0f)
            .setDuration((3000..6000).random().toLong())
            .withEndAction {
                waterLevel.removeView(bubble)
            }
            .start()
    }
}
