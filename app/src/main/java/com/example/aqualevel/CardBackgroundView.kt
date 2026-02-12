package com.example.aqualevel

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat

class CardBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Role {
        TANK_LEVEL, DAILY_USAGE, HOURLY_AVG, EST_TIME
    }

    private var role: Role = Role.TANK_LEVEL
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    
    private var animationValue = 0f
    private var animator: ValueAnimator? = null
    
    // Colors
    private var primaryColor: Int = Color.BLACK
    private var secondaryColor: Int = Color.BLACK

    fun setRole(role: Role, primaryColor: Int, secondaryColor: Int) {
        this.role = role
        this.primaryColor = primaryColor
        this.secondaryColor = secondaryColor
        startAnimation()
        invalidate()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (role) {
                Role.TANK_LEVEL -> 5000L
                Role.DAILY_USAGE -> 3000L
                Role.HOURLY_AVG -> 2000L
                Role.EST_TIME -> 4000L
            }
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animationValue = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        when (role) {
            Role.TANK_LEVEL -> drawWave(canvas, w, h)
            Role.DAILY_USAGE -> drawDroplets(canvas, w, h)
            Role.HOURLY_AVG -> drawPulses(canvas, w, h)
            Role.EST_TIME -> drawGlow(canvas, w, h)
        }
    }

    private fun drawWave(canvas: Canvas, w: Float, h: Float) {
        paint.color = secondaryColor
        paint.alpha = 40 // Very subtle
        path.reset()
        
        val baseLine = h * 0.7f
        val amplitude = 15f
        val frequency = 2 * Math.PI / w
        val offset = animationValue * 2 * Math.PI.toFloat()

        path.moveTo(0f, h)
        path.lineTo(0f, baseLine)
        
        var x = 0f
        while (x <= w) {
            val y = (amplitude * Math.sin(frequency * x + offset)).toFloat() + baseLine
            path.lineTo(x, y)
            x += 10f
        }
        
        path.lineTo(w, h)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawDroplets(canvas: Canvas, w: Float, h: Float) {
        paint.color = secondaryColor
        paint.alpha = 30
        
        // Draw 3 moving droplets
        for (i in 0 until 3) {
            val dropletProgress = (animationValue + i * 0.33f) % 1.0f
            val cx = w * (0.2f + i * 0.3f)
            val cy = h * (1.1f - dropletProgress * 1.2f)
            val radius = 10f + 5f * Math.sin(dropletProgress * Math.PI).toFloat()
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawPulses(canvas: Canvas, w: Float, h: Float) {
        paint.color = secondaryColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        
        val maxRadius = Math.max(w, h) * 0.6f
        for (i in 0 until 2) {
            val pulseProgress = (animationValue + i * 0.5f) % 1.0f
            paint.alpha = (40 * (1.0f - pulseProgress)).toInt()
            canvas.drawCircle(w * 0.8f, h * 0.5f, maxRadius * pulseProgress, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawGlow(canvas: Canvas, w: Float, h: Float) {
        paint.color = secondaryColor
        val glowProgress = Math.abs(Math.sin(animationValue * Math.PI)).toFloat()
        paint.alpha = (10 + 20 * glowProgress).toInt()
        
        val gradient = RadialGradient(
            w * 0.5f, h * 0.5f, w * 0.8f,
            intArrayOf(secondaryColor, Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
