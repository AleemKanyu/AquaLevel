package com.example.aqualevel

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import kotlin.math.*

/**
 * Realistic water simulation using column-based shallow water physics.
 * Water flows freely between columns creating natural wave propagation,
 * driven by the accelerometer for gravity-responsive sloshing.
 */
class WaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), SensorEventListener {

    // --- Rendering ---
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val deepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val wavePath = Path()
    private var baseColor = ContextCompat.getColor(context, R.color.duo_blue)

    // --- Column-based water simulation ---
    private val NUM_COLUMNS = 40
    private val columns = FloatArray(NUM_COLUMNS)       // water height per column
    private val velocity = FloatArray(NUM_COLUMNS)       // vertical velocity per column
    private val flowRate = FloatArray(NUM_COLUMNS + 1)   // horizontal flow between columns

    // Physics constants
    private val WAVE_SPEED = 120f      // how fast waves propagate
    private val DAMPING = 0.985f       // velocity damping (closer to 1 = less damping)
    private val SPREAD = 0.06f         // how quickly height differences equalize
    private val GRAVITY_SCALE = 30f    // how strongly tilt pushes water
    private val MAX_VELOCITY = 250f    // velocity cap to prevent instability

    // --- Water level ---
    private var progress: Float = 0f   // 0.0 to 1.0
    private var targetWaterHeight = 0f

    // --- Animation ---
    private var animator: ValueAnimator? = null
    private var animTime = 0f

    // --- Accelerometer ---
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var sensorRegistered = false
    private var gyroUserEnabled = false
    private var rawTiltX = 0f
    private var rawTiltY = 0f
    private val sensorSmooth = 0.4f    // responsive sensor input
    
    // Shake detection
    private var prevAccelX = 0f
    private var prevAccelY = 0f
    private var prevAccelZ = 0f

    // --- Timing ---
    private var lastFrameTime = 0L

    // --- Bubbles ---
    private val bubbles = mutableListOf<Bubble>()
    private val random = java.util.Random()

    init {
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = 60f * context.resources.displayMetrics.density
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        for (i in 0 until NUM_COLUMNS) {
            columns[i] = 0f
            velocity[i] = 0f
        }
    }

    // --- Public API ---
    fun setGyroEnabled(enabled: Boolean) {
        gyroUserEnabled = enabled
        if (enabled) {
            registerSensor()
        } else {
            unregisterSensor()
            rawTiltX = 0f
            rawTiltY = 0f
        }
    }

    fun setWaterLevel(percent: Int) {
        progress = (percent / 100f).coerceIn(0f, 1f)
        targetWaterHeight = height.toFloat() * progress
        for (i in 0 until NUM_COLUMNS) {
            columns[i] = targetWaterHeight
            velocity[i] = 0f
        }
        invalidate()
    }

    fun setWaveColor(color: Int) {
        baseColor = color
        invalidate()
    }

    fun splash() {
        // Create a smooth, natural ripple spreading from center
        val center = NUM_COLUMNS / 2
        val splashWidth = NUM_COLUMNS / 3  // wider, gentler splash
        
        for (i in 0 until NUM_COLUMNS) {
            val dist = abs(i - center).toFloat()
            // Gaussian-shaped push: strong at center, fades smoothly
            val gaussian = exp(-(dist * dist) / (2f * splashWidth * splashWidth / 9f))
            // Push up in center, slight down on edges for natural displacement
            val push = if (dist < splashWidth / 2f) {
                gaussian * 40f  // gentle upward center push
            } else {
                -gaussian * 15f  // slight downward at edges
            }
            velocity[i] += push
        }
        
        // Spawn a few bubbles
        val w = width.toFloat()
        val h = height.toFloat()
        if (w > 0 && h > 0) {
            for (i in 0..5) bubbles.add(Bubble(w, h))
        }
    }

    // --- Sensor ---
    private fun registerSensor() {
        if (!sensorRegistered) {
            accelerometer?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
                sensorRegistered = true
            }
        }
    }

    private fun unregisterSensor() {
        if (sensorRegistered) {
            sensorManager?.unregisterListener(this)
            sensorRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            
            rawTiltX += (ax - rawTiltX) * sensorSmooth
            rawTiltY += (ay - rawTiltY) * sensorSmooth
            
            // Shake detection: sudden acceleration change disturbs the water
            val dx = ax - prevAccelX
            val dy = ay - prevAccelY
            val dz = az - prevAccelZ
            val shakeMagnitude = sqrt(dx * dx + dy * dy + dz * dz)
            
            if (shakeMagnitude > 8f) {
                // Disturb random columns proportional to shake intensity
                val intensity = (shakeMagnitude * 3f).coerceAtMost(60f)
                val numDisturbances = (shakeMagnitude * 0.5f).toInt().coerceIn(1, 5)
                for (j in 0 until numDisturbances) {
                    val col = random.nextInt(NUM_COLUMNS)
                    velocity[col] += (random.nextFloat() - 0.5f) * intensity
                }
            }
            
            prevAccelX = ax
            prevAccelY = ay
            prevAccelZ = az
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- Lifecycle ---
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (gyroUserEnabled) registerSensor()
        lastFrameTime = System.nanoTime()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        unregisterSensor()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        targetWaterHeight = h.toFloat() * progress
        for (i in 0 until NUM_COLUMNS) {
            columns[i] = targetWaterHeight
            velocity[i] = 0f
        }
        if (w > 0) startAnimation()
        invalidateOutline()
    }

    // ========================================
    //  SHALLOW WATER PHYSICS (STABLE)
    // ========================================
    private fun updatePhysics() {
        val now = System.nanoTime()
        val dt = ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.025f)
        lastFrameTime = now

        val h = height.toFloat()
        if (h <= 0 || progress <= 0f) return
        targetWaterHeight = h * progress

        // -- Step 1: Apply gravity — actually transfer water mass --
        if (gyroUserEnabled) {
            val clampedTilt = (-rawTiltX).coerceIn(-9.8f, 9.8f)
            val tiltNormalized = clampedTilt / 9.8f  // -1 to +1
            
            // Transfer water between adjacent columns in the tilt direction
            // This moves actual mass, not just velocity
            val transferRate = abs(tiltNormalized) * 0.15f * dt * 60f  // fraction to transfer per frame
            
            if (tiltNormalized > 0.05f) {
                // Tilt right: move water from left columns to right columns
                for (i in NUM_COLUMNS - 2 downTo 0) {
                    val amount = columns[i] * transferRate * tiltNormalized
                    if (amount > 0f && columns[i] > 0f) {
                        columns[i] -= amount
                        columns[i + 1] += amount
                    }
                }
            } else if (tiltNormalized < -0.05f) {
                // Tilt left: move water from right columns to left columns
                for (i in 1 until NUM_COLUMNS) {
                    val amount = columns[i] * transferRate * abs(tiltNormalized)
                    if (amount > 0f && columns[i] > 0f) {
                        columns[i] -= amount
                        columns[i - 1] += amount
                    }
                }
            }
        }

        // -- Step 2: Propagate waves with sub-stepping --
        val subSteps = 4
        val subDt = dt / subSteps
        for (step in 0 until subSteps) {
            // Flow between adjacent columns based on height difference
            for (i in 0 until NUM_COLUMNS - 1) {
                val heightDiff = columns[i + 1] - columns[i]
                velocity[i] += heightDiff * SPREAD
                velocity[i + 1] -= heightDiff * SPREAD
            }

            // Apply velocity and damping
            for (i in 0 until NUM_COLUMNS) {
                // Clamp velocity to prevent explosion
                velocity[i] = velocity[i].coerceIn(-MAX_VELOCITY, MAX_VELOCITY)
                columns[i] += velocity[i] * subDt * WAVE_SPEED
                velocity[i] *= DAMPING

                // Hard floor at 0, allow going up to container height
                if (columns[i] < 0f) {
                    columns[i] = 0f
                    velocity[i] = abs(velocity[i]) * 0.3f  // gentle bounce
                }
                if (columns[i] > h) {
                    columns[i] = h
                    velocity[i] = -abs(velocity[i]) * 0.3f  // gentle bounce
                }
            }
        }

        // -- Step 3: Volume conservation (additive, not multiplicative) --
        var totalHeight = 0f
        for (i in 0 until NUM_COLUMNS) totalHeight += columns[i]
        val expectedTotal = targetWaterHeight * NUM_COLUMNS
        val diff = (expectedTotal - totalHeight) / NUM_COLUMNS
        for (i in 0 until NUM_COLUMNS) {
            columns[i] = (columns[i] + diff).coerceIn(0f, h)
        }
    }

    // ========================================
    //  DRAWING
    // ========================================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        updatePhysics()

        val columnWidth = w / NUM_COLUMNS

        // --- Layer 1: Deep water (darker) ---
        drawWaterSurface(canvas, w, h, columnWidth,
            yOffset = 6f,
            waveAmp = 2f,
            waveFreq = 1.5f,
            color = adjustAlpha(darkenColor(baseColor, 0.65f), 170))

        // --- Layer 2: Main water ---
        drawWaterSurface(canvas, w, h, columnWidth,
            yOffset = 0f,
            waveAmp = 3f,
            waveFreq = 2.5f,
            color = adjustAlpha(baseColor, 230))

        // --- Layer 3: Surface highlight ---
        drawWaterSurface(canvas, w, h, columnWidth,
            yOffset = -4f,
            waveAmp = 2f,
            waveFreq = 4f,
            color = adjustAlpha(lightenColor(baseColor, 0.35f), 90))

        // --- Surface shimmer ---
        drawShimmer(canvas, w, h, columnWidth)

        // --- Bubbles ---
        var minSurface = h
        for (i in 0 until NUM_COLUMNS) {
            val sy = h - columns[i]
            if (sy < minSurface) minSurface = sy
        }
        drawBubbles(canvas, w, h, minSurface)
    }

    private fun drawWaterSurface(
        canvas: Canvas, w: Float, h: Float, colW: Float,
        yOffset: Float, waveAmp: Float, waveFreq: Float, color: Int
    ) {
        wavePath.reset()
        waterPaint.color = color

        wavePath.moveTo(0f, h)  // bottom-left

        for (i in 0 until NUM_COLUMNS) {
            val x = i * colW
            val surfaceY = h - columns[i] + yOffset
            // Add small animated ripple for organic look
            val ripple = sin(
                (waveFreq * 2.0 * PI * i / NUM_COLUMNS) + animTime.toDouble()
            ).toFloat() * waveAmp
            wavePath.lineTo(x, surfaceY + ripple)
        }
        // Last column edge
        val lastSurface = h - columns[NUM_COLUMNS - 1] + yOffset
        val lastRipple = sin(
            (waveFreq * 2.0 * PI) + animTime.toDouble()
        ).toFloat() * waveAmp
        wavePath.lineTo(w, lastSurface + lastRipple)

        wavePath.lineTo(w, h)  // bottom-right
        wavePath.close()

        canvas.drawPath(wavePath, waterPaint)
    }

    private fun drawShimmer(canvas: Canvas, w: Float, h: Float, colW: Float) {
        shimmerPaint.alpha = 25
        shimmerPaint.strokeWidth = 2.5f
        shimmerPaint.style = Paint.Style.STROKE

        val path = Path()
        var started = false
        for (i in 0 until NUM_COLUMNS) {
            val x = i * colW
            val surfaceY = h - columns[i]
            val shimmer = sin((3.0 * 2.0 * PI * i / NUM_COLUMNS) + animTime * 1.3).toFloat() * 2.5f
            if (!started) {
                path.moveTo(x, surfaceY + shimmer)
                started = true
            } else {
                path.lineTo(x, surfaceY + shimmer)
            }
        }
        canvas.drawPath(path, shimmerPaint)
        shimmerPaint.style = Paint.Style.FILL
    }

    // --- Bubbles ---
    private fun drawBubbles(canvas: Canvas, w: Float, h: Float, waterSurfaceY: Float) {
        val motionIntensity = velocity.map { abs(it) }.average().toFloat()
        val spawnChance = if (motionIntensity > 2f) 5 else 35
        if (random.nextInt(spawnChance) == 0 && progress > 0.1f) {
            bubbles.add(Bubble(w, h))
        }

        val iterator = bubbles.iterator()
        while (iterator.hasNext()) {
            val bubble = iterator.next()
            bubble.update(rawTiltX * 0.4f)
            if (bubble.y < waterSurfaceY + bubble.size || bubble.alpha <= 0) {
                iterator.remove()
            } else {
                bubble.draw(canvas)
            }
        }
        if (bubbles.isNotEmpty()) invalidate()
    }

    // --- Animation ---
    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animTime = (animTime + 0.025f) % (2 * PI).toFloat()
                invalidate()
            }
            start()
        }
    }

    // --- Color utilities ---
    private fun adjustAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun darkenColor(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )

    private fun lightenColor(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt().coerceIn(0, 255),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt().coerceIn(0, 255),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt().coerceIn(0, 255)
    )

    // --- Bubble ---
    private inner class Bubble(screenWidth: Float, screenHeight: Float) {
        var x = random.nextFloat() * screenWidth
        var y = screenHeight - random.nextFloat() * screenHeight * progress * 0.5f
        var speed = 1.2f + random.nextFloat() * 2f
        var size = 2f + random.nextFloat() * 7f
        var alpha = 50 + random.nextInt(100)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        fun update(drift: Float) {
            y -= speed
            x += drift + (random.nextFloat() - 0.5f) * 1.5f
            size += 0.015f
            alpha = (alpha - 1).coerceAtLeast(0)
        }

        fun draw(canvas: Canvas) {
            if (alpha <= 0) return
            paint.alpha = alpha
            canvas.drawCircle(x, y, size, paint)
            paint.alpha = alpha / 3
            canvas.drawCircle(x - size * 0.2f, y - size * 0.2f, size * 0.35f, paint)
        }
    }
}
