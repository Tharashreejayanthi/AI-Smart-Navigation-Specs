package com.innovista.smartglasses.hardware

import android.content.Context
import com.innovista.smartglasses.services.VoiceEngine

/**
 * Factory to create the appropriate HardwareBridge based on user preference.
 */
object HardwareBridgeFactory {

    fun getPreferredBridge(context: Context, voiceEngine: VoiceEngine): HardwareBridge {
        val prefs = context.getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
        val type = prefs.getString("hardware_connection_type", "bluetooth") ?: "bluetooth"

        return when (type.lowercase()) {
            "wifi" -> WiFiHardwareBridge(context, voiceEngine)
            else -> BluetoothHardwareBridge(context, voiceEngine)
        }
    }
}
