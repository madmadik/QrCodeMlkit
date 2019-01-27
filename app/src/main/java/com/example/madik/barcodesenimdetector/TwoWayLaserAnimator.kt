package com.example.madik.barcodesenimdetector

import android.animation.Keyframe
import android.animation.PropertyValuesHolder
import android.graphics.Rect

private const val FRACTION_1 = 0.25f
private const val FRACTION_2 = 0.5f
private const val FRACTION_3 = 0.75f

class TwoWayLaserAnimator(framingRect: Rect) : LaserAnimator(framingRect) {

    override val direction: Int
        get() = if (animator.animatedFraction < 0.5f) 1 else -1

    override val animationDuration: Long
        get() = 3000

    override val positionValues: PropertyValuesHolder
        get() {
            val k1 = Keyframe.ofInt(0f, framingRect.top)
            val k2 = Keyframe.ofInt(FRACTION_1, framingRect.bottom)
            val k3 = Keyframe.ofInt(FRACTION_2, framingRect.bottom)
            val k4 = Keyframe.ofInt(FRACTION_3, framingRect.top)
            val k5 = Keyframe.ofInt(1f, framingRect.top)

            return PropertyValuesHolder.ofKeyframe(PROP_POSITION, k1, k2, k3, k4, k5)
        }

    override val heightValues: PropertyValuesHolder
        get() {
            val maxHeight = framingRect.height() / 3

            val k1 = Keyframe.ofInt(0f, 0)
            val k2 = Keyframe.ofInt(FRACTION_1, maxHeight)
            val k3 = Keyframe.ofInt(FRACTION_2, 0)
            val k4 = Keyframe.ofInt(FRACTION_3, maxHeight)
            val k5 = Keyframe.ofInt(1f, 0)

            return PropertyValuesHolder.ofKeyframe(PROP_HEIGHT, k1, k2, k3, k4, k5)
        }
}
