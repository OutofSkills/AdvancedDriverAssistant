package com.example.advanceddrivingassistant.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.advanceddrivingassistant.R
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MapsLayout(
    modifier: Modifier,
    currentLocation: Location?,
    targetLocation: Location? = null,
    isInteractive: Boolean = true
) {
    val context = LocalContext.current
    val currentLatLng = LatLng(currentLocation?.latitude ?: 0.0, currentLocation?.longitude ?: 0.0)
    val targetLatLng = targetLocation?.let { LatLng(it.latitude, it.longitude) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLatLng, 15f) // Zoom level adjusted to 15f for better visibility
    }
    var previousLocation by remember { mutableStateOf<Location?>(null) }
    val bearing = calculateBearing(previousLocation, currentLocation)

    // Distance threshold in meters to consider as "arrived"
    val arrivalThreshold = 50

    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLatLng, 15f)
            previousLocation = currentLocation
        }
    }

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = isInteractive,
                scrollGesturesEnabled = isInteractive,
                zoomGesturesEnabled = isInteractive,
                tiltGesturesEnabled = isInteractive,
                rotationGesturesEnabled = isInteractive,
            )
        ) {
            if (currentLocation != null) {
                Marker(
                    state = MarkerState(position = currentLatLng),
                    icon = bitmapDescriptorFromVector(R.drawable.car, context, 150, 150),
                    zIndex = 1f,
                    rotation = bearing.toFloat(),
                    anchor = Offset(0.5f, 0.5f),
                )
            }

            if (targetLocation != null && currentLocation != null) {
                val distanceToTarget = calculateDistance(currentLocation, targetLocation)

                if (distanceToTarget > arrivalThreshold) {
                    Marker(
                        state = MarkerState(position = targetLatLng!!),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )

                    Polyline(
                        points = listOf(currentLatLng, targetLatLng),
                        color = Color.Blue,
                        width = 5f
                    )
                }
            }
        }
    }
}

fun calculateBearing(startLocation: Location?, endLocation: Location?): Double {
    val startLat = Math.toRadians(startLocation?.latitude ?: 0.0)
    val startLong = Math.toRadians(startLocation?.longitude ?: 0.0)
    val endLat = Math.toRadians(endLocation?.latitude ?: 0.0)
    val endLong = Math.toRadians(endLocation?.longitude ?: 0.0)

    val dLong = endLong - startLong

    val y = sin(dLong) * cos(endLat)
    val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLong)

    var bearing = atan2(y, x)
    bearing = Math.toDegrees(bearing)
    return (bearing + 360) % 360
}

private fun bitmapDescriptorFromVector(vectorResId: Int, context: Context, width: Int, height: Int): BitmapDescriptor {
    val vectorDrawable: Drawable? = ContextCompat.getDrawable(context, vectorResId)
    vectorDrawable?.setBounds(0, 0, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    vectorDrawable?.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

fun calculateDistance(startLocation: Location, endLocation: Location): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        startLocation.latitude, startLocation.longitude,
        endLocation.latitude, endLocation.longitude,
        results
    )
    return results[0]
}