package com.innovista.smartglasses.hardware

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining how the app communicates with the physical Smart Glasses.
 */
interface HardwareBridge {
    fun connect(): Boolean
    fun disconnect()
    fun sendCommand(command: HardwareCommand)
    fun receiveData(): Flow<HardwareData>
    fun isConnected(): LiveData<Boolean>
    fun getDeviceName(): String
    fun shutdown()
}
