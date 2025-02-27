package com.example.advanceddrivingassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.advanceddrivingassistant.ui.theme.AdvancedDrivingAssistantTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.advanceddrivingassistant.components.MapsLayout
import com.example.advanceddrivingassistant.components.PerformanceChart
import com.example.advanceddrivingassistant.db.DrivingData
import com.example.advanceddrivingassistant.db.LocalDbManager
import com.example.advanceddrivingassistant.location.LocationUpdatesManager
import com.example.advanceddrivingassistant.observers.LocationLifecycleObserver
import com.example.advanceddrivingassistant.utils.EcoScoreUtils
import com.example.advanceddrivingassistant.utils.calculateConsumptionPer100km
import com.example.advanceddrivingassistant.utils.formatDouble
import com.example.advanceddrivingassistant.utils.logToFile
import com.github.pires.obd.enums.AvailableCommandNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DashboardActivity : ComponentActivity(), LocationLifecycleObserver.LocationSetupCallback {

    private val loggingContext = "DashboardActivityTag"

    private val averageCarModelFuelConsumptions = 6.2 // l / 100Km

    private val speedState = mutableStateOf("0")
    private val rpmState = mutableStateOf("0")
    private val fuelLevelState = mutableStateOf("0")

    private val carModelState = mutableStateOf("")
    private val carMakeState = mutableStateOf("")

    private var fuelConsumptionsRates = mutableStateOf(emptyList<Double>())

    private var selectedCarDataRangeFilter = 0 // live
    private var selectedFuelDataRangeFilter = 0 // live

    private var coroutineDbJob: Job? = null
    private val coroutineDbScope = CoroutineScope(Dispatchers.IO)
    private val localDbManager: LocalDbManager = LocalDbManager(this)

    private val currentLocationState = mutableStateOf<Location?>(null)

    private lateinit var locationUpdatesManager: LocationUpdatesManager

    private lateinit var locationObserver: LocationLifecycleObserver


    private val obdCommandIntentFilter = IntentFilter().apply {
        addAction("FUEL_LEVEL")
        addAction("VIN")
    }

    private val obdDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                Log.d(
                    loggingContext,
                    "val: ${it.getStringExtra("EXTRA_COMMAND_VALUE")} ${it.action === AvailableCommandNames.ENGINE_RPM.value}"
                )
                when (val action = it.action) {
                    AvailableCommandNames.FUEL_LEVEL.value -> {
                        fuelLevelState.value = it.getStringExtra("EXTRA_COMMAND_VALUE") ?: ""
                    }

                    "VIN" -> {
                        carMakeState.value = it.getStringExtra("EXTRA_COMMAND_MAKE") ?: "Unknown"
                        carModelState.value = it.getStringExtra("EXTRA_COMMAND_MODEL") ?: "Unknown"
                        logToFile(
                            this@DashboardActivity,
                            loggingContext,
                            "$carMakeState $carModelState"
                        )
                    }

                    else -> {
                        Log.d(loggingContext, "Unknown action: $action")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdvancedDrivingAssistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val speed by remember { speedState }
                    val rpm by remember { rpmState }
                    val fuelLevel by remember { fuelLevelState }
                    val fuelConsumptionsRates by remember { fuelConsumptionsRates }
                    val carModel by remember { carModelState }
                    val carMake by remember { carMakeState }
                    val currentLocation by remember { currentLocationState }

                    DashboardLayout(
                        modifier = Modifier.padding(innerPadding),
                        speed = speed,
                        rpm = rpm,
                        fuelLevel = fuelLevel,
                        onCarDataFilterClick = {
                            selectedCarDataRangeFilter = it
                        },
                        onFuelDataFilterClick = {
                            selectedFuelDataRangeFilter = it
                        },
                        fuelConsumptionsRates = fuelConsumptionsRates,
                        averageCarModelFuelConsumptions = averageCarModelFuelConsumptions,
                        carModel = carModel,
                        carMake = carMake,
                        currentLocation = currentLocation,
                    )
                }
            }
        }

        ContextCompat.registerReceiver(
            this, obdDataReceiver, obdCommandIntentFilter, ContextCompat.RECEIVER_EXPORTED
        )

        locationUpdatesManager = LocationUpdatesManager(this, ::onLocationUpdated)
        locationObserver = LocationLifecycleObserver(this, this)
        lifecycle.addObserver(locationObserver)

        refreshAverageDrivingData()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(obdDataReceiver)
        coroutineDbJob?.cancel()
    }

    private fun refreshAverageDrivingData() {
        coroutineDbJob = coroutineDbScope.launch {
            while (true) {
                var avgDrivingData: DrivingData

                if (selectedFuelDataRangeFilter == 0) {
                    fuelConsumptionsRates.value =
                        localDbManager.getFuelLastConsumptionRatesRecords(20)
                } else {
                    fuelConsumptionsRates.value =
                        localDbManager.getFuelConsumptionRates(selectedFuelDataRangeFilter)
                }

                if (selectedCarDataRangeFilter == 0) {
                    val lastRecordedData = localDbManager.getLastRecordedData(1)
                    avgDrivingData = lastRecordedData.last()
                } else {
                    avgDrivingData = localDbManager.getAverageData(selectedCarDataRangeFilter)
                }

                speedState.value = avgDrivingData.speed.toString()
                rpmState.value = avgDrivingData.rpm.toString()

                delay(1000)
            }
        }
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
fun DashboardLayout(
    modifier: Modifier,
    speed: String = "-",
    rpm: String = "-",
    fuelLevel: String = "-",
    onCarDataFilterClick: (Int) -> Unit = {},
    onFuelDataFilterClick: (Int) -> Unit = {},
    fuelConsumptionsRates: List<Double>,
    averageCarModelFuelConsumptions: Double = 0.0,
    carModel: String = "-",
    carMake: String = "-",
    currentLocation: Location? = null,
) {
    val fuelConsumptionPer100km =
        calculateConsumptionPer100km(speed.toDoubleOrNull() ?: 0.0, fuelConsumptionsRates.average())
    val availableRange =
        ((fuelLevel.toIntOrNull() ?: 0) / fuelConsumptionsRates.average()) * (speed.toIntOrNull()
            ?: 0)

    val chartLineColor = if (fuelConsumptionPer100km <= averageCarModelFuelConsumptions) Color(
        0xFF8FBC8F
    ) else Color(0xFFB22222)

    val ecoScore = EcoScoreUtils.calculateEcoScore(
        speed.toDoubleOrNull() ?: 0.0,
        fuelConsumptionsRates.average()
    )

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.Absolute.SpaceBetween
        ) {
            InfoButton()
            NotificationsButton()
        }

        CarInfoCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
            availableRange.toInt(),
            fuelLevel.toIntOrNull() ?: 0,
            averageCarModelFuelConsumptions,
            carModel,
            carMake,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
                    .height(200.dp),
                shape = RoundedCornerShape(10)
            ) {
                Column {
                    TimeDataFilterHeader(onFilterClick = onFuelDataFilterClick)
                    PerformanceChart(
                        Modifier
                            .height(80.dp)
                            .padding(16.dp),
                        list = fuelConsumptionsRates,
                        lineColor = chartLineColor,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "%.1f".format(fuelConsumptionsRates.average()), fontSize = 24.sp
                        )
                        Text(
                            text = "l/h",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Bottom)
                        )
                    }
                }
            }

            MapLocationInfoCard(
                Modifier
                    .weight(1f)
                    .height(200.dp),
                currentLocation = currentLocation
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), shape = RoundedCornerShape(10)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimeDataFilterHeader(onFilterClick = onCarDataFilterClick)

                TextButton(onClick = { }) {
                    Text(text = "More", fontSize = 12.sp)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                LabeledValueRow(
                    label = "Eco Score",
                    value = "${formatDouble(ecoScore)}pts",
                    icon = ImageVector.vectorResource(R.drawable.eco_friendly)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LabeledValueRow(
                    label = "Speed",
                    value = "${speed}Km/h",
                    icon = ImageVector.vectorResource(R.drawable.deadline)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LabeledValueRow(
                    label = "RPM",
                    value = "${rpm}r/m",
                    icon = ImageVector.vectorResource(R.drawable.dynamo),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }

}

@Composable
fun LabeledValueRow(
    label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row {
            Icon(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .scale(0.75f),
                imageVector = icon,
                contentDescription = label
            )
            Text(text = label, color = Color.Gray, fontSize = 12.sp)
        }

        Text(text = value)
    }
}

@Composable
private fun TimeDataFilterHeader(onFilterClick: (Int) -> Unit) {
    var selectedFilter by remember { mutableStateOf<Int?>(null) }

    Row {
        val filters = listOf(1 to "Day", 7 to "Week", 30 to "Month")

        filters.forEach { (days, label) ->
            val isSelected = selectedFilter == days
            TextButton(
                onClick = {
                    selectedFilter = if (isSelected) null else days
                    onFilterClick(selectedFilter ?: 0)
                },
                colors = if (isSelected) ButtonDefaults.buttonColors(containerColor = Color.Gray) else ButtonDefaults.textButtonColors()
            ) {
                Text(text = label, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun NotificationsButton(onClick: () -> Unit = {}) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Notifications, contentDescription = "Notifications"
        )
        Text(text = "Notifications")
    }
}

@Composable
private fun InfoButton(onClick: () -> Unit = {}) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Info, contentDescription = "Information"
        )
        Text(text = "Information")
    }
}

@Composable
fun MapLocationInfoCard(modifier: Modifier, currentLocation: Location?) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .padding(start = 4.dp)
            .clickable {
                val intent = Intent(context, MapsActivity::class.java).apply {
                    putExtra("currentLocation", currentLocation)
                }
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(10),

        ) {
        Box {
            MapsLayout(modifier = modifier, currentLocation = currentLocation, isInteractive = false)
            Surface(
                modifier = Modifier.matchParentSize(),
                color = Color.Transparent
            ) { }
        }
    }
}

@Composable
fun CarInfoCard(
    modifier: Modifier,
    availableRange: Int = 0,
    fuelLevel: Int = 0,
    averageCarModelFuelConsumptions: Double = 0.0,
    carModel: String = "",
    carMake: String = ""
) {
    val image = painterResource(R.drawable.green_car)

    val carModelAndMake = if (carMake.isEmpty() && carModel.isEmpty()) {
        "-"
    } else {
        "$carMake $carModel"
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text(text = "My car")
            }

            Image(
                painter = image, contentDescription = "My car"
            )

            Text(
                text = carModelAndMake,
                modifier = Modifier.padding(bottom = 8.dp),
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    Row(
                        modifier = Modifier.padding(end = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.route),
                            contentDescription = "Available range",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(text = "~${availableRange}Km", fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.padding(start = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.gas_station),
                            contentDescription = "Fuel level",
                            modifier = Modifier.size(16.dp)
                        )
                        Text(text = "${fuelLevel}L", fontSize = 12.sp)
                    }
                }
                Text(text = "$averageCarModelFuelConsumptions l/100km", fontSize = 12.sp)
            }
        }
    }
}