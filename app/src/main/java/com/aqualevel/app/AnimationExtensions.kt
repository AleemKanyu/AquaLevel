package com.aqualevel.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.widget.TextView

/**
 * Cancels any running animation on this view if it was started by these extensions.
 */
fun View.cancelAnimation() {
    val animator = getTag(R.id.animator_tag) as? ValueAnimator
    animator?.cancel()
    setTag(R.id.animator_tag, null)
}

/**
 * Animates a TextView's text to count up from a start value to an end value.
 *
 * @param from The starting number (default 0).
 * @param to The target number.
 * @param duration The duration of the animation in milliseconds.
 * @param suffix An optional string to append to the number (e.g. "%" or " L").
 */
fun TextView.animateCountUp(to: Int, from: Int = 0, duration: Long = 1000, suffix: String = "") {
    cancelAnimation() // Cancel previous animation on this view
    
    // If values are same, just set text and return
    if (from == to) {
        text = "$to$suffix"
        return
    }

    val animator = ValueAnimator.ofInt(from, to)
    animator.duration = duration
    animator.addUpdateListener { animation ->
        // Check if view is still attached? ValueAnimator doesn't care, but we do
        this.text = "${animation.animatedValue}$suffix"
    }
    // Tag the animator so we can cancel it later
    setTag(R.id.animator_tag, animator)
    
    animator.start()
}

/**
 * Animates a TextView's text to count up from a start value to an end value (Double version).
 */
fun TextView.animateCountUpDouble(to: Double, from: Double = 0.0, duration: Long = 1000, suffix: String = "") {
    cancelAnimation()
    
    if (from == to) {
        text = "${to.toInt()}$suffix"
        return
    }

    val animator = ValueAnimator.ofFloat(from.toFloat(), to.toFloat())
    animator.duration = duration
    animator.addUpdateListener { animation ->
        val value = animation.animatedValue as Float
        this.text = "${value.toInt()}$suffix"
    }
    setTag(R.id.animator_tag, animator)
    animator.start()
}

/**
 * Animates a View entry (Slide down and Fade In).
 */
fun View.animateEntry(duration: Long = 800) {
    this.alpha = 0f
    this.translationY = -50f
    this.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(duration)
        .setInterpolator(android.view.animation.DecelerateInterpolator())
        .start()
}
