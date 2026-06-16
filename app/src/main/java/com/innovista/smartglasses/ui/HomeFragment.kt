package com.innovista.smartglasses.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.innovista.smartglasses.R
import com.innovista.smartglasses.databinding.FragmentHomeBinding
import com.innovista.smartglasses.services.DangerLevel
import com.innovista.smartglasses.services.VisionMode
import com.innovista.smartglasses.hardware.HardwareBridgeFactory
import java.text.SimpleDateFormat
import java.util.*

@ExperimentalCamera2Interop
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val handler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startClock()
        observeSystemStatus()
    }

    private fun startClock() {
        handler.post(timeRunnable)
    }

    private fun updateTime() {
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        
        binding.tvTime.text = timeFormat.format(calendar.time)
        binding.tvDate.text = dateFormat.format(calendar.time)
    }

    private fun observeSystemStatus() {
        val mainActivity = requireActivity() as? MainActivity ?: return

        // Observe Danger Level
        mainActivity.getDangerPredictionService().dangerLevel.observe(viewLifecycleOwner) { level ->
            val color = when (level) {
                DangerLevel.SAFE -> android.graphics.Color.GREEN
                DangerLevel.CAUTION -> android.graphics.Color.YELLOW
                DangerLevel.DANGER -> android.graphics.Color.RED
                else -> android.graphics.Color.GRAY
            }
            binding.indicatorDanger.background.setTint(color)
        }

        // Observe Vision Mode
        mainActivity.getNightVisionManager().currentVisionMode.observe(viewLifecycleOwner) { mode ->
            binding.tvVisionMode.text = mode.name
            binding.tvVisionMode.setTextColor(if (mode == VisionMode.NIGHT) android.graphics.Color.CYAN else android.graphics.Color.YELLOW)
        }

        // Observe Last Command
        mainActivity.lastCommandText.observe(viewLifecycleOwner) { text ->
            binding.tvLastCommandHome.text = "Last: $text"
        }

        // Observe Hardware Status
        val bridge = HardwareBridgeFactory.getPreferredBridge(requireContext(), mainActivity.getVoiceEngine())
        bridge.isConnected().observe(viewLifecycleOwner) { connected ->
            binding.tvHardwareStatus.text = if (connected) "CONNECTED" else "DISCONNECTED"
            binding.tvHardwareStatus.setTextColor(if (connected) android.graphics.Color.GREEN else android.graphics.Color.RED)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timeRunnable)
        _binding = null
    }

    companion object {
        const val TAG = "HomeFragment"
    }
}
