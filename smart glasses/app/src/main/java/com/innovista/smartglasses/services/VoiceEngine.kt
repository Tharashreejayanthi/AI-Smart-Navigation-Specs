package com.innovista.smartglasses.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

enum class VoiceCommand {
    NAVIGATE, DESCRIBE_SCENE, FIND_OBJECT, SOS, WHERE_AM_I, STOP, UNKNOWN
}

interface VoiceEngineCallback {
    fun onCommandReceived(text: String, command: VoiceCommand)
}

class VoiceEngine private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var callback: VoiceEngineCallback? = null
    private var isListening = false

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
        tts = TextToSpeech(context, this)
        setupSpeechRecognizer()
    }

    // --- TTS Logic ---

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let {
                it.language = Locale.ENGLISH
                it.setSpeechRate(0.85f)
            }
        } else {
            Log.e(TAG, "TTS Initialization failed")
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    fun speakInterrupt(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
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
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
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
            lowerText.contains("sos") || lowerText.contains("help") || lowerText.contains("emergency") -> VoiceCommand.SOS
            lowerText.contains("where am i") || lowerText.contains("location") -> VoiceCommand.WHERE_AM_I
            lowerText.contains("stop") || lowerText.contains("cancel") -> VoiceCommand.STOP
            else -> VoiceCommand.UNKNOWN
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        INSTANCE = null
    }
}
