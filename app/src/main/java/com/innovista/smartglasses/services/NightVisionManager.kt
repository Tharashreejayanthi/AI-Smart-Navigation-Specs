package com.innovista.smartglasses.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

enum class VisionMode {
    DAY, DIM, NIGHT
}

/**
 * NightVisionManager monitors ambient light levels and manages camera vision modes.
 */
class NightVisionManager(
    private val context: Context,
    private val voiceEngine: VoiceEngine
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _currentVisionMode = MutableLiveData<VisionMode>(VisionMode.DAY)
    val currentVisionMode: LiveData<VisionMode> get() = _currentVisionMode

    private var lastCheckTime = 0L
    private val CHECK_INTERVAL_MS = 5000L
    private var isManualMode = false

    /**
     * Activates the light sensor monitoring.
     */
    fun activate() {
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /**
     * Deactivates the light sensor monitoring.
     */
    fun deactivate() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCheckTime >= CHECK_INTERVAL_MS) {
                lastCheckTime = currentTime
                if (!isManualMode) {
                    processLightLevel(event.values[0])
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processLightLevel(lux: Float) {
        val newMode = when {
            lux > 100 -> VisionMode.DAY
            lux >= 50 -> VisionMode.DIM
            else -> VisionMode.NIGHT
        }
        setVisionMode(newMode, isManual = false)
    }

    /**
     * Manually sets the vision mode.
     */
    fun setManualVisionMode(mode: VisionMode) {
        isManualMode = true
        setVisionMode(mode, isManual = true)
    }

    /**
     * Returns to automatic light sensing.
     */
    fun setAutoMode() {
        isManualMode = false
    }

    private fun setVisionMode(mode: VisionMode, isManual: Boolean) {
        val previousMode = _currentVisionMode.value
        if (mode == previousMode) return

        _currentVisionMode.postValue(mode)

        when (mode) {
            VisionMode.DAY -> {
                if (previousMode == VisionMode.NIGHT) {
                    voiceEngine.speak("Day vision mode")
                }
            }
            VisionMode.NIGHT -> {
                if (previousMode == VisionMode.DAY || isManual) {
                    voiceEngine.speak("Night vision mode activated")
                }
            }
            else -> {} // DIM mode is silent
        }
    }
}
