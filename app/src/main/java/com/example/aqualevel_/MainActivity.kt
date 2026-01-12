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
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


class MainActivity : AppCompatActivity() {

    private lateinit var button: ImageView
    private lateinit var capacityValue: TextView
    private lateinit var usageValue: TextView
    private lateinit var percentage: TextView
    lateinit var waterLevel: FrameLayout
    private val level: String = "Water_Level"
    private var timing: String = "Time_Stamp"
    private lateinit var listner: ListenerRegistration

    var waterReading = mutableMapOf<String, Any>()
    override fun onStart() {
        super.onStart()
       listner= docRef.addSnapshotListener(this) {document,error ->
            error?.let{
                return@addSnapshotListener
            }
            document?.let{
                docRef.get()
                    .addOnSuccessListener { it ->
                        if (it.exists()) {
                            val level = it.getString(level)
                            capacityValue.text = "${level?.toInt()} Litres"
                        }else{
                            Toast.makeText(this, "Failed to retrieve data", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to retrieve data", Toast.LENGTH_SHORT).show()
                    }
            }

        }
    }

//    override fun onStop() {
//        super.onStop()                 we use this keyword inside the addSnapshotListener function
//        listner.remove()
//    }
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val docRef = db.collection("Hello").document("Second_Reading")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

        }
        button = findViewById(R.id.buttonCheck)
        capacityValue = findViewById(R.id.capacityValue)
        usageValue = findViewById(R.id.usageValue)
        percentage = findViewById(R.id.percentage)
        waterLevel = findViewById(R.id.waterLevel)



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



        button.setOnClickListener {
            val value = checkLevel()
            val per = (value / 2000) * 100

            percentage.text = "${per.toInt()}%"
            getData()
            val params = waterLevel.layoutParams
            val height = (320 * per) / 100
            params.height = dpToPx(this, height.toInt())
            waterLevel.layoutParams = params

            updateWaterCorners(per)

            waterReading.put(level, "1000")
            waterReading.put(timing, "11:00AM")


        }
    }

    private fun sendData() {
        docRef.set(waterReading).addOnSuccessListener {
            Toast.makeText(this, "Successfully uploaded to database", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to do stuff", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getData() {
        docRef.get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val level = documentSnapshot.getString(level)
                    capacityValue.text = "${level?.toInt()} Litres"
                }else{
                    Toast.makeText(this, "Failed to retrieve data", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to retrieve data", Toast.LENGTH_SHORT).show()
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
