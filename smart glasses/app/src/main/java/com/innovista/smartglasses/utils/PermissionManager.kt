package com.innovista.smartglasses.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class PermissionManager {

    companion object {
        private const val TAG = "PermissionManagerFragment"

        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.BLUETOOTH
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()

        fun checkAndRequestAll(
            activity: AppCompatActivity,
            onAllGranted: () -> Unit,
            onDenied: (String) -> Unit
        ) {
            val missing = REQUIRED_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isEmpty()) {
                onAllGranted()
                return
            }

            val fragmentManager = activity.supportFragmentManager
            val fragment = PermissionFragment().apply {
                this.missingPermissions = missing.toTypedArray()
                this.onAllGranted = onAllGranted
                this.onDenied = onDenied
            }
            fragmentManager.beginTransaction().add(fragment, TAG).commitNow()
        }
    }

    class PermissionFragment : Fragment() {
        var missingPermissions: Array<String> = arrayOf()
        var onAllGranted: (() -> Unit)? = null
        var onDenied: ((String) -> Unit)? = null

        private val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val denied = result.entries.firstOrNull { !it.value }?.key
            if (denied != null) {
                onDenied?.invoke(denied)
            } else {
                onAllGranted?.invoke()
            }
            parentFragmentManager.beginTransaction().remove(this).commit()
        }

        override fun onStart() {
            super.onStart()
            if (missingPermissions.isNotEmpty()) {
                launcher.launch(missingPermissions)
            } else {
                parentFragmentManager.beginTransaction().remove(this).commit()
            }
        }
    }
}
