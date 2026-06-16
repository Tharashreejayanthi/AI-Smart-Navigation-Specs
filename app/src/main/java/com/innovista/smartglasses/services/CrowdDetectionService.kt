package com.innovista.smartglasses.services

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

enum class CrowdLevel {
    EMPTY, NORMAL, MODERATE, DENSE
}

/**
 * CrowdDetectionService analyzes images to detect and count people.
 */
class CrowdDetectionService(private val voiceEngine: VoiceEngine) {

    private var detector: ObjectDetector? = null
    private var lastAlertTime = 0L
    private val ALERT_COOLDOWN_MS = 5000L
    private val LOG_INTERVAL_MS = 10000L
    
    private var currentPeopleCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private val logRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "Current Crowd Count: $currentPeopleCount | Level: ${getCrowdLevel()}")
            handler.postDelayed(this, LOG_INTERVAL_MS)
        }
    }

    fun start() {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        detector = ObjectDetection.getClient(options)
        handler.post(logRunnable)
    }

    fun stop() {
        detector?.close()
        detector = null
        handler.removeCallbacks(logRunnable)
    }

    /**
     * Process an InputImage and return a Task for external orchestration.
     */
    fun processImage(image: InputImage): Task<List<DetectedObject>>? {
        return detector?.process(image)?.addOnSuccessListener { detectedObjects ->
            currentPeopleCount = detectedObjects.count { obj ->
                obj.labels.any { it.text.equals("Person", ignoreCase = true) }
            }
            checkCrowdThresholds()
        }
    }

    private fun checkCrowdThresholds() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime < ALERT_COOLDOWN_MS) return

        when (getCrowdLevel()) {
            CrowdLevel.MODERATE -> {
                voiceEngine.speakInterrupt("Moderate crowd ahead, proceed carefully")
                voiceEngine.vibrate(500)
                lastAlertTime = currentTime
            }
            CrowdLevel.DENSE -> {
                voiceEngine.speakInterrupt("Dense crowd detected, danger alert, consider alternate route")
                voiceEngine.vibrate(1000)
                lastAlertTime = currentTime
            }
            else -> { /* No alert */ }
        }
    }

    fun getCrowdLevel(): CrowdLevel {
        return when {
            currentPeopleCount == 0 -> CrowdLevel.EMPTY
            currentPeopleCount <= 3 -> CrowdLevel.NORMAL
            currentPeopleCount <= 7 -> CrowdLevel.MODERATE
            else -> CrowdLevel.DENSE
        }
    }

    companion object {
        private const val TAG = "CrowdDetectionService"
    }
}
