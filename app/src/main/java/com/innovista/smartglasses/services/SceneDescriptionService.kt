package com.innovista.smartglasses.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.nio.ByteBuffer

/**
 * SceneDescriptionService uses ML Kit Image Labeling to describe the surroundings.
 */
class SceneDescriptionService(
    private val voiceEngine: VoiceEngine,
    private val context: Context
) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.65f)
            .build()
    )

    /**
     * Analyzes the bitmap and speaks a natural language description of the scene.
     */
    fun describeScene(imageBitmap: Bitmap) {
        val image = InputImage.fromBitmap(imageBitmap, 0)

        labeler.process(image)
            .addOnSuccessListener { labels ->
                if (labels.isEmpty()) {
                    voiceEngine.speak("I cannot clearly identify the surroundings")
                    return@addOnSuccessListener
                }

                // Get top 5 labels
                val topLabels = labels.take(5).map { it.text.lowercase() }
                
                val sentence = when (topLabels.size) {
                    1 -> "I can see a ${topLabels[0]} nearby."
                    else -> {
                        val allButLast = topLabels.dropLast(1).joinToString(", ")
                        val last = topLabels.last()
                        "I can see a $allButLast, and a $last nearby."
                    }
                }

                Log.d(TAG, "Scene Description: $sentence")
                voiceEngine.speak(sentence)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Image labeling failed", e)
                voiceEngine.speak("Sorry, I encountered an error while identifying the scene.")
            }
    }

    /**
     * Captures a snapshot using ImageCapture and describes it.
     */
    fun captureAndDescribe(imageCapture: ImageCapture) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    if (bitmap != null) {
                        describeScene(bitmap)
                    } else {
                        voiceEngine.speak("Failed to process image")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed", exception)
                    voiceEngine.speak("Failed to capture image")
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    companion object {
        private const val TAG = "SceneDescriptionService"
    }
}
