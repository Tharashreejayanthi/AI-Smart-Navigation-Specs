package com.innovista.smartglasses.services

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage

/**
 * MLOrchestrator coordinates multiple ML services to run on a single image stream.
 */
class MLOrchestrator(
    private val objectDetectionService: ObjectDetectionService,
    private val crowdDetectionService: CrowdDetectionService
) : ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            // Run Object Detection
            val objTask = objectDetectionService.processImage(image, imageProxy.width, imageProxy.height)
            
            // Run Crowd Detection
            val crowdTask = crowdDetectionService.processImage(image)

            // Since we're running sequentially or overlapping on the same image, 
            // we close the imageProxy when all relevant tasks are done.
            // For simplicity in this demo, we'll wait for both.
            
            val totalTasks = 2
            var completedTasks = 0

            fun checkCompletion() {
                completedTasks++
                if (completedTasks >= totalTasks) {
                    imageProxy.close()
                }
            }

            objTask?.addOnCompleteListener { checkCompletion() } ?: checkCompletion()
            crowdTask?.addOnCompleteListener { checkCompletion() } ?: checkCompletion()

        } else {
            imageProxy.close()
        }
    }
}
