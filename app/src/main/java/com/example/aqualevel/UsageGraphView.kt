package com.example.aqualevel

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class UsageGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.duo_blue)
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.duo_blue)
        style = Paint.Style.FILL
    }

    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E5E5E5") // Light gray
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private var dataPoints: List<Float?> = emptyList()

    fun setData(points: List<Float?>) {
        dataPoints = points
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val startColor = ContextCompat.getColor(context, R.color.duo_blue)
        // Create a gradient for the fill
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(adjustAlpha(startColor, 0.4f), adjustAlpha(startColor, 0.05f)),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val paddingLeft = 100f
        val paddingRight = 50f
        val paddingTop = 60f
        val paddingBottom = 80f
        
        val graphW = w - paddingLeft - paddingRight
        val graphH = h - paddingTop - paddingBottom

        // Draw basic layout even if empty
        // Y-axis labels and grid lines
        val ySteps = 4
        textPaint.textAlign = Paint.Align.RIGHT
        
        // Always assuming some max value for empty state 
        val maxVal = if (dataPoints.isEmpty()) 10f else (dataPoints.filterNotNull().maxOrNull()?.coerceAtLeast(10f) ?: 10f)

        for (i in 0..ySteps) {
            val yVal = (maxVal / ySteps) * i
            val y = h - paddingBottom - (yVal / maxVal) * graphH
            canvas.drawText(yVal.toInt().toString(), paddingLeft - 20f, y + 8f, textPaint)
            
            // Grid line
            canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint)
        }
        
        // Axis Labels
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Litres", 20f, paddingTop - 20f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Hours", w - 20f, h - 20f, textPaint)

        if (dataPoints.isEmpty()) return

        val totalHours = 24
        val stepX = graphW / (totalHours - 1).coerceAtLeast(1)

        val path = Path()
        val fillPath = Path()
        
        var firstPoint = true
        var lastX = 0f
        var lastY = 0f

        // Calculate points first to make curve calculation easier
        val points = mutableListOf<PointF>()
        dataPoints.forEachIndexed { i, val_ ->
             if (i >= totalHours) return@forEachIndexed
             val x = paddingLeft + i * stepX
             // Treat null as 0 for continuity or skip? Treating as 0 for fill consistency
             val v = val_ ?: 0f
             val y = h - paddingBottom - (v / maxVal) * graphH
             points.add(PointF(x, y))
        }

        if (points.isEmpty()) return

        // Fill Path
        fillPath.moveTo(paddingLeft, h - paddingBottom) // Start at bottom-left
        
        // Stroke Path
        path.moveTo(points[0].x, points[0].y)
        fillPath.lineTo(points[0].x, points[0].y)

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            
            val cp1X = p1.x + (p2.x - p1.x) * 0.5f
            val cp1Y = p1.y
            val cp2X = p1.x + (p2.x - p1.x) * 0.5f // Control point approach: horizontal Bezier 
            val cp2Y = p2.y
            
            // Using cubicTo with control points at halfway X but keeping Y same (horizontal inflection)
            // effective for time series
            path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
            fillPath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
        }
        
        // Close fill path
        fillPath.lineTo(points.last().x, h - paddingBottom)
        fillPath.close()

        // Draw fill first
        canvas.drawPath(fillPath, fillPaint)
        // Draw line
        canvas.drawPath(path, linePaint)

        // Draw dots and X-axis labels
        textPaint.textAlign = Paint.Align.CENTER
        points.forEachIndexed { i, p ->
            val originalVal = dataPoints.getOrNull(i)
            
            // Draw X label
            if (i % 4 == 0) { // Every 4 hours
                 canvas.drawText(String.format("%02d", i), p.x, h - 30f, textPaint)
            }
            
            // Draw dot if there's real data > 0
            if (originalVal != null && originalVal > 0) {
                canvas.drawCircle(p.x, p.y, 8f, dotPaint)
                canvas.drawCircle(p.x, p.y, 8f, dotStrokePaint)
            }
        }
    }
    
    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}
