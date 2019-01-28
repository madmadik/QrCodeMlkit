package com.example.madik.barcodesenimdetector

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.*
import android.os.Build
import android.renderscript.*
import android.support.annotation.RequiresApi
import android.support.annotation.RequiresPermission
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import android.graphics.Bitmap
import android.R.attr.data
import android.renderscript.Allocation
import android.support.v4.view.ViewCompat.setX
import android.support.v4.view.ViewCompat.setY
import android.renderscript.Element.U8


class CameraSource {

    companion object {
        @SuppressLint("InlinedApi")
        val CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK
        @SuppressLint("InlinedApi")
        val CAMERA_FACING_FRONT = CameraInfo.CAMERA_FACING_FRONT
    }

    private val TAG = "CameraSource"

    /**
     * The dummy surface texture must be assigned a chosen name. Since we never use an OpenGL context,
     * we can choose any ID we want here. The dummy surface texture is not a crazy hack - it is
     * actually how the camera team recommends using the camera without a preview.
     */
    private val DUMMY_TEXTURE_NAME = 100

    /**
     * If the absolute difference between a preview size aspect ratio and a picture size aspect ratio
     * is less than this tolerance, they are considered to be the same aspect ratio.
     */
    private val ASPECT_RATIO_TOLERANCE = 0.01f

    private var activity: Activity

    var camera: Camera? = null

    private var facing = CAMERA_FACING_BACK

    /**
     * Rotation of the device, and thus the associated preview images captured from the device. See
     * Frame.Metadata#getRotation().
     */
    private var rotation: Int = 0

    private var previewSize: Size? = null

    private val requestedFps = 20.0f
    private val requestedPreviewWidth = 1280
    private val requestedPreviewHeight = 960
    private val requestedAutoFocus = true

    private var dummySurfaceTexture: SurfaceTexture? = null
    private var graphicOverlay: GraphicOverlay

    private var usingSurfaceTexture: Boolean = false

    /**
     * Dedicated thread and associated runnable for calling into the detector with frames, as the
     * frames become available from the camera.
     */
    private var processingThread: Thread? = null

    private var processingRunnable: FrameProcessingRunnable

    private val processorLock = Any()
    // @GuardedBy("processorLock")
    private var frameProcessor: BarcodeRecognitionProcessor? = null

    /**
     * Map to convert between a byte array, received from the camera, and its associated byte buffer.
     * We use byte buffers internally because this is a more efficient way to call into native code
     * later (avoids a potential copy).
     *
     *
     * **Note:** uses IdentityHashMap here instead of HashMap because the behavior of an array's
     * equals, hashCode and toString methods is both useless and unexpected. IdentityHashMap enforces
     * identity ('==') check on the keys.
     */
    private val bytesToByteBuffer = IdentityHashMap<ByteArray, ByteBuffer>()

    private var rs: RenderScript
    private var yuvToRgbIntrinsic: ScriptIntrinsicYuvToRGB
    private var yuvType: Type.Builder? = null
    private var rgbaType: Type.Builder? = null
    private var inner: Allocation? = null
    private var outer: Allocation? = null


    @SuppressLint("NewApi")
    constructor(activity: Activity, overlay: GraphicOverlay) {
        this.activity = activity
        graphicOverlay = overlay
        graphicOverlay.clear()
        processingRunnable = FrameProcessingRunnable()
        rs = RenderScript.create(activity.applicationContext)
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
    }

    /** Stops the camera and releases the resources of the camera and underlying detector.  */
    fun release() {
        synchronized(processorLock) {
            stop()
            processingRunnable.release()
            cleanScreen()

            if (frameProcessor != null) {
                frameProcessor!!.stop()
            }
        }
    }

    /**
     * Opens the camera and starts sending preview frames to the underlying detector. The preview
     * frames are not displayed.
     *
     * @throws IOException if the camera's preview texture or display could not be initialized
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.CAMERA)
    @Synchronized
    @Throws(IOException::class)
    fun start(): CameraSource {
        if (camera != null) {
            return this
        }

        camera = createCamera()
        dummySurfaceTexture = SurfaceTexture(DUMMY_TEXTURE_NAME)
        camera!!.setPreviewTexture(dummySurfaceTexture)
        usingSurfaceTexture = true
        camera!!.startPreview()

        processingThread = Thread(processingRunnable)
        processingRunnable.setActive(true)
        processingThread!!.start()
        return this
    }

    /**
     * Opens the camera and starts sending preview frames to the underlying detector. The supplied
     * surface holder is used for the preview so frames can be displayed to the user.
     *
     * @param surfaceHolder the surface holder to use for the preview frames
     * @throws IOException if the supplied surface holder could not be used as the preview display
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    @Synchronized
    @Throws(IOException::class)
    fun start(surfaceHolder: SurfaceHolder): CameraSource {
        if (camera != null) {
            return this
        }

        camera = createCamera()
        camera!!.setPreviewDisplay(surfaceHolder)
        camera!!.startPreview()

        processingThread = Thread(processingRunnable)
        processingRunnable.setActive(true)
        processingThread!!.start()

        usingSurfaceTexture = false
        return this
    }

    /**
     * Closes the camera and stops sending frames to the underlying frame detector.
     *
     *
     * This camera source may be restarted again by calling [.start] or [ ][.start].
     *
     *
     * Call [.release] instead to completely shut down this camera source and release the
     * resources of the underlying detector.
     */
    @Synchronized
    fun stop() {
        processingRunnable.setActive(false)
        if (processingThread != null) {
            try {
                // Wait for the thread to complete to ensure that we can't have multiple threads
                // executing at the same time (i.e., which would happen if we called start too
                // quickly after stop).
                processingThread!!.join()
            } catch (e: InterruptedException) {
                Log.d(TAG, "Frame processing thread interrupted on release.")
            }

            processingThread = null
        }

        if (camera != null) {
            camera!!.stopPreview()
            camera!!.setPreviewCallbackWithBuffer(null)
            try {
                if (usingSurfaceTexture) {
                    camera!!.setPreviewTexture(null)
                } else {
                    camera!!.setPreviewDisplay(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear camera preview: $e")
            }

            camera!!.release()
            camera = null
        }

        bytesToByteBuffer.clear()
    }

    /** Changes the facing of the camera.  */
    @Synchronized
    fun setFacing(facing: Int) {
        if (facing != CAMERA_FACING_BACK && facing != CAMERA_FACING_FRONT) {
            throw IllegalArgumentException("Invalid camera: $facing")
        }
        this.facing = facing
    }

    /** Returns the preview size that is currently in use by the underlying camera.  */
    fun getPreviewSize(): Size? {
        return previewSize
    }

    /**
     * Returns the selected camera; one of [.CAMERA_FACING_BACK] or [ ][.CAMERA_FACING_FRONT].
     */
    fun getCameraFacing(): Int {
        return facing
    }

    /**
     * Opens the camera and applies the user settings.
     *
     * @throws IOException if camera cannot be found or preview cannot be processed
     */
    @SuppressLint("InlinedApi")
    @Throws(IOException::class)
    private fun createCamera(): Camera {
        val requestedCameraId = getIdForRequestedCamera(facing)
        if (requestedCameraId == -1) {
            throw IOException("Could not find requested camera.")
        }
        val camera = open(requestedCameraId)

        val sizePair = selectSizePair(camera, requestedPreviewWidth, requestedPreviewHeight)
            ?: throw IOException("Could not find suitable preview size.")
        val pictureSize = sizePair.pictureSize()
        previewSize = sizePair.previewSize()

        val previewFpsRange = selectPreviewFpsRange(camera, requestedFps)
            ?: throw IOException("Could not find suitable preview frames per second range.")

        val parameters = camera.parameters

        if (pictureSize != null) {
            parameters.setPictureSize(pictureSize.width, pictureSize.height)
        }
        parameters.setPreviewSize(previewSize!!.width, previewSize!!.height)
        parameters.setPreviewFpsRange(
            previewFpsRange[Parameters.PREVIEW_FPS_MIN_INDEX],
            previewFpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]
        )
        parameters.previewFormat = ImageFormat.NV21

        setRotation(camera, parameters, requestedCameraId)

        if (requestedAutoFocus) {
            if (parameters
                    .supportedFocusModes
                    .contains(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)
            ) {
                parameters.focusMode = Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            } else {
                Log.i(TAG, "Camera auto focus is not supported on this device.")
            }
        }

        camera.parameters = parameters

        camera.setPreviewCallbackWithBuffer(CameraPreviewCallback())
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))
        camera.addCallbackBuffer(createPreviewBuffer(previewSize!!))

        return camera
    }

    /**
     * Gets the id for the camera specified by the direction it is facing. Returns -1 if no such
     * camera was found.
     *
     * @param facing the desired camera (front-facing or rear-facing)
     */
    private fun getIdForRequestedCamera(facing: Int): Int {
        val cameraInfo = CameraInfo()
        for (i in 0 until getNumberOfCameras()) {
            getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == facing) {
                return i
            }
        }
        return -1
    }

    /**
     * Selects the most suitable preview and picture size, given the desired width and height.
     *
     *
     * Even though we only need to find the preview size, it's necessary to find both the preview
     * size and the picture size of the camera together, because these need to have the same aspect
     * ratio. On some hardware, if you would only set the preview size, you will get a distorted
     * image.
     *
     * @param camera the camera to select a preview size from
     * @param desiredWidth the desired width of the camera preview frames
     * @param desiredHeight the desired height of the camera preview frames
     * @return the selected preview and picture size pair
     */
    private fun selectSizePair(camera: Camera, desiredWidth: Int, desiredHeight: Int): SizePair? {
        val validPreviewSizes = generateValidPreviewSizeList(camera)

        var selectedPair: SizePair? = null
        var minDiff = Integer.MAX_VALUE
        for (sizePair in validPreviewSizes) {
            val size = sizePair.previewSize()
            val diff = Math.abs(size.width - desiredWidth) + Math.abs(size.height - desiredHeight)
            if (diff < minDiff) {
                selectedPair = sizePair
                minDiff = diff
            }
        }

        return selectedPair
    }

    fun setFlashOn() {
        val cameraParams = camera?.parameters
        cameraParams?.flashMode = Camera.Parameters.FLASH_MODE_TORCH
        camera?.parameters = cameraParams
    }

    fun setFlashOff() {
        val cameraParams = camera?.parameters
        cameraParams?.flashMode = Camera.Parameters.FLASH_MODE_OFF
        camera?.parameters = cameraParams
    }

    /**
     * Stores a preview size and a corresponding same-aspect-ratio picture size. To avoid distorted
     * preview images on some devices, the picture size must be set to a size that is the same aspect
     * ratio as the preview size or the preview may end up being distorted. If the picture size is
     * null, then there is no picture size with the same aspect ratio as the preview size.
     */
    private class SizePair internal constructor(
        previewSize: Camera.Size,
        pictureSize: Camera.Size?,
        camera: Camera
    ) {
        private val preview: Size = camera.Size(previewSize.width, previewSize.height)
        private var picture: Size? = null

        init {
            if (pictureSize != null) {
                picture = camera.Size(pictureSize.width, pictureSize.height)
            }
        }

        internal fun previewSize(): Size {
            return preview
        }

        internal fun pictureSize(): Size? {
            return picture
        }
    }

    /**
     * Generates a list of acceptable preview sizes. Preview sizes are not acceptable if there is not
     * a corresponding picture size of the same aspect ratio. If there is a corresponding picture size
     * of the same aspect ratio, the picture size is paired up with the preview size.
     *
     *
     * This is necessary because even if we don't use still pictures, the still picture size must
     * be set to a size that is the same aspect ratio as the preview size we choose. Otherwise, the
     * preview images may be distorted on some devices.
     */
    private fun generateValidPreviewSizeList(camera: Camera): List<SizePair> {
        val parameters = camera.parameters
        val supportedPreviewSizes = parameters.supportedPreviewSizes
        val supportedPictureSizes = parameters.supportedPictureSizes
        val validPreviewSizes = ArrayList<SizePair>()
        for (previewSize in supportedPreviewSizes) {
            val previewAspectRatio = previewSize.width.toFloat() / previewSize.height.toFloat()

            // By looping through the picture sizes in order, we favor the higher resolutions.
            // We choose the highest resolution in order to support taking the full resolution
            // picture later.
            for (pictureSize in supportedPictureSizes) {
                val pictureAspectRatio = pictureSize.width.toFloat() / pictureSize.height.toFloat()
                if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                    validPreviewSizes.add(SizePair(previewSize, pictureSize, camera))
                    break
                }
            }
        }

        if (validPreviewSizes.size == 0) {
            Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size")
            for (previewSize in supportedPreviewSizes) {
                // The null picture size will let us know that we shouldn't set a picture size.
                validPreviewSizes.add(SizePair(previewSize, null, camera))
            }
        }

        return validPreviewSizes
    }

    /**
     * Selects the most suitable preview frames per second range, given the desired frames per second.
     *
     * @param camera the camera to select a frames per second range from
     * @param desiredPreviewFps the desired frames per second for the camera preview frames
     * @return the selected preview frames per second range
     */
    @SuppressLint("InlinedApi")
    private fun selectPreviewFpsRange(camera: Camera, desiredPreviewFps: Float): IntArray? {
        val desiredPreviewFpsScaled = (desiredPreviewFps * 1000.0f).toInt()

        var selectedFpsRange: IntArray? = null
        var minDiff = Integer.MAX_VALUE
        val previewFpsRangeList = camera.parameters.supportedPreviewFpsRange
        for (range in previewFpsRangeList) {
            val deltaMin = desiredPreviewFpsScaled - range[Parameters.PREVIEW_FPS_MIN_INDEX]
            val deltaMax = desiredPreviewFpsScaled - range[Parameters.PREVIEW_FPS_MAX_INDEX]
            val diff = Math.abs(deltaMin) + Math.abs(deltaMax)
            if (diff < minDiff) {
                selectedFpsRange = range
                minDiff = diff
            }
        }
        return selectedFpsRange
    }

    /**
     * Calculates the correct rotation for the given camera id and sets the rotation in the
     * parameters. It also sets the camera's display orientation and rotation.
     *
     * @param parameters the camera parameters for which to set the rotation
     * @param cameraId the camera id to set rotation based on
     */
    private fun setRotation(camera: Camera, parameters: Camera.Parameters, cameraId: Int) {
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var degrees = 0
        val rotation = windowManager.defaultDisplay.rotation
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
            else -> Log.e(TAG, "Bad rotation value: $rotation")
        }

        val cameraInfo = CameraInfo()
        getCameraInfo(cameraId, cameraInfo)

        val angle: Int
        val displayAngle: Int
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
            angle = (cameraInfo.orientation + degrees) % 360
            displayAngle = (360 - angle) % 360 // compensate for it being mirrored
        } else { // back-facing
            angle = (cameraInfo.orientation - degrees + 360) % 360
            displayAngle = angle
        }

        this.rotation = angle / 90

        camera.setDisplayOrientation(displayAngle)
        parameters.setRotation(angle)
    }

    /**
     * Creates one buffer for the camera preview callback. The size of the buffer is based off of the
     * camera preview size and the format of the camera image.
     *
     * @return a new preview buffer of the appropriate size for the current camera settings
     */
    @SuppressLint("InlinedApi")
    private fun createPreviewBuffer(previewSize: Size): ByteArray {
        val bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21)
        val sizeInBits = previewSize.height * previewSize.width * bitsPerPixel
        val bufferSize = Math.ceil(sizeInBits / 8.0).toInt() + 1

        val byteArray = ByteArray(bufferSize)
        val buffer = ByteBuffer.wrap(byteArray)
        if (!buffer.hasArray() || buffer.array() != byteArray) {
            throw IllegalStateException("Failed to create valid buffer for camera source.")
        }

        bytesToByteBuffer[byteArray] = buffer
        return byteArray
    }


    /** Called when the camera has a new preview frame.  */
    private inner class CameraPreviewCallback : Camera.PreviewCallback {
        override fun onPreviewFrame(data: ByteArray, camera: Camera) {
            processingRunnable.setNextFrame(data, camera)
        }
    }

    fun setMachineLearningFrameProcessor(processor: BarcodeRecognitionProcessor) {
        synchronized(processorLock) {
            cleanScreen()
            if (frameProcessor != null) {
                frameProcessor!!.stop()
            }
            frameProcessor = processor
        }
    }

    /**
     * This runnable controls access to the underlying receiver, calling it to process frames when
     * available from the camera. This is designed to run detection on frames as fast as possible
     * (i.e., without unnecessary context switching or waiting on the next frame).
     *
     *
     * While detection is running on a frame, new frames may be received from the camera. As these
     * frames come in, the most recent frame is held onto as pending. As soon as detection and its
     * associated processing is done for the previous frame, detection on the mostly recently received
     * frame will immediately start on the same thread.
     */
    private inner class FrameProcessingRunnable internal constructor() : Runnable {

        private val lock = Object()
        private var active = true

        private var pendingFrameData: ByteBuffer? = null

        /**
         * Releases the underlying receiver. This is only safe to do after the associated thread has
         * completed, which is managed in camera source's release method above.
         */
        @SuppressLint("Assert")
        internal fun release() {
            assert(processingThread!!.state == Thread.State.TERMINATED)
        }

        /** Marks the runnable as active/not active. Signals any blocked threads to continue.  */
        internal fun setActive(active: Boolean) {
            synchronized(lock) {
                this.active = active
                lock.notifyAll()
            }
        }

        /**
         * Sets the frame data received from the camera. This adds the previous unused frame buffer (if
         * present) back to the camera, and keeps a pending reference to the frame data for future use.
         */
        internal fun setNextFrame(data: ByteArray, camera: Camera) {
            synchronized(lock) {
                if (pendingFrameData != null) {
                    camera.addCallbackBuffer(pendingFrameData!!.array())
                    pendingFrameData = null
                }

                if (!bytesToByteBuffer.containsKey(data)) {
                    Log.d(
                        TAG,
                        "Skipping frame. Could not find ByteBuffer associated with the image " + "data from the camera."
                    )
                    return
                }

                pendingFrameData = bytesToByteBuffer[data]

                // Notify the processor thread if it is waiting on the next frame (see below).
                lock.notifyAll()
            }
        }

        /**
         * As long as the processing thread is active, this executes detection on frames continuously.
         * The next pending frame is either immediately available or hasn't been received yet. Once it
         * is available, we transfer the frame info to local variables and run detection on that frame.
         * It immediately loops back for the next frame without pausing.
         *
         *
         * If detection takes longer than the time in between new frames from the camera, this will
         * mean that this loop will run without ever waiting on a frame, avoiding any context switching
         * or frame acquisition time latency.
         *
         *
         * If you find that this is using more CPU than you'd like, you should probably decrease the
         * FPS setting above to allow for some idle time in between frames.
         */
        @SuppressLint("InlinedApi")
        override fun run() {
            lateinit var data: ByteBuffer

            while (true) {
                synchronized(lock) {
                    while (active && pendingFrameData == null) {
                        try {
                            lock.wait()
                        } catch (e: InterruptedException) {
                            Log.d(TAG, "Frame processing loop terminated.", e)
                            return
                        }

                    }

                    if (!active) {
                        return
                    }

                    data = this.pendingFrameData!!
                    pendingFrameData = null
                }

                try {
                    synchronized(processorLock) {
                        Log.d(TAG, "Process an image")

                        if (yuvType == null) {
                            yuvType = Type.Builder(rs, Element.U8(rs)).setX(data.array().size)
                            inner = Allocation.createTyped(rs, yuvType!!.create(), Allocation.USAGE_SCRIPT)

                            rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(previewSize!!.width)
                                .setY(previewSize!!.height)
                            outer = Allocation.createTyped(rs, rgbaType!!.create(), Allocation.USAGE_SCRIPT)
                        }

                        inner?.copyFrom(data.array())

                        yuvToRgbIntrinsic.setInput(inner)
                        yuvToRgbIntrinsic.forEach(outer)

                        val bitmap =
                            Bitmap.createBitmap(previewSize!!.width, previewSize!!.height, Bitmap.Config.ARGB_8888)
                        outer?.copyTo(bitmap)

                        frameProcessor!!.process(
                            bitmap,
                            FrameMetadata.Builder()
                                .setWidth(previewSize!!.width)
                                .setHeight(previewSize!!.height)
                                .setRotation(rotation)
                                .setCameraFacing(facing)
                                .build(),
                            graphicOverlay
                        )
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Exception thrown from receiver.", t)
                } finally {
                    camera!!.addCallbackBuffer(data.array())
                }
            }
        }
    }

    /** Cleans up graphicOverlay and child classes can do their cleanups as well .  */
    private fun cleanScreen() {
        graphicOverlay.clear()
    }
}