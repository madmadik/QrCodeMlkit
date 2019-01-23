package com.example.madik.barcodesenimdetector

import android.Manifest
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 200
    private var cameraSource: CameraSource? = null
    private lateinit var preview: CameraSourcePreview
    private lateinit var graphicOverlay: GraphicOverlay


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
        preview = findViewById(R.id.camera_source_preview)

        graphicOverlay = findViewById(R.id.graphics_overlay)

        createCameraSource()
        startCameraSource()
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
}

