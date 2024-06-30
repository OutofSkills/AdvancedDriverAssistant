package com.example.advanceddrivingassistant

import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.advanceddrivingassistant.components.MapsLayout
import com.example.advanceddrivingassistant.location.LocationUpdatesManager
import com.example.advanceddrivingassistant.observers.LocationLifecycleObserver
import com.example.advanceddrivingassistant.ui.theme.AdvancedDrivingAssistantTheme
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient

class MapsActivity : ComponentActivity(), LocationLifecycleObserver.LocationSetupCallback {

    private val currentLocationState = mutableStateOf<Location?>(null)

    private lateinit var locationUpdatesManager: LocationUpdatesManager

    private lateinit var locationObserver: LocationLifecycleObserver

    private lateinit var placesClient: PlacesClient

    fun mockLocation(latitude: Double, longitude: Double, provider: String = "mock"): Location {
        return Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.accuracy = 1f
            this.time = System.currentTimeMillis()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val currentLocation by remember { currentLocationState }
            val targetLocation = remember { mutableStateOf<Location?>(null) }

            AdvancedDrivingAssistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        MapsLayout(
                            modifier = Modifier.fillMaxSize(),
                            currentLocation = currentLocation,
                            targetLocation = targetLocation.value
                        )

                        AutocompleteSearchBox(
                            placesClient = placesClient,
                            onPlaceSelected = { location ->
                                targetLocation.value = location
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }

        // Initialize Places API
        Places.initialize(applicationContext, "YOUR_PLACES_API_KEY")
        placesClient = Places.createClient(this)

        locationUpdatesManager = LocationUpdatesManager(this, ::onLocationUpdated)
        locationObserver = LocationLifecycleObserver(this, this)
        lifecycle.addObserver(locationObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationUpdatesManager.stopLocationUpdates()
    }

    private fun onLocationUpdated(location: Location?) {
        Log.d("MapsActivityTag", "Location updated: $location")
        currentLocationState.value = location
    }

    override fun locationTurnedOn() {
        locationUpdatesManager.startLocationUpdates()
    }

    override fun locationRequestCanceled() {
    }

    override fun locationPermissionsGranted() {
    }

    override fun locationPermissionsRefused() {
    }
}

@Composable
fun AutocompleteSearchBox(
    placesClient: PlacesClient,
    onPlaceSelected: (Location) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchText by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }

    Column(modifier.padding(16.dp)) {
        TextField(
            value = searchText,
            onValueChange = {
                searchText = it
                fetchPredictions(placesClient, searchText) { predictions = it }
            },
            label = { Text("Search places") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
        )

        predictions.forEach { prediction ->
            Text(
                text = prediction.getPrimaryText(null).toString(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        fetchPlaceDetails(placesClient, prediction.placeId) { location ->
                            onPlaceSelected(location)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

private fun fetchPredictions(
    placesClient: PlacesClient,
    query: String,
    onPredictionsReceived: (List<AutocompletePrediction>) -> Unit
) {
    val token = AutocompleteSessionToken.newInstance()
    val request = FindAutocompletePredictionsRequest.builder()
        .setTypeFilter(TypeFilter.ADDRESS)
        .setSessionToken(token)
        .setQuery(query)
        .build()

    val predictions = mutableListOf<AutocompletePrediction>()

    placesClient.findAutocompletePredictions(request)
        .addOnSuccessListener { response ->
            response.autocompletePredictions.forEach { prediction ->
                predictions.add(prediction)
            }
            onPredictionsReceived(predictions)
        }
        .addOnFailureListener { exception ->
            // Handle error
        }
}

private fun fetchPlaceDetails(
    placesClient: PlacesClient,
    placeId: String,
    onPlaceDetailsFetched: (Location) -> Unit
) {
    val request = FetchPlaceRequest.builder(placeId, listOf(Place.Field.LAT_LNG)).build()

    placesClient.fetchPlace(request)
        .addOnSuccessListener { response ->
            val place = response.place
            place.latLng?.let { latLng ->
                val location = Location("").apply {
                    latitude = latLng.latitude
                    longitude = latLng.longitude
                }
                onPlaceDetailsFetched(location)
            }
        }
        .addOnFailureListener { exception ->
            // Handle error
        }
}