package com.aqualevel.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import kotlin.math.*

/**
 * A custom ImageView that renders a gyroscope-reactive water splash inside the bounds of its drawable icon content.
 * Perfect for the Home button water-drop splash effect.
 */
class WaterDropIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), SensorEventListener {

    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wavePath = Path()
    private val clipPath = Path()
    private val iconBounds = RectF()
    private var baseColor = ContextCompat.getColor(context, R.color.duo_blue)

    // Masking
    private var maskBitmap: Bitmap? = null
    private var maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    // --- Physics (scaled down from WaveView) ---
    private val NUM_COLUMNS = 20
    private val columns = FloatArray(NUM_COLUMNS)
    private val velocity = FloatArray(NUM_COLUMNS)
    private val WAVE_SPEED = 140f
    private val DAMPING = 0.95f
    private val SPREAD = 0.08f

    private var targetWaterPercentage = 0f // 0 to 1
    private var targetWaterPx = 0f

    // --- Sensor ---
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var sensorRegistered = false
    private var rawTiltX = 0f
    private val sensorSmooth = 0.5f

    // --- Animation ---
    private var animTime = 0f
    private var lastFrameTime = 0L
    private var isAnimating = false

    init {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // Start empty
        for (i in 0 until NUM_COLUMNS) {
            columns[i] = 0f
            velocity[i] = 0f
        }
    }

    /** Triggers a splash that fills the icon halfway, sloshes around, and then drains back to 0. */
    fun splash(onEnd: (() -> Unit)? = null) {
        if (!sensorRegistered) {
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                sensorRegistered = true
            }
        }
        isAnimating = true
        lastFrameTime = System.nanoTime()

        // 1. Instantly drop water in (set height to 50%)
        val h = height.toFloat()
        targetWaterPercentage = 0.5f
        targetWaterPx = h * targetWaterPercentage
        
        // Push center columns down hard to create a big splash wave
        for (i in 0 until NUM_COLUMNS) {
            columns[i] = targetWaterPx
            val distFromCenter = abs(i - NUM_COLUMNS / 2f)
            if (distFromCenter < 4) {
                velocity[i] = -80f // push down
            } else {
                velocity[i] = 40f  // push up edges
            }
        }

        // 2. Animate the base level draining back down to 0 over 1.5 seconds
        ValueAnimator.ofFloat(0.5f, 0f).apply {
            duration = 1500
            startDelay = 300 // hold the splash for a moment
            interpolator = android.view.animation.AccelerateInterpolator()
            addUpdateListener {
                val p = it.animatedValue as Float
                targetWaterPx = height.toFloat() * p
                if (p == 0f) {
                    isAnimating = false
                    if (sensorRegistered) {
                        sensorManager?.unregisterListener(this@WaterDropIconView)
                        sensorRegistered = false
                    }
                    onEnd?.invoke()
                }
            }
            start()
        }

        // Render loop
        postInvalidateOnAnimation()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            rawTiltX += (ax - rawTiltX) * sensorSmooth
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMask()
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        updateMask()
    }

    private fun updateMask() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0) return
        
        // Create a bitmap mask matching the exact shape of the icon currently displayed
        maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap!!)
        d.setBounds(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        d.draw(canvas)
    }

    private fun updatePhysics() {
        if (!isAnimating) return
        val now = System.nanoTime()
        val dt = ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.03f)
        lastFrameTime = now
        animTime += dt

        val h = height.toFloat()
        if (h <= 0) return

        // Gravity Tilt
        val clampedTilt = (-rawTiltX).coerceIn(-9.8f, 9.8f)
        val tiltNormalized = clampedTilt / 9.8f
        val transferRate = abs(tiltNormalized) * 0.4f * dt * 60f

        if (tiltNormalized > 0.05f) {
            for (i in NUM_COLUMNS - 2 downTo 0) {
                val amount = columns[i] * transferRate * tiltNormalized
                if (amount > 0f) { columns[i] -= amount; columns[i + 1] += amount }
            }
        } else if (tiltNormalized < -0.05f) {
            for (i in 1 until NUM_COLUMNS) {
                val amount = columns[i] * transferRate * abs(tiltNormalized)
                if (amount > 0f) { columns[i] -= amount; columns[i - 1] += amount }
            }
        }

        // Wave Propagate
        val subSteps = 3
        val subDt = dt / subSteps
        for (step in 0 until subSteps) {
            for (i in 0 until NUM_COLUMNS - 1) {
                val diff = columns[i + 1] - columns[i]
                velocity[i] += diff * SPREAD
                velocity[i + 1] -= diff * SPREAD
            }
            for (i in 0 until NUM_COLUMNS) {
                columns[i] += velocity[i] * subDt * WAVE_SPEED
                velocity[i] *= DAMPING
                
                // Keep water bound to bottom (0) and top (h)
                if (columns[i] < 0f) { columns[i] = 0f; velocity[i] *= -0.3f }
                if (columns[i] > h) { columns[i] = h; velocity[i] *= -0.3f }
            }
        }

        // Volume conservation
        var totalHeight = 0f
        for (i in 0 until NUM_COLUMNS) totalHeight += columns[i]
        val expectedTotal = targetWaterPx * NUM_COLUMNS
        val diff = (expectedTotal - totalHeight) / NUM_COLUMNS
        for (i in 0 until NUM_COLUMNS) {
            columns[i] = (columns[i] + diff).coerceIn(0f, h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Draw the normal tinted icon first
        super.onDraw(canvas)

        if (!isAnimating || targetWaterPx <= 0.5f) return
        val w = width.toFloat()
        val h = height.toFloat()
        updatePhysics()

        // We need to draw the water, but ONLY where the icon exists.
        // We do this by drawing to an offscreen buffer, then masking it with the icon's alpha.
        val saveCount = canvas.saveLayer(0f, 0f, w, h, null)

        val colW = w / NUM_COLUMNS
        wavePath.reset()
        wavePath.moveTo(0f, h)
        for (i in 0 until NUM_COLUMNS) {
            val x = i * colW
            val y = h - columns[i]
            // Add tiny organic ripple
            val ripple = sin((4.0 * PI * i / NUM_COLUMNS) + animTime * 10.0).toFloat() * 1.5f
            wavePath.lineTo(x, y + ripple)
        }
        wavePath.lineTo(w, h - columns[NUM_COLUMNS - 1])
        wavePath.lineTo(w, h)
        wavePath.close()

        // Draw water
        waterPaint.color = baseColor
        canvas.drawPath(wavePath, waterPaint)
        
        // Add lighter surface highlight
        waterPaint.color = adjustAlpha(lightenColor(baseColor, 0.4f), 150)
        canvas.drawPath(wavePath, waterPaint)

        // Mask out the water so it only appears inside the icon shapes
        maskBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, maskPaint)
        }

        canvas.restoreToCount(saveCount)

        // Keep rendering while animating
        if (isAnimating) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (sensorRegistered) {
            sensorManager?.unregisterListener(this)
            sensorRegistered = false
        }
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun lightenColor(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt().coerceIn(0, 255),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt().coerceIn(0, 255),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt().coerceIn(0, 255)
    )
}
