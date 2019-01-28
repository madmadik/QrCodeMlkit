package com.example.madik.barcodesenimdetector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.AttrRes
import android.support.annotation.RequiresApi
import android.support.design.widget.CoordinatorLayout
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat.setTint
import android.support.v7.view.ContextThemeWrapper
import android.support.v7.widget.AppCompatImageButton
import android.support.v7.widget.AppCompatTextView
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 200
    private var cameraSource: CameraSource? = null
    private lateinit var preview: CameraSourcePreview
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var flashButton: AppCompatImageButton
    private lateinit var instructionTextView: AppCompatTextView
    private lateinit var closeButton: AppCompatImageButton

    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var viewFinderView: ViewFinderView

    private var flashEnabled: Boolean = false
    private val viewFinderWidthFraction = VIEW_FINDER_WIDTH_FRACTION

    var onCloseButtonClickListener: OnCloseButtonClickListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.CAMERA
                )
            ) {
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
        flashButton = AppCompatImageButton(this)
        instructionTextView = AppCompatTextView(this)
        closeButton = AppCompatImageButton(ContextThemeWrapper(this, R.style.LightRipple))
        preview = findViewById(R.id.camera_source_preview)
        coordinatorLayout = findViewById(R.id.coordinator_layout)

        graphicOverlay = findViewById(R.id.graphics_overlay)

        createCameraSource()
        startCameraSource()

        viewFinderView = ViewFinderView(this, displayMetrics, viewFinderWidthFraction)
        this.coordinatorLayout.addView(viewFinderView)

        this.coordinatorLayout.addView(

            closeButton.apply {
                setImageResource(R.drawable.ic_cross)
                setColorFilter(R.color.white)
                isClickable = true
                isFocusable = true
                setOnClickListener { onCloseButtonClickListener?.onCloseButtonClicked() }
                setBackgroundResource(context.attrResource(R.attr.selectableItemBackgroundBorderless))
            }, CoordinatorLayout.LayoutParams(dpToPxF(48).toInt(), dpToPxF(48).toInt()).apply {
                topMargin = dpToPxF(40).toInt()
                rightMargin = dpToPxF(20).toInt()
                gravity = Gravity.TOP or Gravity.RIGHT
            })



        this.coordinatorLayout.addView(
            instructionTextView.apply {
                text = resources.getText(R.string.help_qr_scan)
                gravity = Gravity.CENTER_HORIZONTAL
                setTextColor(resources.getColor(R.color.textColorLight))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                isAllCaps = true
            },
            CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            })

        this.coordinatorLayout.addView(
            flashButton.apply {
                setImageResource(ICON_FLASH_ON)
                setColorFilter(R.color.white)
                setBackgroundResource(R.drawable.selector_btn_transparent_round)
                setOnClickListener { toggleFlash() }
            },
            CoordinatorLayout.LayoutParams(dpToPxF(48).toInt(), dpToPxF(48).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        positionFlashButton(viewFinderView)
        positionInstructionText(viewFinderView)
    }

    private fun positionFlashButton(viewFinderView: ViewFinderView) {
        val margin = dpToPxF(20)
        flashButton.y = viewFinderView.bottom + margin
    }

    private fun positionInstructionText(viewFinderView: ViewFinderView) {
        val maxWidth = displayMetrics.widthPixels - dpToPxF(16) * 2
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(maxWidth.toInt(), View.MeasureSpec.AT_MOST)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        instructionTextView.measure(widthMeasureSpec, heightMeasureSpec)
        val height = instructionTextView.measuredHeight

        val margin = dpToPxF(50)
        instructionTextView.y = viewFinderView.top - viewFinderView.viewFinderWidth - margin - height
    }

    public override fun onResume() {
        super.onResume()
        startCameraSource()
        viewFinderView.resumeAnimation()
    }

    override fun onPause() {
        super.onPause()
        preview.stop()
        viewFinderView.pauseAnimation()
    }

    public override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
    }

    private fun createCameraSource() {
        cameraSource = CameraSource(this, graphicOverlay)
        cameraSource?.setFacing(CameraSource.CAMERA_FACING_BACK)

        cameraSource?.setMachineLearningFrameProcessor(BarcodeRecognitionProcessor())
    }

    private fun startCameraSource() {
        try {
            cameraSource?.let { preview.start(it, graphicOverlay) }
        } catch (e: IOException) {
            cameraSource?.release()
            cameraSource = null
        }
    }

    private fun toggleFlash() {
        flashEnabled = !flashEnabled
        updateFlash()
    }

    private fun updateFlash() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            if (flashEnabled) {
                cameraSource?.setFlashOn()
                flashButton.setImageResource(ICON_FLASH_OFF)
            } else {
                cameraSource?.setFlashOff()
                flashButton.setImageResource(ICON_FLASH_ON)
            }
        }
    }

    interface OnCloseButtonClickListener {
        fun onCloseButtonClicked()
    }

    private fun Context.dpToPxF(dp: Int): Float =
        (dp * (resources.displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))

    private val Context.displayMetrics: DisplayMetrics
        get() {
            val metrics = DisplayMetrics()
            val displayParams = windowManager.defaultDisplay.getMetrics(metrics)
            return metrics
        }

    fun Context.attrResource(@AttrRes attrRes: Int): Int {
        val outValue = TypedValue()
        theme.resolveAttribute(attrRes, outValue, true)
        return outValue.resourceId
    }
}

private const val ICON_FLASH_ON = R.drawable.ic_flash_on
private const val ICON_FLASH_OFF = R.drawable.ic_flash_off
private const val VIEW_FINDER_WIDTH_FRACTION = 0.625f