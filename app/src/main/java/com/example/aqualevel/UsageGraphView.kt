package com.example.aqualevel

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * A custom view that renders a cubic Bézier curve graph for water usage data.
 * Displays volume (Liters) on the Y-axis and time (Hours) on the X-axis.
 */
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
        color = ContextCompat.getColor(context, R.color.border_color) 
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private var dataPoints: List<Float?> = emptyList()
    private var currentHour: Int = -1  // The current hour of day (0-23), used to cap display

    // Animation properties
    private val pathMeasure = PathMeasure()
    private val drawnPath = Path()
    private var animationProgress = 0f
    private var animator: android.animation.ValueAnimator? = null

    /**
     * Sets the hourly data points for the graph and triggers a redraw with animation.
     * @param points A list of values representing usage in Litres for hours 0..currentHourVal.
     * @param currentHourVal The current hour of day so the graph knows which hour is live.
     */
    fun setData(points: List<Float?>, currentHourVal: Int = -1) {
        dataPoints = points
        currentHour = currentHourVal
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val startColor = ContextCompat.getColor(context, R.color.duo_blue)
        // Create a gradient for the fill below the line
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(adjustAlpha(startColor, 0.4f), adjustAlpha(startColor, 0.05f)),
            null,
            Shader.TileMode.CLAMP
        )
    }

    /**
     * Draws the graph components: grid lines, labels, Bézier path, and data points.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val paddingLeft = 100f
        val paddingRight = 50f
        val paddingTop = 60f
        val paddingBottom = 95f
        
        val graphW = w - paddingLeft - paddingRight
        val graphH = h - paddingTop - paddingBottom

        // Y-axis labels and grid lines
        val ySteps = 4
        textPaint.textAlign = Paint.Align.RIGHT
        
        val maxVal = if (dataPoints.isEmpty()) 10f else (dataPoints.filterNotNull().maxOrNull()?.coerceAtLeast(10f) ?: 10f)

        for (i in 0..ySteps) {
            val yVal = (maxVal / ySteps) * i
            val y = h - paddingBottom - (yVal / maxVal) * graphH
            canvas.drawText(yVal.toInt().toString(), paddingLeft - 20f, y + 8f, textPaint)
            
            // Draw horizontal grid line
            canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint)
        }
        
        // Axis Labels
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Litres", 20f, paddingTop - 20f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Hours", w - 20f, h - 8f, textPaint)

        if (dataPoints.isEmpty()) return

        val totalSlots = dataPoints.size.coerceAtLeast(2)  // number of hours to display
        val stepX = graphW / (totalSlots - 1).coerceAtLeast(1)

        val path = Path()
        val fillPath = Path()

        val points = mutableListOf<PointF>()
        dataPoints.forEachIndexed { i, val_ ->
            val x = paddingLeft + i * stepX
            val v = val_ ?: 0f
            val y = h - paddingBottom - (v / maxVal) * graphH
            points.add(PointF(x, y))
        }

        if (points.isEmpty()) return

        // Initialize Paths
        fillPath.moveTo(paddingLeft, h - paddingBottom)
        path.moveTo(points[0].x, points[0].y)
        fillPath.lineTo(points[0].x, points[0].y)

        // Calculate cubic Bézier segments
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            
            val cp1X = p1.x + (p2.x - p1.x) * 0.5f
            val cp1Y = p1.y
            val cp2X = p1.x + (p2.x - p1.x) * 0.5f 
            val cp2Y = p2.y
            
            path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
            fillPath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
        }
        
        // Finalize fill path (only for full path logic, but let's animate fill too if possible, or just fade it)
        // For simplicity, let's just animate the stroke line for now to keep it clean.
        // Actually, animating fill is tricky. Let's just draw the full fill with alpha animation?
        // Or keep fill static. Let's try to animate the line path.

        pathMeasure.setPath(path, false)
        drawnPath.reset()
        pathMeasure.getSegment(0f, pathMeasure.length * animationProgress, drawnPath, true)

        // Draw fill with alpha based on progress
        fillPaint.alpha = (255 * animationProgress).toInt().coerceIn(0, 255)
        fillPath.lineTo(points.last().x, h - paddingBottom)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(drawnPath, linePaint)

        // Draw X-axis hour labels and highlight data points
        textPaint.textAlign = Paint.Align.CENTER
        val labelStep = if (totalSlots <= 6) 1 else if (totalSlots <= 12) 2 else 3
        points.forEachIndexed { i, p ->
            val originalVal = dataPoints.getOrNull(i)
            // Hour label: show every labelStep hours
            if (i % labelStep == 0) {
                val hourLabel = if (currentHour >= 0) String.format("%02d", i) else String.format("%02d", i)
                canvas.drawText(hourLabel, p.x, h - 45f, textPaint)
            }
            if (originalVal != null && originalVal > 0) {
                val pointProgress = if (points.size <= 1) 1f else i.toFloat() / (points.size - 1)
                if (animationProgress >= pointProgress) {
                    canvas.drawCircle(p.x, p.y, 8f, dotPaint)
                    canvas.drawCircle(p.x, p.y, 8f, dotStrokePaint)
                }
            }
            // Highlight the current (last) hour with a larger glowing dot
            if (i == points.size - 1) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = adjustAlpha(ContextCompat.getColor(context, R.color.duo_blue), 0.3f)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(p.x, p.y, 18f, glowPaint)
                canvas.drawCircle(p.x, p.y, 10f, dotPaint)
                canvas.drawCircle(p.x, p.y, 10f, dotStrokePaint)
            }
        }
    }
    
    /**
     * Adjusts the alpha transparency of a given color.
     */
    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}
