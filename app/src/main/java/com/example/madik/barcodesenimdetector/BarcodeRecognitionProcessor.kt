package com.example.madik.barcodesenimdetector

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.common.FirebaseMLException
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import android.graphics.Bitmap


class BarcodeRecognitionProcessor {
    private val TAG = "BarcodeRecProc"

    private var detector = FirebaseVision.getInstance().visionBarcodeDetector

    private val shouldThrottle = AtomicBoolean(false)

    fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown while trying to close Text Detector: $e")
        }
    }


    @Throws(FirebaseMLException::class)
    fun process(data: Bitmap, frameMetadata: FrameMetadata, graphicOverlay: GraphicOverlay) {

        if (shouldThrottle.get()) {
            return
        }

        val croppedBitmap = Bitmap.createBitmap(
            data,
            frameMetadata.width / 5,
            frameMetadata.height / 5,
            (frameMetadata.width / 1.5).toInt(),
            (frameMetadata.height / 1.5).toInt()
        )

        val image = FirebaseVisionImage.fromBitmap(croppedBitmap)
        detectInVisionImage(image, frameMetadata, graphicOverlay)
    }

    private fun detectInImage(image: FirebaseVisionImage): Task<MutableList<FirebaseVisionBarcode>>? {
        return detector.detectInImage(image)
    }


    private fun onSuccess(
        results: MutableList<FirebaseVisionBarcode>,
        frameMetadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    ) {

        graphicOverlay.clear()

        for (barcode in results) {
            val barcodeGraphic = BarcodeGraphic(graphicOverlay, barcode)
            graphicOverlay.add(barcodeGraphic)
            Log.i(TAG, barcode.rawValue.toString())
        }
    }

    private fun onFailure(e: Exception) {
        Log.w(TAG, "Barcode detection failed.$e")
    }

    private fun detectInVisionImage(
        image: FirebaseVisionImage,
        metadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    ) {
        detectInImage(image)?.addOnSuccessListener { results ->
            shouldThrottle.set(false)
            this@BarcodeRecognitionProcessor.onSuccess(results, metadata, graphicOverlay)
        }?.addOnFailureListener { e ->
            shouldThrottle.set(false)
            this@BarcodeRecognitionProcessor.onFailure(e)
        }

        shouldThrottle.set(true)
    }
}