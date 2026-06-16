package com.innovista.smartglasses.services

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.annotations.SerializedName
import com.innovista.smartglasses.ui.MainActivity
import org.osmdroid.util.GeoPoint
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.Locale

// --- Data Classes for OSRM API ---

data class OSRMResponse(
    @SerializedName("routes") val routes: List<OSRMRoute>,
    @SerializedName("code") val code: String
)

data class OSRMRoute(
    @SerializedName("legs") val legs: List<OSRMLeg>,
    @SerializedName("geometry") val geometry: OSRMGeometry?
)

data class OSRMGeometry(
    @SerializedName("coordinates") val coordinates: List<List<Double>>
)

data class OSRMLeg(
    @SerializedName("steps") val steps: List<OSRMStep>
)

data class OSRMStep(
    @SerializedName("maneuver") val maneuver: OSRMManeuver,
    @SerializedName("name") val name: String
)

data class OSRMManeuver(
    @SerializedName("instruction") val instruction: String,
    @SerializedName("location") val location: List<Double>
)

data class NavigationStep(
    val instruction: String,
    val endLocation: GeoPoint
)

// --- Retrofit Interface ---

interface DirectionsApi {
    @GET("route/v1/foot/{coordinates}")
    fun getDirections(
        @Path("coordinates") coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: Boolean = true
    ): Call<OSRMResponse>
}

// --- Navigation Service ---

class NavigationService(
    context: Context,
    private val voiceEngine: VoiceEngine,
    private val locationService: LocationService
) {

    private val context = context.applicationContext
    private var navigationSteps = mutableListOf<NavigationStep>()
    private var currentStepIndex = 0
    private var isNavigating = false
    private var isOfflineNavigation = false
    private var destinationPoint: GeoPoint? = null
    private var lastOfflineUpdate = 0L

    private val _routePoints = MutableLiveData<List<GeoPoint>>()
    val routePoints: LiveData<List<GeoPoint>> get() = _routePoints

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://router.project-osrm.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val directionsApi = retrofit.create(DirectionsApi::class.java)

    /**
     * Starts navigation to a text destination.
     */
    @ExperimentalCamera2Interop
    fun startNavigation(destination: String) {
        voiceEngine.speak("Finding route to $destination")
        
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(destination, 1)
            if (addresses.isNullOrEmpty()) {
                voiceEngine.speak("Could not find destination $destination")
                return
            }
            
            val destPoint = GeoPoint(addresses[0].latitude, addresses[0].longitude)
            this.destinationPoint = destPoint
            
            val currentLoc = LocationService.currentLocation.value
            if (currentLoc == null) {
                voiceEngine.speak("Waiting for GPS signal")
                return
            }

            val isOnline = (context as? MainActivity)?.getOfflineModeManager()?.offlineMode == false
            
            if (isOnline) {
                // OSRM format: lon,lat;lon,lat
                val coordinates = "${currentLoc.longitude},${currentLoc.latitude};${destPoint.longitude},${destPoint.latitude}"

                directionsApi.getDirections(coordinates)
                    .enqueue(object : Callback<OSRMResponse> {
                        override fun onResponse(call: Call<OSRMResponse>, response: Response<OSRMResponse>) {
                            if (response.isSuccessful && response.body()?.code == "Ok") {
                                isOfflineNavigation = false
                                parseDirections(response.body()!!)
                            } else {
                                Log.e(TAG, "OSRM API Error: ${response.body()?.code}")
                                startOfflineNavigation()
                            }
                        }

                        override fun onFailure(call: Call<OSRMResponse>, t: Throwable) {
                            Log.e(TAG, "OSRM API Failure", t)
                            startOfflineNavigation()
                        }
                    })
            } else {
                startOfflineNavigation()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed", e)
            startOfflineNavigation()
        }
    }

    private fun startOfflineNavigation() {
        isOfflineNavigation = true
        isNavigating = true
        voiceEngine.speak("Offline mode: Basic navigation only")
        
        val currentLoc = LocationService.currentLocation.value
        val dest = destinationPoint
        if (currentLoc != null && dest != null) {
            _routePoints.postValue(listOf(
                GeoPoint(currentLoc.latitude, currentLoc.longitude),
                dest
            ))
            speakOfflineGuidance(currentLoc)
        }
    }

    private fun speakOfflineGuidance(currentLocation: Location) {
        val dest = destinationPoint ?: return
        val destLoc = Location("").apply {
            latitude = dest.latitude
            longitude = dest.longitude
        }
        
        val distance = currentLocation.distanceTo(destLoc).toInt()
        val bearing = currentLocation.bearingTo(destLoc)
        
        val direction = when {
            bearing >= -45 && bearing < 45 -> "Head North"
            bearing >= 45 && bearing < 135 -> "Head East"
            bearing >= 135 || bearing < -135 -> "Head South"
            else -> "Head West"
        }
        
        voiceEngine.speak("$direction. Approximately $distance meters to destination.")
        lastOfflineUpdate = System.currentTimeMillis()
    }

    private fun parseDirections(response: OSRMResponse) {
        navigationSteps.clear()
        val route = response.routes.firstOrNull() ?: return
        val steps = route.legs.firstOrNull()?.steps ?: return
        
        for (step in steps) {
            navigationSteps.add(NavigationStep(
                step.maneuver.instruction,
                GeoPoint(step.maneuver.location[1], step.maneuver.location[0])
            ))
        }

        // Geometry for the polyline
        val fullGeometry = route.geometry?.coordinates?.map { GeoPoint(it[1], it[0]) } ?: emptyList()
        _routePoints.postValue(fullGeometry)

        if (navigationSteps.isNotEmpty()) {
            currentStepIndex = 0
            isNavigating = true
            voiceEngine.speak("Route found. ${navigationSteps[0].instruction}")
        }
    }

    /**
     * Checks progress against the current route.
     */
    fun checkProgress(currentLocation: Location) {
        if (!isNavigating) return

        if (isOfflineNavigation) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastOfflineUpdate > 10000) {
                speakOfflineGuidance(currentLocation)
            }
            
            val dest = destinationPoint ?: return
            val destLoc = Location("").apply {
                latitude = dest.latitude
                longitude = dest.longitude
            }
            if (currentLocation.distanceTo(destLoc) < 15) {
                voiceEngine.speak("You have arrived at your destination")
                stopNavigation()
            }
            return
        }

        if (currentStepIndex >= navigationSteps.size) return

        val nextStep = navigationSteps[currentStepIndex]
        val dest = Location("").apply {
            latitude = nextStep.endLocation.latitude
            longitude = nextStep.endLocation.longitude
        }

        val distance = currentLocation.distanceTo(dest)
        
        if (distance < 15) {
            currentStepIndex++
            if (currentStepIndex < navigationSteps.size) {
                voiceEngine.speak(navigationSteps[currentStepIndex].instruction)
            } else {
                voiceEngine.speak("You have arrived at your destination")
                stopNavigation()
            }
        }
    }

    /**
     * Stops navigation.
     */
    fun stopNavigation() {
        isNavigating = false
        navigationSteps.clear()
        _routePoints.postValue(emptyList())
        currentStepIndex = 0
        voiceEngine.speak("Navigation stopped")
    }

    companion object {
        private const val TAG = "NavigationService"
    }
}
