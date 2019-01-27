package com.example.madik.barcodesenimdetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.View

class ViewFinderView : View {
    private var paintBorder: Paint
    private var paintMask: Paint
    private var paintText: Paint
    private var displayMetrics: DisplayMetrics
    private lateinit var path: Path

    var viewFinderWidth: Float = 0f
    var transparent = transparency
    var left: Float = 0f
    var top: Float = 0f
    var right: Float = 0f
    var bottom: Float = 0f

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

        paintText = Paint(Paint.ANTI_ALIAS_FLAG)
        paintText.color = Color.WHITE
        paintText.style = Paint.Style.FILL
        paintText.textSize = 32f
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

        //draw text above ROI
        canvas.drawText(resources.getString(R.string.qr_code_advice), left, top - 55, paintText)
    }
}

private const val transparency = 95