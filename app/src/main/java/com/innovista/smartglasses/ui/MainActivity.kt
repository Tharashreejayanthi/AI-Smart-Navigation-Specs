package com.innovista.smartglasses.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.innovista.smartglasses.R
import com.innovista.smartglasses.databinding.ActivityMainBinding
import com.innovista.smartglasses.services.*
import com.innovista.smartglasses.ui.SOSSetupFragment
import com.innovista.smartglasses.ui.SettingsFragment
import com.innovista.smartglasses.ui.CameraFragment
import com.innovista.smartglasses.ui.MapFragment
import com.innovista.smartglasses.ui.HomeFragment
import com.innovista.smartglasses.utils.PermissionManager
import androidx.camera.camera2.interop.ExperimentalCamera2Interop

@ExperimentalCamera2Interop
class MainActivity : AppCompatActivity(), VoiceEngineCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var objectFinderService: ObjectFinderService
    private lateinit var navigationService: NavigationService
    private lateinit var sosService: SOSService
    private lateinit var nightVisionManager: NightVisionManager
    private lateinit var crowdDetectionService: CrowdDetectionService
    private lateinit var dangerPredictionService: DangerPredictionService
    private lateinit var offlineModeManager: OfflineModeManager
    
    private val _lastCommandText = MutableLiveData<String>("None")
    val lastCommandText: LiveData<String> get() = _lastCommandText

    private var locationService: LocationService? = null
    private var isLocationBound = false
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    // Triple tap detection
    private var tapCount = 0
    private var lastTapTime = 0L

    private val locationServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            val ls = binder.getService()
            locationService = ls
            isLocationBound = true
            
            // Initialize navigation service when location service is ready
            navigationService = NavigationService(this@MainActivity, voiceEngine, ls)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            isLocationBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        voiceEngine = VoiceEngine.getInstance(this)
        objectFinderService = ObjectFinderService(voiceEngine)
        sosService = SOSService(this, voiceEngine)
        nightVisionManager = NightVisionManager(this, voiceEngine)
        crowdDetectionService = CrowdDetectionService(voiceEngine)
        dangerPredictionService = DangerPredictionService(voiceEngine, crowdDetectionService)
        offlineModeManager = OfflineModeManager(this, voiceEngine)

        setupListeners()
        observeDangerLevel()
        acquireWakeLock()
        checkPermissions()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SmartGlasses::KeepAlive")
        wakeLock?.acquire()
    }

    private fun observeDangerLevel() {
        dangerPredictionService.dangerLevel.observe(this) { level ->
            when (level) {
                DangerLevel.DANGER -> flashScreen(android.graphics.Color.RED)
                DangerLevel.CAUTION -> flashScreen(android.graphics.Color.YELLOW)
                else -> {}
            }
        }
    }

    private fun flashScreen(color: Int) {
        val originalBackground = binding.root.background
        binding.root.setBackgroundColor(color)
        binding.root.postDelayed({
            binding.root.background = originalBackground
        }, 500)
    }

    override fun onStart() {
        super.onStart()
        nightVisionManager.activate()
        dangerPredictionService.start()
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, locationServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        nightVisionManager.deactivate()
        dangerPredictionService.stop()
        if (isLocationBound) {
            unbindService(locationServiceConnection)
            isLocationBound = false
        }
    }

    private fun startLocationTracking() {
        // Start foreground service
        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)
    }

    private fun announceCurrentLocation() {
        LocationService.currentLocation.observe(this) { location ->
            if (location == null) {
                voiceEngine.speak("Location unavailable, check GPS settings")
                LocationService.currentLocation.removeObservers(this)
                return@observe
            }
            
            val isOnline = offlineModeManager.isOnline.value ?: true
            val address = if (isOnline) {
                locationService?.getAddressFromLocation(location)
            } else {
                offlineModeManager.getOfflineAddress(location)
            } ?: "Finding your address..."

            if (address != "Address not found" && address != "Finding your address..." && address != "Address unavailable offline") {
                voiceEngine.speakInterrupt("You are currently at $address")
                // Stop updates after one successful announcement to avoid spamming
                LocationService.currentLocation.removeObservers(this)
            }
        }
    }

    private fun setupListeners() {
        binding.btnMic.setOnClickListener {
            startVoiceControl()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 1000) {
                tapCount++
            } else {
                tapCount = 1
            }
            lastTapTime = currentTime

            if (tapCount >= 3) {
                tapCount = 0
                voiceEngine.speak("SOS triggered by tap")
                onCommandReceived("sos", VoiceCommand.SOS)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun checkPermissions() {
        PermissionManager.checkAndRequestAll(this,
            onAllGranted = {
                onPermissionsGranted()
            },
            onDenied = { permission ->
                binding.tvStatus.text = "Permission Denied: $permission"
                voiceEngine.speak("Permissions are required to use Smart Glasses")
            }
        )
    }

    fun getLocationService() = locationService
    fun getNavigationService(): NavigationService? = if (::navigationService.isInitialized) navigationService else null
    fun getNightVisionManager() = nightVisionManager
    fun getCrowdDetectionService() = crowdDetectionService
    fun getDangerPredictionService() = dangerPredictionService
    fun getOfflineModeManager() = offlineModeManager
    fun getVoiceEngine() = voiceEngine

    private fun onPermissionsGranted() {
        voiceEngine.speak("Smart Glasses ready. Say a command.")
        // App startup vibration
        val v = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            v.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(300)
        }

        showHomeFragment()
        startLocationTracking() // Start background location tracking on launch
        startVoiceControl()
    }

    private fun showHomeFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HomeFragment(), HomeFragment.TAG)
            .commit()
    }

    private fun startVoiceControl() {
        binding.tvStatus.text = "Listening..."
        startPulseAnimation()
        voiceEngine.startListening(this)
    }

    private fun startPulseAnimation() {
        val pulse = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse_glow)
        binding.micPulse.startAnimation(pulse)
        binding.micPulse.alpha = 1f
    }

    private fun stopPulseAnimation() {
        binding.micPulse.clearAnimation()
        binding.micPulse.alpha = 0f
    }

    override fun onCommandReceived(text: String, command: VoiceCommand) {
        _lastCommandText.postValue(text)
        binding.tvLastCommand.text = "Last command: $text"
        binding.tvStatus.text = "Processing..."
        
        when (command) {
            VoiceCommand.DESCRIBE_SCENE -> {
                binding.tvStatus.text = "Describing scene..."
                showCameraFragment(captureScene = true)
            }
            VoiceCommand.NAVIGATE -> {
                val destination = extractDestination(text)
                binding.tvStatus.text = "Navigating to $destination..."
                showMapFragment()
                startLocationTracking() // Ensure GPS is active
                if (::navigationService.isInitialized) {
                    navigationService.startNavigation(destination)
                }
            }
            VoiceCommand.FIND_OBJECT -> {
                val target = extractTargetObject(text)
                binding.tvStatus.text = "Finding $target..."
                startObjectSearch(target)
            }
            VoiceCommand.SOS -> {
                binding.tvStatus.text = "SOS ALERT!"
                val location = LocationService.currentLocation.value
                val success = sosService.triggerSOS(location)
                if (!success) {
                    showSOSSetupFragment()
                }
            }
            VoiceCommand.SOS_SETUP -> {
                binding.tvStatus.text = "Opening Setup..."
                showSOSSetupFragment()
            }
            VoiceCommand.NIGHT_MODE -> {
                binding.tvStatus.text = "Activating Night Vision..."
                voiceEngine.speak("Night vision activated")
                setVisionMode(VisionMode.NIGHT)
            }
            VoiceCommand.DAY_MODE -> {
                binding.tvStatus.text = "Activating Day Vision..."
                voiceEngine.speak("Day vision activated")
                setVisionMode(VisionMode.DAY)
            }
            VoiceCommand.DANGER_LEVEL -> {
                binding.tvStatus.text = "Analyzing Danger..."
                val level = dangerPredictionService.dangerLevel.value ?: DangerLevel.SAFE
                voiceEngine.speak("Current danger level is $level")
            }
            VoiceCommand.SETTINGS -> {
                binding.tvStatus.text = "Opening Settings..."
                showSettingsFragment()
            }
            VoiceCommand.WHERE_AM_I -> {
                binding.tvStatus.text = "Finding location..."
                announceCurrentLocation()
            }
            VoiceCommand.STOP -> {
                binding.tvStatus.text = "Stopped"
                
                if (::navigationService.isInitialized && navigationService.routePoints.value?.isNotEmpty() == true) {
                    navigationService.stopNavigation()
                } else {
                    voiceEngine.speakInterrupt("Stopped")
                    voiceEngine.stopListening()
                    stopPulseAnimation()
                }
                
                objectFinderService.stopFinding()
                sosService.cancelSOS()
                
                // Keep location service running in background but hide UI overlays
                supportFragmentManager.findFragmentByTag(CameraFragment.TAG)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
                supportFragmentManager.findFragmentByTag(MapFragment.TAG)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
                supportFragmentManager.findFragmentByTag("SOSSetupFragment")?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
                supportFragmentManager.findFragmentByTag(SettingsFragment.TAG)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
                
                showHomeFragment()
            }
            VoiceCommand.UNKNOWN -> {
                voiceEngine.speak("Command not recognized, please try again")
            }
        }
    }

    private fun showSettingsFragment() {
        val fragment = SettingsFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, SettingsFragment.TAG)
            .addToBackStack(null)
            .commit()
    }

    private fun showMapFragment() {
        val fragment = MapFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, MapFragment.TAG)
            .commit()
    }

    private fun showSOSSetupFragment() {
        val fragment = SOSSetupFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, "SOSSetupFragment")
            .commit()
    }

    private fun showCameraFragment(captureScene: Boolean = false) {
        val fragment = CameraFragment.newInstance(captureScene)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, CameraFragment.TAG)
            .commit()
    }

    private fun startObjectSearch(target: String) {
        // First show camera fragment
        showCameraFragment()
        
        // Wait for fragment to be ready and get its analyzer
        binding.root.postDelayed({
            val fragment = supportFragmentManager.findFragmentByTag(CameraFragment.TAG) as? CameraFragment
            val analyzer = fragment?.getImageAnalysis()
            if (analyzer != null) {
                objectFinderService.findObject(target, analyzer)
            } else {
                voiceEngine.speak("Camera not ready for searching")
            }
        }, 1500)
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun setVisionMode(mode: VisionMode) {
        val fragment = supportFragmentManager.findFragmentByTag(CameraFragment.TAG) as? CameraFragment
        if (fragment != null) {
            fragment.setVisionMode(mode)
        } else {
            // If not in camera fragment, maybe show it first? 
            // Or just speak that camera is not active.
            voiceEngine.speak("Camera is not active")
        }
    }

    private fun extractDestination(text: String): String {
        return text.lowercase()
            .replace("navigate to", "")
            .replace("go to", "")
            .replace("take me to", "")
            .trim()
    }

    private fun extractTargetObject(text: String): String {
        return text.lowercase()
            .replace("find", "")
            .replace("search", "")
            .replace("for", "")
            .replace("the", "")
            .replace("a ", " ")
            .trim()
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
        offlineModeManager.unregister()
        voiceEngine.shutdown()
    }
}
