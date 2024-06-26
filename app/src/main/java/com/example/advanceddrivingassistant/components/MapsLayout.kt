package com.example.advanceddrivingassistant.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Location
import android.util.Log
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
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin


@Composable
fun MapsLayout(modifier: Modifier, currentLocation: Location?, isInteractive: Boolean = true) {
    val currentLatLng = LatLng(currentLocation?.latitude ?: 0.0, currentLocation?.longitude ?: 0.0)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLatLng, 15f) // Zoom level adjusted to 15f for better visibility
    }
    var previousLocation by remember { mutableStateOf<Location?>(null) }
    val bearing = calculateBearing(previousLocation, currentLocation)

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
            // Google Map content
            if (currentLocation != null) {
                Log.d("MapsActivityTag", "Bearing: $bearing")
                Marker(
                    state = MarkerState(position = currentLatLng),
                    icon = bitmapDescriptorFromVector(R.drawable.car, LocalContext.current, 200, 200),
                    zIndex = 1f,
                    rotation = bearing.toFloat(),
                    anchor = Offset(0.5f, 0.5f),
                )
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