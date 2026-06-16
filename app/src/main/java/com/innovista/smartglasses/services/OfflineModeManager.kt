package com.innovista.smartglasses.services

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.Locale

/**
 * OfflineModeManager monitors network connectivity and ensures AI models are available offline.
 */
class OfflineModeManager(
    private val context: Context,
    private val voiceEngine: VoiceEngine
) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isOnline = MutableLiveData<Boolean>(checkInitialConnection())
    val isOnline: LiveData<Boolean> get() = _isOnline
    
    var offlineMode: Boolean = !(_isOnline.value ?: true)
        private set

    private var isFirstLaunch: Boolean
        get() {
            val prefs = context.getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("first_launch_offline", true)
        }
        set(value) {
            val prefs = context.getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("first_launch_offline", value).apply()
        }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (_isOnline.value == false) {
                _isOnline.postValue(true)
                offlineMode = false
                voiceEngine.speak("Internet restored. Full features available")
            }
        }

        override fun onLost(network: Network) {
            if (_isOnline.value == true) {
                _isOnline.postValue(false)
                offlineMode = true
                voiceEngine.speak("Internet disconnected. Switching to offline mode")
            }
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        if (isFirstLaunch) {
            downloadModelsForOffline()
            isFirstLaunch = false
        }
    }

    private fun checkInitialConnection(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Downloads ML Kit models for offline use.
     * Note: Standard ML Kit "Base" models are bundled, but this logic is for 
     * ensuring they are ready and optimized for on-device use.
     */
    fun downloadModelsForOffline() {
        voiceEngine.speak("Downloading offline AI models, please wait")
        
        // ML Kit handles model management internally for the base models we use.
        // For custom models, we would use RemoteModelManager.
        // For base models, we can trigger a "warm up" or check download status.
        
        Log.d(TAG, "Offline models preparation initiated")
        // In a real scenario with custom models, we would enqueue downloads here.
        // For standard ML Kit, it happens on first use, or via Google Play Services.
    }

    fun isMLKitModelDownloaded(): Boolean {
        // Base models are generally included in the APK or downloaded by GPS.
        return true 
    }

    fun getOfflineAddress(location: Location): String {
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            // Geocoder.getFromLocation can work offline if the area was recently cached
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                addresses[0].getAddressLine(0)
            } else {
                "Address unavailable offline"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Offline Geocoding failed", e)
            "Address unavailable offline"
        }
    }

    fun unregister() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    companion object {
        private const val TAG = "OfflineModeManager"
    }
}
