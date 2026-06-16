package com.innovista.smartglasses.services

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraService handles the CameraX implementation for the Smart Glasses app.
 * It provides preview, image analysis, and image capture capabilities.
 */
@ExperimentalCamera2Interop
class CameraService(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private var currentPreviewView: PreviewView? = null
    private val analyzers = mutableListOf<ImageAnalysis.Analyzer>()
    private var currentVisionMode = VisionMode.DAY

    /**
     * Starts the camera with the provided PreviewView.
     */
    fun startCamera(previewView: PreviewView) {
        currentPreviewView = previewView
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Build Preview
            val previewBuilder = Preview.Builder()
            
            // Apply Vision Mode settings
            applyVisionSettings(previewBuilder)

            preview = previewBuilder
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Build ImageAnalysis
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Build ImageCapture
            val captureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            
            applyVisionSettings(captureBuilder)
            imageCapture = captureBuilder.build()
            
            // Re-bind analyzers
            updateAnalyzer()

            try {
                // Unbind use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                (context as? com.innovista.smartglasses.ui.MainActivity)?.getVoiceEngine()?.speak("Camera error, please restart app")
            }

        }, ContextCompat.getMainExecutor(context))
    }

    private fun applyVisionSettings(builder: Any) {
        val extender = when (builder) {
            is Preview.Builder -> Camera2Interop.Extender(builder)
            is ImageCapture.Builder -> Camera2Interop.Extender(builder)
            else -> return
        }

        when (currentVisionMode) {
            VisionMode.NIGHT -> {
                extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                extender.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 100_000_000L) // 0.1s
                extender.setCaptureRequestOption(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_MONO)
            }
            VisionMode.DIM -> {
                extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 6)
            }
            VisionMode.DAY -> {
                // Default settings
            }
        }
    }

    /**
     * Sets the vision mode and restarts the camera if needed.
     */
    fun setVisionMode(mode: VisionMode) {
        if (currentVisionMode != mode) {
            currentVisionMode = mode
            currentPreviewView?.let { startCamera(it) }
        }
    }

    /**
     * Adds an analyzer to the camera stream.
     */
    fun addAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        if (!analyzers.contains(analyzer)) {
            analyzers.add(analyzer)
            updateAnalyzer()
        }
    }

    /**
     * Removes an analyzer from the camera stream.
     */
    fun removeAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        if (analyzers.remove(analyzer)) {
            updateAnalyzer()
        }
    }

    /**
     * Sets a single analyzer, replacing all existing ones.
     */
    fun setAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        analyzers.clear()
        analyzers.add(analyzer)
        updateAnalyzer()
    }

    private fun updateAnalyzer() {
        if (analyzers.isEmpty()) {
            imageAnalysis?.clearAnalyzer()
        } else {
            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                // If there's only one analyzer, let it handle the proxy (it must close it)
                if (analyzers.size == 1) {
                    analyzers[0].analyze(imageProxy)
                } else {
                    // If multiple, we'd need a more complex multiplexer.
                    // For now, we'll just use the first one.
                    analyzers[0].analyze(imageProxy)
                }
            }
        }
    }

    /**
     * Returns the ImageCapture use case.
     */
    fun getImageCapture(): ImageCapture? = imageCapture

    /**
     * Returns the ImageAnalysis use case.
     */
    fun getImageAnalysis(): ImageAnalysis? = imageAnalysis

    /**
     * Toggles between front and back camera.
     */
    fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        // Restart camera if it was already running
        currentPreviewView?.let { startCamera(it) }
    }

    /**
     * Unbinds all use cases and stops the camera.
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    /**
     * Shuts down the executor. Should be called when the service is no longer needed.
     */
    fun shutdown() {
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraService"
    }
}
