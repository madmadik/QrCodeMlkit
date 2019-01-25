package com.example.madik.barcodesenimdetector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.design.widget.CoordinatorLayout
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.AppCompatImageButton
import android.util.DisplayMetrics
import android.view.Gravity
import android.widget.FrameLayout
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 200
    private var cameraSource: CameraSource? = null
    private lateinit var preview: CameraSourcePreview
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var flashButton: AppCompatImageButton
    private var flashEnabled: Boolean = false

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
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
        preview = findViewById(R.id.camera_source_preview)

        graphicOverlay = findViewById(R.id.graphics_overlay)

        createCameraSource()
        startCameraSource()
        val viewFinderSize = (preview.width * VIEW_FINDER_WIDTH_FRACTION).toInt()

        val viewFinderOffset = dpToPxF(VIEW_FINDER_OFFSET_DP)

        val framingRect = Rect()
        val topOffset = (preview.height - viewFinderSize) / 2
        framingRect.top = (topOffset + viewFinderOffset).toInt()
        framingRect.bottom = (topOffset + viewFinderSize - viewFinderOffset).toInt()

        preview.addView(
            flashButton.apply {
                setImageResource(ICON_FLASH_ON)
                setColorFilter(R.color.white)
                setBackgroundResource(R.drawable.selector_btn_transparent_round)
                setOnClickListener { toggleFlash() }
            },
            CoordinatorLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            })
        positionFlashButton(framingRect)
    }

    public override fun onResume() {
        super.onResume()
        startCameraSource()
    }

    override fun onPause() {
        super.onPause()
        preview.stop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
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

    private fun positionFlashButton(framingRect: Rect) {
        val margin = dpToPxF(16)
        flashButton.y = framingRect.bottom + margin
    }

    private fun Context.dpToPxF(dp: Int): Float =
        (dp * (resources.displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))
}

private const val ICON_FLASH_ON = R.drawable.ic_flash_on
private const val ICON_FLASH_OFF = R.drawable.ic_flash_off
private const val VIEW_FINDER_WIDTH_FRACTION = 0.625f
const val VIEW_FINDER_OFFSET_DP = -16