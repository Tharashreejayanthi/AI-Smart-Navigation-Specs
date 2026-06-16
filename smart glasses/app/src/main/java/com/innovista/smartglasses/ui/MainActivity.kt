package com.innovista.smartglasses.ui

import android.os.Bundle
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.innovista.smartglasses.R
import com.innovista.smartglasses.databinding.ActivityMainBinding
import com.innovista.smartglasses.services.VoiceCommand
import com.innovista.smartglasses.services.VoiceEngine
import com.innovista.smartglasses.services.VoiceEngineCallback
import com.innovista.smartglasses.utils.PermissionManager

class MainActivity : AppCompatActivity(), VoiceEngineCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceEngine: VoiceEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        voiceEngine = VoiceEngine.getInstance(this)

        setupListeners()
        checkPermissions()
    }

    private fun setupListeners() {
        binding.btnMic.setOnClickListener {
            startVoiceControl()
        }
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

    private fun onPermissionsGranted() {
        voiceEngine.speak("Smart Glasses ready. Say a command.")
        startVoiceControl()
    }

    private fun startVoiceControl() {
        binding.tvStatus.text = "Listening..."
        startPulseAnimation()
        voiceEngine.startListening(this)
    }

    private fun startPulseAnimation() {
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_glow)
        binding.micPulse.startAnimation(pulse)
        binding.micPulse.alpha = 1f
    }

    private fun stopPulseAnimation() {
        binding.micPulse.clearAnimation()
        binding.micPulse.alpha = 0f
    }

    override fun onCommandReceived(text: String, command: VoiceCommand) {
        binding.tvLastCommand.text = "Last command: $text"
        
        when (command) {
            VoiceCommand.DESCRIBE_SCENE -> {
                binding.tvStatus.text = "Describing scene..."
                voiceEngine.speakInterrupt("Scene description coming in Day 2")
            }
            VoiceCommand.NAVIGATE -> {
                binding.tvStatus.text = "Navigation loading..."
                voiceEngine.speakInterrupt("Navigation coming in Day 2")
            }
            VoiceCommand.FIND_OBJECT -> {
                binding.tvStatus.text = "Searching..."
                voiceEngine.speakInterrupt("Object finder coming in Day 2")
            }
            VoiceCommand.SOS -> {
                binding.tvStatus.text = "SOS ALERT!"
                voiceEngine.speakInterrupt("SOS coming in Day 4")
            }
            VoiceCommand.WHERE_AM_I -> {
                binding.tvStatus.text = "Finding location..."
                voiceEngine.speakInterrupt("Navigation coming in Day 3")
            }
            VoiceCommand.STOP -> {
                binding.tvStatus.text = "Stopped"
                voiceEngine.speakInterrupt("Stopped")
                voiceEngine.stopListening()
                stopPulseAnimation()
            }
            VoiceCommand.UNKNOWN -> {
                voiceEngine.speak("Command not recognized, please try again")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceEngine.shutdown()
    }
}
