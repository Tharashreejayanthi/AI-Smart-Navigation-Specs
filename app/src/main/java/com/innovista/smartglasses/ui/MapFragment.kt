package com.innovista.smartglasses.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.innovista.smartglasses.databinding.FragmentMapBinding
import com.innovista.smartglasses.services.LocationService
import com.innovista.smartglasses.services.NavigationService
import com.innovista.smartglasses.services.VisionMode
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Calendar

@ExperimentalCamera2Interop
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var currentPolyline: Polyline? = null
    private var navigationService: NavigationService? = null
    private lateinit var locationOverlay: MyLocationNewOverlay

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize osmdroid configuration
        Configuration.getInstance().load(requireContext(), 
            requireContext().getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
            
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        navigationService = (requireActivity() as? MainActivity)?.getNavigationService()

        setupMap()

        binding.btnStopNavigation.setOnClickListener {
            (requireActivity() as? MainActivity)?.onCommandReceived("stop", com.innovista.smartglasses.services.VoiceCommand.STOP)
        }

        observeLocationUpdates()
        observeRouteUpdates()
        observeVisionMode()
    }

    private fun setupMap() {
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        
        val mapController = binding.map.controller
        mapController.setZoom(18.0)

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.map)
        locationOverlay.enableMyLocation()
        binding.map.overlays.add(locationOverlay)

        // Auto center once on first location
        locationOverlay.runOnFirstFix {
            requireActivity().runOnUiThread {
                mapController.setCenter(locationOverlay.myLocation)
            }
        }
    }

    private fun observeVisionMode() {
        (requireActivity() as? MainActivity)?.getNightVisionManager()?.currentVisionMode?.observe(viewLifecycleOwner) { mode ->
            applyMapStyle(mode)
        }
        
        (requireActivity() as? MainActivity)?.getOfflineModeManager()?.isOnline?.observe(viewLifecycleOwner) { isOnline ->
            updateOfflineUI(isOnline)
        }
    }

    private fun updateOfflineUI(isOnline: Boolean) {
        if (!isOnline) {
            binding.tvCurrentAddress.text = "OFFLINE MAP - Basic Guidance"
            binding.tvCurrentAddress.setTextColor(Color.YELLOW)
        } else {
            binding.tvCurrentAddress.setTextColor(Color.WHITE)
        }
    }

    private fun applyMapStyle(mode: VisionMode) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNightTime = hour >= 19 || hour < 6
        
        if (mode == VisionMode.NIGHT || isNightTime) {
            binding.map.overlayManager.tilesOverlay.setColorFilter(org.osmdroid.views.overlay.TilesOverlay.INVERT_COLORS)
        } else {
            binding.map.overlayManager.tilesOverlay.setColorFilter(null)
        }
    }

    private fun observeLocationUpdates() {
        LocationService.currentLocation.observe(viewLifecycleOwner) { location ->
            if (location != null) {
                val userPoint = GeoPoint(location.latitude, location.longitude)
                binding.map.controller.animateTo(userPoint)
                
                navigationService?.checkProgress(location)
                
                val address = (requireActivity() as? MainActivity)?.getLocationService()?.getAddressFromLocation(location)
                if (address != null) {
                    binding.tvCurrentAddress.text = address
                }
            }
        }
    }

    private fun observeRouteUpdates() {
        navigationService?.routePoints?.observe(viewLifecycleOwner) { points ->
            if (points.isNullOrEmpty()) {
                clearRoute()
            } else {
                drawRoute(points)
            }
        }
    }

    fun drawRoute(points: List<GeoPoint>) {
        clearRoute()
        currentPolyline = Polyline(binding.map).apply {
            setPoints(points)
            outlinePaint.color = Color.BLUE
            outlinePaint.strokeWidth = 10f
        }
        binding.map.overlays.add(currentPolyline)
        binding.map.invalidate()
    }

    fun clearRoute() {
        currentPolyline?.let {
            binding.map.overlays.remove(it)
            currentPolyline = null
            binding.map.invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MapFragment"
    }
}
