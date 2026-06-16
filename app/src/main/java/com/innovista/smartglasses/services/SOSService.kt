package com.innovista.smartglasses.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.innovista.smartglasses.ui.EmergencyContact

/**
 * SOSService handles emergency alerts, SMS notifications, and automated calls.
 */
class SOSService(
    private val context: Context,
    private val voiceEngine: VoiceEngine
) {

    var isSOSActive: Boolean = false
        private set

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Triggers the SOS sequence.
     * Returns true if SOS was initiated, false if no contacts found.
     */
    fun triggerSOS(currentLocation: Location?): Boolean {
        val contacts = loadContacts()
        if (contacts.isEmpty()) {
            voiceEngine.speak("No emergency contacts found. Please set up emergency contacts first.")
            return false
        }

        isSOSActive = true
        voiceEngine.speak("Sending SOS alert, please wait")
        vibrateSOS()

        val locationUrl = if (currentLocation != null) {
            "https://maps.google.com/?q=${currentLocation.latitude},${currentLocation.longitude}"
        } else {
            "Location unavailable"
        }

        val contactNames = contacts.map { it.name }.joinToString(" and ")
        
        for (contact in contacts) {
            sendSMS(contact.phone, locationUrl)
        }

        // Make call to the first contact
        makePhoneCall(contacts[0].phone)

        voiceEngine.speak("SOS sent to $contactNames. Help is on the way.")
        return true
    }

    fun cancelSOS() {
        if (isSOSActive) {
            isSOSActive = false
            vibrator.cancel()
            voiceEngine.speak("SOS cancelled")
        }
    }

    private fun vibrateSOS() {
        val pattern = longArrayOf(0, 200, 200, 200, 200, 200, 400, 600, 200, 600, 200, 600, 400, 200, 200, 200, 200, 200)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun sendSMS(phoneNumber: String, locationUrl: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java) ?: throw Exception("SMS Manager not found")
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                
                val message = "EMERGENCY ALERT! I need help. \nLocation: $locationUrl \nSent from Smart Glasses App"
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                Log.d(TAG, "SMS sent to $phoneNumber")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS", e)
                voiceEngine.speak("Could not send SOS SMS, calling emergency contact")
            }
        } else {
            Log.e(TAG, "SMS permission not granted")
        }
    }

    private fun makePhoneCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            Log.e(TAG, "Call permission not granted")
        }
    }

    private fun loadContacts(): List<EmergencyContact> {
        val prefs = context.getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("emergency_contacts", null)
        return if (json != null) {
            val type = object : TypeToken<List<EmergencyContact>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyList()
        }
    }

    companion object {
        private const val TAG = "SOSService"
    }
}
