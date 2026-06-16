package com.innovista.smartglasses.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.innovista.smartglasses.databinding.ActivitySplashBinding
import com.innovista.smartglasses.services.VoiceEngine
import com.innovista.smartglasses.R

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var voiceEngine: VoiceEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // No action bar
        supportActionBar?.hide()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start glow animation
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_glow)
        binding.glowCircle.startAnimation(pulse)

        // Initialize VoiceEngine and speak
        voiceEngine = VoiceEngine.getInstance(this)
        
        // Small delay to ensure TTS is ready
        Handler(Looper.getMainLooper()).postDelayed({
            voiceEngine.speak("Welcome to Smart Glasses")
        }, 500)

        // Navigate to MainActivity after 2.5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2500)
    }
}
