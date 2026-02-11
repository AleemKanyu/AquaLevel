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
                
                // Offset top by -radius to flatten top corners
                // setRoundRect(left, top, right, bottom, radius)
                outline.setRoundRect(0, (-radius).toInt(), w, h, radius)
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        wavePath.reset()
        
        val angularFrequency = 2f * Math.PI / waveLength
        
        wavePath.moveTo(0f, h)
        
        var x = 0f
        val step = 10f
        while (x <= w) {
            val y = (waveAmplitude * Math.sin((angularFrequency * x) + waveOffset)).toFloat() + waveAmplitude
            if (x == 0f) {
                wavePath.lineTo(x, y)
            } else {
                wavePath.lineTo(x, y)
            }
            x += step
        }
        
        // Ensure right edge match
        val finalY = (waveAmplitude * Math.sin((angularFrequency * w) + waveOffset)).toFloat() + waveAmplitude
        wavePath.lineTo(w, finalY)

        wavePath.lineTo(w, h)
        wavePath.close()

        canvas.drawPath(wavePath, wavePaint)
    }

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
