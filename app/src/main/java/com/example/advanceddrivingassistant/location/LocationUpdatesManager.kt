package com.example.advanceddrivingassistant.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.example.advanceddrivingassistant.utils.logToFile
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.TimeUnit

class LocationUpdatesManager(private val context: Context, private val onLocationChanged: (Location?) -> Unit) {

    private val loggingTag = "LocationProvider"

    // Main class for receiving location updates.
    // TODO: Step 1.2, Review the FusedLocationProviderClient.
    private val fusedLocationProviderClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // LocationRequest - Requirements for the location updates, i.e., how often you
    // should receive updates, the priority, etc.
    private var locationRequest: LocationRequest = LocationRequest.Builder(TimeUnit.SECONDS.toMillis(5))
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .build()

    // LocationCallback - Called when FusedLocationProviderClient has a new Location.
    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)

            onLocationChanged(locationResult.lastLocation);
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        try {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            logToFile(context, loggingTag, e.toString())
        }
    }

    fun stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }
}