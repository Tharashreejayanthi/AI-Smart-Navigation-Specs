package com.innovista.smartglasses.services

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.Executors

/**
 * ObjectFinderService helps the user find a specific object by providing directional guidance.
 */
class ObjectFinderService(private val voiceEngine: VoiceEngine) {

    private var detector: ObjectDetector? = null
    private var isSearching = false
    private var targetObject: String = ""
    private var currentImageAnalysis: ImageAnalysis? = null
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private val searchTimeoutRunnable = Runnable {
        if (isSearching) {
            val failedTarget = targetObject
            stopFinding()
            voiceEngine.speak("Could not find $failedTarget nearby")
        }
    }

    /**
     * Starts searching for a specific object using the camera stream.
     */
    fun findObject(target: String, analyzer: ImageAnalysis) {
        // Stop any ongoing search before starting a new one
        stopFinding()

        this.targetObject = target
        this.isSearching = true
        this.currentImageAnalysis = analyzer

        voiceEngine.speak("Searching for $targetObject...")

        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
        detector = ObjectDetection.getClient(options)

        analyzer.setAnalyzer(executor) { imageProxy ->
            if (isSearching) {
                analyzeImage(imageProxy)
            } else {
                imageProxy.close()
            }
        }

        // Set 15-second timeout
        handler.postDelayed(searchTimeoutRunnable, 15000)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        detector?.process(image)
            ?.addOnSuccessListener { objects ->
                if (!isSearching) return@addOnSuccessListener

                for (obj in objects) {
                    val label = obj.labels.maxByOrNull { it.confidence }?.text ?: ""
                    if (label.equals(targetObject, ignoreCase = true)) {
                        
                        // Determine the center X relative to the processed image orientation
                        val centerX = obj.boundingBox.centerX()
                        
                        // If rotation is 90 or 270, the "width" seen by the user is the imageProxy.height
                        val referenceWidth = if (rotation == 90 || rotation == 270) {
                            imageProxy.height
                        } else {
                            imageProxy.width
                        }

                        val position = when {
                            centerX < referenceWidth / 3 -> "to your left"
                            centerX < (referenceWidth * 2) / 3 -> "straight ahead"
                            else -> "to your right"
                        }

                        val foundObject = targetObject
                        stopFinding()
                        voiceEngine.speak("$foundObject found, $position")
                        break
                    }
                }
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Object search failed", e)
            }
            ?.addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * Cancels the search and releases resources.
     */
    fun stopFinding() {
        isSearching = false
        handler.removeCallbacks(searchTimeoutRunnable)
        currentImageAnalysis?.clearAnalyzer()
        currentImageAnalysis = null
        detector?.close()
        detector = null
    }

    companion object {
        private const val TAG = "ObjectFinderService"
    }
}
