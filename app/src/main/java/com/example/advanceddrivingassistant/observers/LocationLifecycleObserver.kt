package com.example.advanceddrivingassistant.observers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.advanceddrivingassistant.utils.logToFile

class LocationLifecycleObserver(
    private val context: ComponentActivity,
    private val locationSetupCallback: LocationSetupCallback,
): DefaultLifecycleObserver {

    private val loggingTag = "LocationLifecycleObserver"
    private var locationManager: LocationManager? = null
    private lateinit var locationEnableLauncher: ActivityResultLauncher<Intent>
    private lateinit var locationPermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationEnableLauncher = context.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode == Activity.RESULT_OK) {
                locationSetupCallback.locationTurnedOn()
            } else {
                locationSetupCallback.locationRequestCanceled()
            }
        }

        locationPermissionsLauncher = context.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                enableLocation()
                locationSetupCallback.locationPermissionsGranted()
            } else {
                locationSetupCallback.locationPermissionsRefused()
            }
        }

        requestLocationPermissions()
    }

    private fun enableLocation() {
        val isLocationEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER)
        logToFile(context, loggingTag, "enableLocation: $isLocationEnabled")

        if (!isLocationEnabled!!) {
            val enableLocationIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            locationEnableLauncher.launch(enableLocationIntent)
        } else {
            locationSetupCallback.locationTurnedOn()
        }
    }

    private fun requestLocationPermissions() {
        if ((ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED)
            || (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            val permissionsToRequest = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            locationPermissionsLauncher.launch(permissionsToRequest)
        } else {
            locationSetupCallback.locationPermissionsGranted()
            try {
                enableLocation()
            } catch (e: Exception) {
                logToFile(context, loggingTag, "requestLocationPermissions: " + e.message)
            }
        }
    }

    interface LocationSetupCallback {
        fun locationTurnedOn()
        fun locationRequestCanceled()
        fun locationPermissionsGranted()
        fun locationPermissionsRefused()
    }
}
