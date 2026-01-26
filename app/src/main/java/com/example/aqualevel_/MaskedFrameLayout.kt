package com.example.aqualevel_

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import android.widget.FrameLayout

class MaskedFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val maskPath = Path()

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()

        maskPath.reset()

        val w = width.toFloat()
        val h = height.toFloat()
        val r = w * 0.25f   // curve strength (tweak if needed)

        // Tanker inner shape (rounded top + bottom)
        maskPath.moveTo(0f, r)
        maskPath.quadTo(0f, 0f, r, 0f)
        maskPath.lineTo(w - r, 0f)
        maskPath.quadTo(w, 0f, w, r)
        maskPath.lineTo(w, h - r)
        maskPath.quadTo(w, h, w - r, h)
        maskPath.lineTo(r, h)
        maskPath.quadTo(0f, h, 0f, h - r)
        maskPath.close()

        canvas.clipPath(maskPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }
}