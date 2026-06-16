package com.innovista.smartglasses.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.innovista.smartglasses.R
import com.innovista.smartglasses.databinding.FragmentSettingsBinding
import com.innovista.smartglasses.hardware.HardwareBridgeFactory
import com.innovista.smartglasses.services.VoiceEngine
import com.innovista.smartglasses.services.VoiceCommand
import androidx.camera.camera2.interop.ExperimentalCamera2Interop

@ExperimentalCamera2Interop
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var voiceEngine: VoiceEngine

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        voiceEngine = VoiceEngine.getInstance(requireContext())

        setupVoiceSettings()
        setupEmergencySettings()
        setupHardwareSettings()
        setupAISettings()
    }

    private fun setupVoiceSettings() {
        val prefs = requireContext().getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
        
        val rate = prefs.getFloat("speech_rate", 0.85f)
        binding.sliderSpeechRate.value = rate
        
        binding.sliderSpeechRate.addOnChangeListener { _, value, _ ->
            prefs.edit().putFloat("speech_rate", value).apply()
            voiceEngine.setSpeechRate(value)
        }

        val volume = prefs.getFloat("speech_volume", 100.0f)
        binding.sliderSpeechVolume.value = volume

        binding.sliderSpeechVolume.addOnChangeListener { _, value, _ ->
            prefs.edit().putFloat("speech_volume", value).apply()
        }

        binding.btnTestVoice.setOnClickListener {
            voiceEngine.speak("Smart Glasses test voice")
        }
    }

    private fun setupEmergencySettings() {
        binding.btnSetupContacts.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SOSSetupFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnTestSOS.setOnClickListener {
            voiceEngine.speak("This is a test SOS from Smart Glasses")
            // In real app, we'd trigger a test SMS via SOSService
        }
    }

    private fun setupHardwareSettings() {
        val prefs = requireContext().getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
        
        val type = prefs.getString("hardware_connection_type", "bluetooth")
        if (type == "wifi") {
            binding.toggleHardwareType.check(R.id.btnWifi)
            binding.etHardwareIp.visibility = View.VISIBLE
        } else {
            binding.toggleHardwareType.check(R.id.btnBluetooth)
            binding.etHardwareIp.visibility = View.GONE
        }

        binding.toggleHardwareType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (checkedId == R.id.btnWifi && isChecked) {
                prefs.edit().putString("hardware_connection_type", "wifi").apply()
                binding.etHardwareIp.visibility = View.VISIBLE
            } else if (checkedId == R.id.btnBluetooth && isChecked) {
                prefs.edit().putString("hardware_connection_type", "bluetooth").apply()
                binding.etHardwareIp.visibility = View.GONE
            }
        }

        binding.etHardwareIp.setText(prefs.getString("hardware_ip", "192.168.1.100"))
        binding.etHardwareIp.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                prefs.edit().putString("hardware_ip", binding.etHardwareIp.text.toString()).apply()
            }
        }

        binding.btnConnectHardware.setOnClickListener {
            val bridge = HardwareBridgeFactory.getPreferredBridge(requireContext(), voiceEngine)
            bridge.connect()
            
            bridge.isConnected().observe(viewLifecycleOwner) { connected ->
                binding.hardwareStatusIndicator.setBackgroundColor(
                    if (connected) android.graphics.Color.GREEN else android.graphics.Color.RED
                )
            }
        }
    }

    private fun setupAISettings() {
        val prefs = requireContext().getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
        val offlineManager = (requireActivity() as? MainActivity)?.getOfflineModeManager()

        binding.btnDownloadModels.setOnClickListener {
            offlineManager?.downloadModelsForOffline()
        }

        offlineManager?.isOnline?.observe(viewLifecycleOwner) { isOnline ->
            binding.tvModelStatus.text = if (isOnline) "Status: Online (Models Ready)" else "Status: Offline"
        }

        binding.switchCrowdDetection.isChecked = prefs.getBoolean("crowd_detection_enabled", true)
        binding.switchCrowdDetection.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("crowd_detection_enabled", isChecked).apply()
        }

        binding.switchDangerPrediction.isChecked = prefs.getBoolean("danger_prediction_enabled", true)
        binding.switchDangerPrediction.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("danger_prediction_enabled", isChecked).apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsFragment"
    }
}
