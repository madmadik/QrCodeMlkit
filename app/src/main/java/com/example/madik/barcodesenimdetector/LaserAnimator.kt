package com.example.madik.barcodesenimdetector

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.animation.LinearInterpolator

const val PROP_POSITION = "position"
const val PROP_HEIGHT = "height"

abstract class LaserAnimator(val framingRect: Rect) {

    protected val animator: ValueAnimator = ValueAnimator()

    val position: Int
        get() = animator.getAnimatedValue(PROP_POSITION) as Int

    val height: Int
        get() = animator.getAnimatedValue(PROP_HEIGHT) as Int

    abstract val direction: Int

    protected abstract val animationDuration: Long
    protected abstract val positionValues: PropertyValuesHolder
    protected abstract val heightValues: PropertyValuesHolder

    init {
        animator.setValues(positionValues, heightValues)
        animator.interpolator = LinearInterpolator()
        animator.duration = animationDuration
        animator.repeatCount = ValueAnimator.INFINITE
    }

    fun addUpdateListener(listener: (LaserAnimator) -> Unit) {
        animator.addUpdateListener { listener(this) }
    }

    fun start() {
        animator.start()
    }

    fun stop() {
        animator.end()
    }
}
