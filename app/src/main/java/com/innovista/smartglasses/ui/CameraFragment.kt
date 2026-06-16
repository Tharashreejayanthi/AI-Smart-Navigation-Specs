package com.innovista.smartglasses.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.innovista.smartglasses.databinding.FragmentCameraBinding
import com.innovista.smartglasses.services.*
import androidx.camera.camera2.interop.ExperimentalCamera2Interop

/**
 * CameraFragment displays the camera feed and handles object detection, crowd detection, and scene description.
 */
@ExperimentalCamera2Interop
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraService: CameraService
    private lateinit var objectDetectionService: ObjectDetectionService
    private lateinit var crowdDetectionService: CrowdDetectionService
    private lateinit var sceneDescriptionService: SceneDescriptionService
    private lateinit var dangerPredictionService: DangerPredictionService
    private lateinit var nightVisionManager: NightVisionManager
    private lateinit var voiceEngine: VoiceEngine
    private lateinit var mlOrchestrator: MLOrchestrator

    private var shouldCaptureOnStart = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = requireActivity() as? MainActivity
        voiceEngine = VoiceEngine.getInstance(requireContext())
        cameraService = CameraService(requireContext(), viewLifecycleOwner)
        objectDetectionService = ObjectDetectionService(voiceEngine)
        
        crowdDetectionService = mainActivity?.getCrowdDetectionService() ?: CrowdDetectionService(voiceEngine)
        sceneDescriptionService = SceneDescriptionService(voiceEngine, requireContext())
        dangerPredictionService = mainActivity?.getDangerPredictionService() ?: DangerPredictionService(voiceEngine, crowdDetectionService)
        nightVisionManager = mainActivity?.getNightVisionManager() ?: NightVisionManager(requireContext(), voiceEngine)
        
        mlOrchestrator = MLOrchestrator(objectDetectionService, crowdDetectionService)

        setupCamera()
        setupNightVision()
    }

    private fun setupNightVision() {
        // NightVisionManager is activated in MainActivity
        nightVisionManager.currentVisionMode.observe(viewLifecycleOwner) { mode ->
            cameraService.setVisionMode(mode)
        }
    }

    fun setVisionMode(mode: VisionMode) {
        nightVisionManager.setManualVisionMode(mode)
    }

    override fun onStart() {
        super.onStart()
        arguments?.let {
            if (it.getBoolean(ARG_CAPTURE_SCENE, false)) {
                shouldCaptureOnStart = true
            }
        }
    }

    private fun setupCamera() {
        cameraService.startCamera(binding.previewView)
        
        objectDetectionService.start(object : ObjectDetectionService.Callback {
            override fun onObjectsDetected(labels: List<String>) {
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        binding.tvDetectionStatus.text = if (labels.isEmpty()) {
                            "Scanning..."
                        } else {
                            "Detected: ${labels.joinToString(", ")}"
                        }
                    }
                }
            }
        })

        crowdDetectionService.start()
        dangerPredictionService.start()
        cameraService.setAnalyzer(mlOrchestrator)
        
        if (shouldCaptureOnStart) {
            binding.root.postDelayed({
                captureAndDescribe()
                shouldCaptureOnStart = false
            }, 1000)
        }
    }

    fun getImageAnalysis() = cameraService.getImageAnalysis()

    fun captureAndDescribe() {
        val imageCapture = cameraService.getImageCapture()
        if (imageCapture != null) {
            sceneDescriptionService.captureAndDescribe(imageCapture)
        } else {
            voiceEngine.speak("Camera not ready for capture")
        }
    }

    override fun onStop() {
        super.onStop()
        cameraService.stopCamera()
        objectDetectionService.stop()
        crowdDetectionService.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CameraFragment"
        private const val ARG_CAPTURE_SCENE = "capture_scene"

        fun newInstance(captureScene: Boolean = false): CameraFragment {
            val fragment = CameraFragment()
            val args = Bundle()
            args.putBoolean(ARG_CAPTURE_SCENE, captureScene)
            fragment.arguments = args
            return fragment
        }
    }
}
