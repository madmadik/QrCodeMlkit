package com.example.madik.barcodesenimdetector

import android.content.Context
import android.graphics.*
import android.support.v4.graphics.ColorUtils
import android.util.DisplayMetrics
import android.view.View

class ViewFinderView : View {
    private var paintBorder: Paint
    private var paintMask: Paint
    private var mBorderPaint: Paint
    private var mLaserPaint: Paint
    private val gradientPaint: Paint

    private var displayMetrics: DisplayMetrics
    private val laserColor: Int = ColorUtils.setAlphaComponent(resources.getColor(R.color.colorAccent), 200)
    private var laserAnimator: LaserAnimator? = null
    private var isLaserAnimationEnabled = true

    private var laserPosition = 0
    private var laserHeight = 0
    private var laserDirection = 1
    private var mBorderLineLength = 0


    private var borderRect: Rect = Rect()

    var viewFinderWidth = 0f
    var transparent = transparency
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f

    constructor(context: Context, displayMetrics: DisplayMetrics, viewFinderWidthFraction: Float) : super(context) {
        this.displayMetrics = displayMetrics
        this.viewFinderWidth = viewFinderWidthFraction * displayMetrics.widthPixels

        initBorder()
    }

    init {
        paintBorder = Paint(Paint.ANTI_ALIAS_FLAG)
        paintBorder.color = Color.WHITE
        paintBorder.strokeWidth = strokeWidth
        paintBorder.style = Paint.Style.STROKE

        paintMask = Paint(Paint.ANTI_ALIAS_FLAG)
        paintMask.style = Paint.Style.FILL
        paintMask.alpha = transparent

        mBorderPaint = Paint()
        mBorderPaint.color = resources.getColor(R.color.white)
        mBorderPaint.style = Paint.Style.STROKE
        mBorderPaint.strokeWidth = borderWidth
        mBorderPaint.isAntiAlias = true

        mLaserPaint = Paint()
        mLaserPaint.color = resources.getColor(R.color.colorAccent)
        mLaserPaint.style = Paint.Style.FILL

        gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        mBorderLineLength = borderLineLength

    }

    private fun initBorder() {
        left = (displayMetrics.widthPixels - viewFinderWidth) / 2
        top = (displayMetrics.heightPixels - viewFinderWidth) / 2
        right = left + viewFinderWidth
        bottom = top + viewFinderWidth

        borderRect.left = left.toInt()
        borderRect.top = top.toInt()
        borderRect.right = right.toInt()
        borderRect.bottom = bottom.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
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

        drawLaser(canvas)
        drawViewFinderBorder(canvas)
    }

    fun drawViewFinderBorder(canvas: Canvas) {
        val framingRect = this.borderRect
        val path = Path()

        path.moveTo(framingRect.left.toFloat(), (framingRect.top + this.mBorderLineLength).toFloat())
        path.lineTo(framingRect.left.toFloat(), framingRect.top.toFloat())
        path.lineTo((framingRect.left + this.mBorderLineLength).toFloat(), framingRect.top.toFloat())
        canvas.drawPath(path, this.mBorderPaint)
        path.moveTo(framingRect.right.toFloat(), (framingRect.top + this.mBorderLineLength).toFloat())
        path.lineTo(framingRect.right.toFloat(), framingRect.top.toFloat())
        path.lineTo((framingRect.right - this.mBorderLineLength).toFloat(), framingRect.top.toFloat())
        canvas.drawPath(path, this.mBorderPaint)
        path.moveTo(framingRect.right.toFloat(), (framingRect.bottom - this.mBorderLineLength).toFloat())
        path.lineTo(framingRect.right.toFloat(), framingRect.bottom.toFloat())
        path.lineTo((framingRect.right - this.mBorderLineLength).toFloat(), framingRect.bottom.toFloat())
        canvas.drawPath(path, this.mBorderPaint)
        path.moveTo(framingRect.left.toFloat(), (framingRect.bottom - this.mBorderLineLength).toFloat())
        path.lineTo(framingRect.left.toFloat(), framingRect.bottom.toFloat())
        path.lineTo((framingRect.left + this.mBorderLineLength).toFloat(), framingRect.bottom.toFloat())
        canvas.drawPath(path, this.mBorderPaint)
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

    fun drawLaser(canvas: Canvas) {

        if (laserAnimator == null) {
            initAnimator()
            laserAnimator!!.start()
        }

        val framingRect = borderRect

        val laserRect = Rect()
        laserRect.left = framingRect.left
        laserRect.right = framingRect.right

        laserRect.top = if (laserDirection > 0) laserPosition - laserHeight else laserPosition
        laserRect.bottom = if (laserDirection > 0) laserPosition else laserPosition + laserHeight

        val colorTop = if (laserDirection > 0) Color.TRANSPARENT else laserColor
        val colorBottom = if (laserDirection > 0) laserColor else Color.TRANSPARENT
        val shader = LinearGradient(
            0f, laserRect.top.toFloat(), 0f, laserRect.bottom.toFloat(),
            colorTop, colorBottom, Shader.TileMode.CLAMP
        )
        gradientPaint.shader = shader

        canvas.drawRect(laserRect, gradientPaint)
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
    }

    fun pauseAnimation() {
        isLaserAnimationEnabled = false
    }

    fun resumeAnimation() {
        isLaserAnimationEnabled = true
    }

}

private const val borderLineLength = 100
private const val transparency = 95
private const val borderWidth = 10f
private const val strokeWidth = 2f