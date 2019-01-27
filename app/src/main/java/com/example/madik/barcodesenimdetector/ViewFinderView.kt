package com.example.madik.barcodesenimdetector

import android.content.Context
import android.graphics.*
import android.support.v4.graphics.ColorUtils
import android.util.DisplayMetrics
import android.view.View

class ViewFinderView : View {
    private val gradientPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paintBorder: Paint
    private var paintMask: Paint
    private var displayMetrics: DisplayMetrics
    private lateinit var path: Path
    private val laserColor: Int = ColorUtils.setAlphaComponent(resources.getColor(R.color.colorAccent), 200)
    private var laserAnimator: LaserAnimator? = null
    private var isLaserAnimationEnabled = true

    private var laserPosition: Int = 0
    private var laserHeight: Int = 0
    private var laserDirection = 1

    var borderRect: Rect = Rect()

    var viewFinderWidth: Float = 0f
    var transparent = transparency
    var left: Float = 0f
    var top: Float = 0f
    var right: Float = 0f
    var bottom: Float = 0f
    private var isSetup = false

    constructor(context: Context, displayMetrics: DisplayMetrics, viewFinderWidthFraction: Float) : super(context) {
        this.displayMetrics = displayMetrics
        this.viewFinderWidth = viewFinderWidthFraction * displayMetrics.widthPixels

        initBorder()
    }

    init {
        paintBorder = Paint(Paint.ANTI_ALIAS_FLAG)
        paintBorder.color = Color.WHITE
        paintBorder.strokeWidth = 2f
        paintBorder.style = Paint.Style.STROKE

        paintMask = Paint(Paint.ANTI_ALIAS_FLAG)
        paintMask.style = Paint.Style.FILL
        paintMask.alpha = transparent
    }

    private fun initBorder() {
        left = (displayMetrics.widthPixels - viewFinderWidth) / 2
        top = (displayMetrics.heightPixels - viewFinderWidth) / 2
        right = left + viewFinderWidth
        bottom = top + viewFinderWidth
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(left, top, right, bottom, paintBorder)

        //draw borders around ROI with semi-transparent
        canvas.drawRect(0f, 0f, displayMetrics.widthPixels.toFloat(), top, paintMask)
        canvas.drawRect(0f, top, left, top + viewFinderWidth, paintMask)
        canvas.drawRect(
            left + viewFinderWidth,
            top,
            displayMetrics.widthPixels.toFloat(),
            top + viewFinderWidth,
            paintMask
        )
        canvas.drawRect(
            0f,
            top + viewFinderWidth,
            displayMetrics.widthPixels.toFloat(),
            displayMetrics.heightPixels.toFloat(), paintMask
        )
    }

    private fun initAnimator() {
        val framingRect = borderRect
        laserAnimator = TwoWayLaserAnimator(framingRect)

        laserAnimator!!.addUpdateListener { animator ->
            laserPosition = animator.position
            laserHeight = animator.height
            laserDirection = animator.direction

            invalidate(borderRect)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        laserAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        laserAnimator?.stop()

        laserPosition = 0
        laserHeight = 0
        laserDirection = 1
        isSetup = false
    }

    fun pauseAnimation() {
        isLaserAnimationEnabled = false
    }

    fun resumeAnimation() {
        isLaserAnimationEnabled = true
    }

}

private const val transparency = 95