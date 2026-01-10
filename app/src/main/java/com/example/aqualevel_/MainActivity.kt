package com.example.aqualevel_

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var button: ImageView
    private lateinit var capacityValue: TextView
    private lateinit var usageValue: TextView
    private lateinit var percentage: TextView
    lateinit var waterLevel: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

        }
        val tankContainer = findViewById<FrameLayout>(R.id.tankContainer)

        tankContainer.post {
            val handler = Handler(mainLooper)

            val bubbleRunnable = object : Runnable {
                override fun run() {
                    spawnBubble(this@MainActivity, waterLevel)
                    handler.postDelayed(this, (400..900).random().toLong())
                }
            }

            handler.post(bubbleRunnable)

        }

        fun updateWaterCorners(percent: Double) {

            val drawable = waterLevel.background as GradientDrawable

            // Bottom corners are ALWAYS 20dp
            val bottomRadius = dpToPx(this, 20).toFloat()

            // Top corners change with level
            val topRadius = when {
                percent < 30 -> dpToPx(this, 0).toFloat()
                percent < 80 -> dpToPx(this, 12).toFloat()
                else -> dpToPx(this, 20).toFloat()
            }

            drawable.cornerRadii = floatArrayOf(
                topRadius, topRadius,           // top-left
                topRadius, topRadius,           // top-right
                bottomRadius, bottomRadius,     // bottom-right (FIXED)
                bottomRadius, bottomRadius      // bottom-left (FIXED)
            )
        }



        button = findViewById(R.id.buttonCheck)
        capacityValue = findViewById(R.id.capacityValue)
        usageValue = findViewById(R.id.usageValue)
        percentage = findViewById(R.id.percentage)
        waterLevel = findViewById(R.id.waterLevel)





        button.setOnClickListener {
            val value = checkLevel()
            val per = (value / 2000) * 100
            capacityValue.text = "${value.toInt()} Litres"
            percentage.text = "${per.toInt()}%"

            val params = waterLevel.layoutParams
            val height = (320 * per) / 100
            params.height = dpToPx(this, height.toInt())
            waterLevel.layoutParams = params

            updateWaterCorners(per)
        }
    }


}


fun checkLevel(): Double {
    return 1700.00
}

fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}

private fun spawnBubble(
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
