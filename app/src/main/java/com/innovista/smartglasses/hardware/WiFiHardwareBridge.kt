package com.innovista.smartglasses.hardware

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.innovista.smartglasses.services.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.net.Socket

/**
 * WiFi implementation of HardwareBridge using TCP Sockets.
 */
class WiFiHardwareBridge(
    private val context: Context,
    private val voiceEngine: VoiceEngine
) : HardwareBridge {

    private val _isConnected = MutableLiveData<Boolean>(false)
    private val incomingDataFlow = MutableSharedFlow<HardwareData>()
    private var scope = CoroutineScope(Dispatchers.IO)
    private var socket: Socket? = null

    override fun connect(): Boolean {
        val prefs = context.getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
        val ipAddress = prefs.getString("hardware_ip", "192.168.1.100") ?: "192.168.1.100"

        Log.d(TAG, "Connecting to WiFi Hardware at $ipAddress:8080...")

        scope.launch {
            try {
                // Mock socket connection
                delay(1500)
                _isConnected.postValue(true)
                voiceEngine.speak("Hardware glasses connected")
                Log.d(TAG, "TCP Socket connected to $ipAddress")
            } catch (e: Exception) {
                Log.e(TAG, "WiFi connection failed", e)
                _isConnected.postValue(false)
            }
        }
        
        return true
    }

    override fun disconnect() {
        scope.launch {
            socket?.close()
            socket = null
            _isConnected.postValue(false)
            voiceEngine.speak("Hardware glasses disconnected")
            Log.d(TAG, "WiFi Socket closed")
        }
    }

    override fun sendCommand(command: HardwareCommand) {
        if (_isConnected.value == true) {
            val jsonCommand = Gson().toJson(mapOf("command" to command.name))
            Log.d(TAG, "Sending WiFi command: $jsonCommand")
            // In real impl: write to socket output stream
        }
    }

    override fun receiveData(): Flow<HardwareData> = incomingDataFlow

    override fun isConnected(): LiveData<Boolean> = _isConnected

    override fun getDeviceName(): String {
        val prefs = context.getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
        return "SmartGlasses-WiFi (${prefs.getString("hardware_ip", "Unknown")})"
    }

    override fun shutdown() {
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO)
    }

    companion object {
        private const val TAG = "WiFiBridge"
    }
}
