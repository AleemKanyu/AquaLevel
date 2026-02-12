package com.example.aqualevel

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

/**
 * A custom FrameLayout that renders an animated wave effect at the top.
 * Used for visual representation of the water surface.
 */
class WaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.duo_blue)
    }

    private val wavePath = Path()
    private var waveOffset = 0f
    private var waveAmplitude = 15f 
    private var waveLength = 0f  
    
    private var animator: ValueAnimator? = null

    init {
        setWillNotDraw(false) // Enable onDraw for ViewGroup
        
        this.clipToOutline = true
        this.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // Radius 60dp to match container
                val radius = 60f * context.resources.displayMetrics.density
                
                // Get width and height from view to be safe
                val w = view.width
                val h = view.height
                
                // Top radius should match the container
                outline.setRoundRect(0, 0, w, h, radius)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        waveLength = w.toFloat() 
        if (w > 0) {
            startAnimation()
        }
        // Invalidate outline when size changes
        invalidateOutline()
    }

    /**
     * Draws the animated wave path on the canvas.
     */
    private var progress: Float = 0f // 0.0 to 1.0

    /**
     * Sets the water level progress (0 to 100).
     */
    fun setWaterLevel(percent: Int) {
        this.progress = (percent / 100f).coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        wavePath.reset()
        
        val angularFrequency = 2f * Math.PI / waveLength
        val waterHeight = h * progress
        val baseLine = h - waterHeight
        
        wavePath.moveTo(0f, h)
        wavePath.lineTo(0f, baseLine)
        
        var x = 0f
        val step = 10f
        while (x <= w) {
            val y = (waveAmplitude * Math.sin((angularFrequency * x) + waveOffset)).toFloat() + baseLine + waveAmplitude
            wavePath.lineTo(x, y)
            x += step
        }
        
        // Ensure right edge match
        val finalY = (waveAmplitude * Math.sin((angularFrequency * w) + waveOffset)).toFloat() + baseLine + waveAmplitude
        wavePath.lineTo(w, finalY)

        wavePath.lineTo(w, h)
        wavePath.close()

        canvas.drawPath(wavePath, wavePaint)
    }

    /**
     * Starts the value animator that updates the wave offset for the animation.
     */
    private fun startAnimation() {
        if (animator?.isRunning == true) return
        
        animator = ValueAnimator.ofFloat(0f, 2 * Math.PI.toFloat()).apply {
            duration = 4000 
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                waveOffset = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
