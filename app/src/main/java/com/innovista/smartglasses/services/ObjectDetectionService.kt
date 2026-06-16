package com.innovista.smartglasses.services

import android.annotation.SuppressLint
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * ObjectDetectionService uses ML Kit to detect objects in the camera stream.
 */
class ObjectDetectionService(private val voiceEngine: VoiceEngine) {

    interface Callback {
        fun onObjectsDetected(labels: List<String>)
    }

    private var detector: ObjectDetector? = null
    private var callback: Callback? = null
    private var lastAlertTime = 0L
    private val ALERT_COOLDOWN_MS = 2000L
    private val CLOSE_THRESHOLD = 0.30 // 30% of frame

    fun start(callback: Callback? = null) {
        this.callback = callback
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        detector = ObjectDetection.getClient(options)
    }

    fun stop() {
        detector?.close()
        detector = null
    }

    /**
     * Process an InputImage and return a Task for external orchestration.
     */
    fun processImage(image: InputImage, frameWidth: Int, frameHeight: Int): Task<List<DetectedObject>>? {
        return detector?.process(image)?.addOnSuccessListener { detectedObjects ->
            val frameArea = frameWidth * frameHeight
            val labelsFound = mutableListOf<String>()
            
            for (obj in detectedObjects) {
                val bounds = obj.boundingBox
                val objArea = bounds.width() * bounds.height()
                val relativeSize = objArea.toDouble() / frameArea
                
                val label = obj.labels.maxByOrNull { it.confidence }?.text ?: "Object"
                labelsFound.add(label)

                if (relativeSize > CLOSE_THRESHOLD) {
                    triggerAlert(label)
                }
            }
            callback?.onObjectsDetected(labelsFound)
        }
    }

    private fun triggerAlert(label: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime > ALERT_COOLDOWN_MS) {
            lastAlertTime = currentTime
            voiceEngine.speakInterrupt("Warning: $label detected ahead")
            voiceEngine.vibrate(500)
        }
    }

    companion object {
        private const val TAG = "ObjectDetectionService"
    }
}
