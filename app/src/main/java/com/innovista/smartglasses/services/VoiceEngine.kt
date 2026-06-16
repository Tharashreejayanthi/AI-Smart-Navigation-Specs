package com.innovista.smartglasses.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

enum class VoiceCommand {
    NAVIGATE, DESCRIBE_SCENE, FIND_OBJECT, SOS, SOS_SETUP, NIGHT_MODE, DAY_MODE, DANGER_LEVEL, SETTINGS, WHERE_AM_I, STOP, UNKNOWN
}

interface VoiceEngineCallback {
    fun onCommandReceived(text: String, command: VoiceCommand)
}

class VoiceEngine private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var callback: VoiceEngineCallback? = null
    private var isListening = false
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (isListening && speechRecognizer == null) {
                Log.w(TAG, "Watchdog: STT stopped unexpectedly, restarting...")
                setupSpeechRecognizer()
                startListening(callback)
            }
            watchdogHandler.postDelayed(this, 5000)
        }
    }

    companion object {
        private const val TAG = "VoiceEngine"
        @Volatile
        private var INSTANCE: VoiceEngine? = null

        fun getInstance(context: Context): VoiceEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        initTTS()
        setupSpeechRecognizer()
        watchdogHandler.post(watchdogRunnable)
    }

    private fun initTTS() {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "TTS Init Exception", e)
        }
    }

    // --- TTS Logic ---

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let {
                it.language = Locale.ENGLISH
                val prefs = context.getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
                val rate = prefs.getFloat("speech_rate", 0.85f)
                it.setSpeechRate(rate)
            }
        } else {
            Log.e(TAG, "TTS Initialization failed")
            // Fallback
            Handler(Looper.getMainLooper()).postDelayed({
                initTTS()
            }, 3000)
        }
    }

    fun speak(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Speak error", e)
        }
    }

    fun speakInterrupt(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "SpeakInterrupt error", e)
        }
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun vibrate(duration: Long) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    // --- STT Logic ---

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error"
                    }
                    Log.e(TAG, "STT Error: $message ($error)")
                    
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        // speak("Could not hear you, please try again")
                    }

                    // Auto-restart if we are supposed to be listening
                    if (isListening) {
                        startListening(callback)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        val command = parseCommand(text)
                        
                        if (command != VoiceCommand.UNKNOWN) {
                            vibrate(100) // Haptic feedback on success
                        }
                        
                        callback?.onCommandReceived(text, command)
                    }
                    
                    // Auto-restart
                    if (isListening) {
                        startListening(callback)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening(callback: VoiceEngineCallback?) {
        this.callback = callback
        this.isListening = true
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Start listening error", e)
            setupSpeechRecognizer()
        }
    }

    fun stopListening() {
        this.isListening = false
        speechRecognizer?.stopListening()
    }

    // --- Command Router ---

    fun parseCommand(text: String): VoiceCommand {
        val lowerText = text.lowercase()
        return when {
            lowerText.contains("navigate") || lowerText.contains("go to") -> VoiceCommand.NAVIGATE
            lowerText.contains("describe") || lowerText.contains("what do you see") -> VoiceCommand.DESCRIBE_SCENE
            lowerText.contains("find") || lowerText.contains("search") -> VoiceCommand.FIND_OBJECT
            lowerText.contains("night mode") -> VoiceCommand.NIGHT_MODE
            lowerText.contains("day mode") -> VoiceCommand.DAY_MODE
            lowerText.contains("danger") || lowerText.contains("how safe") -> VoiceCommand.DANGER_LEVEL
            lowerText.contains("settings") || lowerText.contains("open settings") -> VoiceCommand.SETTINGS
            lowerText.contains("sos setup") || lowerText.contains("setup emergency") || lowerText.contains("setup contacts") || lowerText.contains("emergency contacts") -> VoiceCommand.SOS_SETUP
            lowerText.contains("sos") || lowerText.contains("help") || lowerText.contains("emergency") -> VoiceCommand.SOS
            lowerText.contains("where am i") || lowerText.contains("location") -> VoiceCommand.WHERE_AM_I
            lowerText.contains("stop") || lowerText.contains("cancel") -> VoiceCommand.STOP
            else -> VoiceCommand.UNKNOWN
        }
    }

    fun shutdown() {
        watchdogHandler.removeCallbacks(watchdogRunnable)
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        INSTANCE = null
    }
}

