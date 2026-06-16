package com.innovista.smartglasses.hardware

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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

/**
 * Bluetooth implementation of HardwareBridge using BLE.
 */
class BluetoothHardwareBridge(
    private val context: Context,
    private val voiceEngine: VoiceEngine
) : HardwareBridge {

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    
    private val _isConnected = MutableLiveData<Boolean>(false)
    private val incomingDataFlow = MutableSharedFlow<HardwareData>()
    private var scope = CoroutineScope(Dispatchers.IO)

    override fun connect(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or disabled")
            return false
        }

        Log.d(TAG, "Scanning for SmartGlasses-HW...")
        
        // Mock connection logic
        scope.launch {
            delay(2000)
            _isConnected.postValue(true)
            voiceEngine.speak("Hardware glasses connected")
            Log.d(TAG, "Connected to SmartGlasses-HW via BLE")
        }
        
        return true
    }

    override fun disconnect() {
        _isConnected.postValue(false)
        voiceEngine.speak("Hardware glasses disconnected")
        Log.d(TAG, "Disconnected from hardware")
    }

    override fun sendCommand(command: HardwareCommand) {
        if (_isConnected.value == true) {
            val jsonCommand = Gson().toJson(mapOf("command" to command.name))
            Log.d(TAG, "Sending BLE command: $jsonCommand")
            // In real impl: write to BLE characteristic
        } else {
            Log.w(TAG, "Cannot send command: Hardware not connected")
        }
    }

    override fun receiveData(): Flow<HardwareData> = incomingDataFlow

    override fun isConnected(): LiveData<Boolean> = _isConnected

    override fun getDeviceName(): String = "SmartGlasses-HW (BLE)"

    override fun shutdown() {
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO)
    }

    companion object {
        private const val TAG = "BluetoothBridge"
    }
}
