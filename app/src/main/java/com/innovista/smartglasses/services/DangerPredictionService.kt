package com.innovista.smartglasses.services

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import java.util.Calendar

enum class DangerLevel {
    SAFE, CAUTION, DANGER
}

/**
 * DangerPredictionService evaluates the surrounding environment and provides 
 * safety warnings based on crowd density, time, and movement.
 */
class DangerPredictionService(
    private val voiceEngine: VoiceEngine,
    private val crowdDetectionService: CrowdDetectionService
) {

    private val _dangerLevel = MutableLiveData<DangerLevel>(DangerLevel.SAFE)
    val dangerLevel: LiveData<DangerLevel> get() = _dangerLevel

    private var serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var isRunning = false

    /**
     * Starts the periodic danger evaluation loop.
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        serviceScope.launch {
            while (isRunning) {
                evaluateDanger()
                delay(10000) // Check every 10 seconds
            }
        }
    }

    /**
     * Stops the danger evaluation loop.
     */
    fun stop() {
        isRunning = false
        serviceScope.cancel()
        serviceScope = CoroutineScope(Dispatchers.Default + Job())
    }

    private fun evaluateDanger() {
        var score = 0

        // 1. Crowd Score
        score += when (crowdDetectionService.getCrowdLevel()) {
            CrowdLevel.EMPTY -> 0
            CrowdLevel.NORMAL -> 1
            CrowdLevel.MODERATE -> 3
            CrowdLevel.DENSE -> 5
        }

        // 2. Time Score
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        score += when {
            hour in 6..19 -> 0  // 6AM - 8PM
            hour in 20..21 -> 2 // 8PM - 10PM
            else -> 4           // 10PM - 6AM
        }

        // 3. Movement Score
        val currentSpeed = LocationService.currentLocation.value?.speed ?: 0f
        if (currentSpeed > 2.0f) {
            score += 1
        }

        // Determine Level and Alert
        val newLevel = when {
            score <= 2 -> DangerLevel.SAFE
            score <= 5 -> DangerLevel.CAUTION
            else -> DangerLevel.DANGER
        }

        // Trigger Alert if level changed or is high
        if (newLevel != _dangerLevel.value) {
            when (newLevel) {
                DangerLevel.CAUTION -> {
                    voiceEngine.speak("Caution: Stay alert in this area")
                    voiceEngine.vibrate(500)
                }
                DangerLevel.DANGER -> {
                    voiceEngine.speak("High danger detected. Consider moving to a safer location")
                    voiceEngine.vibrate(1000)
                }
                else -> {}
            }
            _dangerLevel.postValue(newLevel)
        }
    }
}
