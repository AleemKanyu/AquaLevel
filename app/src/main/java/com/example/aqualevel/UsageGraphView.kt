package com.example.aqualevel

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class UsageGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A1C9F1")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A1C9F1")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private var dataPoints: List<Float?> = emptyList()

    fun setData(points: List<Float?>) {
        dataPoints = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val paddingLeft = 80f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 60f
        
        val graphW = w - paddingLeft - paddingRight
        val graphH = h - paddingTop - paddingBottom

        // Use 24 points for X axis scaling regardless of dataPoints size if we want 24h
        val totalHours = 24
        val stepX = graphW / (totalHours - 1).coerceAtLeast(1)

        val maxVal = dataPoints.filterNotNull().maxOrNull()?.coerceAtLeast(1f) ?: 1f

        // Draw Y-axis labels and grid lines
        val ySteps = 5
        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..ySteps) {
            val yVal = (maxVal / ySteps) * i
            val y = h - paddingBottom - (yVal / maxVal) * graphH
            canvas.drawText(yVal.toInt().toString(), paddingLeft - 10f, y + 8f, textPaint)
            if (i > 0) {
                canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint)
            }
        }

        // Draw path
        val path = Path()
        var firstPoint = true
        dataPoints.forEachIndexed { i, val_ ->
            if (i >= totalHours) return@forEachIndexed
            val x = paddingLeft + i * stepX
            
            if (val_ != null) {
                val y = h - paddingBottom - (val_ / maxVal) * graphH
                if (firstPoint) {
                    path.moveTo(x, y)
                    firstPoint = false
                } else {
                    path.lineTo(x, y)
                }
                // Draw small dot for each hour with data
                canvas.drawCircle(x, y, 6f, dotPaint)
            }

            // Draw X-axis labels (dots for numbers to avoid jumbling)
            textPaint.textAlign = Paint.Align.CENTER
            if (i % 2 == 0) {
                canvas.drawText(i.toString(), x, h - 20f, textPaint)
            } else {
                canvas.drawCircle(x, h - 30f, 2f, textPaint)
            }
        }
        canvas.drawPath(path, paint)
        
        // Label for axis
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("L", 10f, paddingTop, textPaint)
        canvas.drawText("h", w - 30f, h - 20f, textPaint)
    }
}
