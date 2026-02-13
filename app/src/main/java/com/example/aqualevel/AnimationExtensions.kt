package com.example.aqualevel

import android.animation.ValueAnimator
import android.widget.TextView

/**
 * Animates a TextView's text to count up from a start value to an end value.
 *
 * @param from The starting number (default 0).
 * @param to The target number.
 * @param duration The duration of the animation in milliseconds.
 * @param suffix An optional string to append to the number (e.g. "%" or " L").
 */
fun TextView.animateCountUp(to: Int, from: Int = 0, duration: Long = 1000, suffix: String = "") {
    val animator = ValueAnimator.ofInt(from, to)
    animator.duration = duration
    animator.addUpdateListener { animation ->
        this.text = "${animation.animatedValue}$suffix"
    }
    animator.start()
}

/**
 * Animates a TextView's text to count up from a start value to an end value (Double version).
 */
fun TextView.animateCountUpDouble(to: Double, from: Double = 0.0, duration: Long = 1000, suffix: String = "") {
    val animator = ValueAnimator.ofFloat(from.toFloat(), to.toFloat())
    animator.duration = duration
    animator.addUpdateListener { animation ->
        val value = animation.animatedValue as Float
        this.text = "${value.toInt()}$suffix"
    }
    animator.start()
}

/**
 * Animates a View entry (Slide down and Fade In).
 */
fun android.view.View.animateEntry(duration: Long = 800) {
    this.alpha = 0f
    this.translationY = -50f
    this.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(duration)
        .setInterpolator(android.view.animation.DecelerateInterpolator())
        .start()
}
