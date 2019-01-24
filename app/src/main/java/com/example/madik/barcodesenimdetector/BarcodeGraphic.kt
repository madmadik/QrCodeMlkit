package com.example.madik.barcodesenimdetector

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode


class BarcodeGraphic : GraphicOverlay.Graphic {
    private var mId: Int = 0

    private val COLOR_CHOICES = intArrayOf(Color.BLUE, Color.CYAN, Color.GREEN)

    private var mCurrentColorIndex = 0

    private var mRectPaint: Paint
    private var mTextPaint: Paint
    @Volatile
    private var mBarcode: FirebaseVisionBarcode? = null

    constructor(overlay: GraphicOverlay, barcode: FirebaseVisionBarcode) : super(overlay) {
        this.mBarcode = barcode
        mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.size
        val selectedColor = COLOR_CHOICES[mCurrentColorIndex]

        mRectPaint = Paint()
        mRectPaint.color = selectedColor
        mRectPaint.style = Paint.Style.STROKE
        mRectPaint.strokeWidth = 4.0f

        mTextPaint = Paint()
        mTextPaint.color = selectedColor
        mTextPaint.textSize = 36.0f
    }

    fun getId(): Int {
        return mId
    }

    fun setId(id: Int) {
        this.mId = id
    }

    fun getBarcode(): FirebaseVisionBarcode? {
        return mBarcode
    }

    fun updateItem(barcode: FirebaseVisionBarcode) {
        mBarcode = barcode
        postInvalidate()
    }

    override fun draw(canvas: Canvas) {
        val barcode = mBarcode ?: return

        val rect = RectF(barcode.boundingBox)
        rect.left = translateX(rect.left)
        rect.top = translateY(rect.top)
        rect.right = translateX(rect.right)
        rect.bottom = translateY(rect.bottom)
        canvas.drawRect(rect, mRectPaint)

        canvas.drawText(barcode.rawValue.toString(), rect.left, rect.bottom, mTextPaint)
    }
}