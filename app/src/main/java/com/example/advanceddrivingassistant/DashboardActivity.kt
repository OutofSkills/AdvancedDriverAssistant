package com.example.advanceddrivingassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.advanceddrivingassistant.ui.theme.AdvancedDrivingAssistantTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import com.example.advanceddrivingassistant.components.PerformanceChart
import com.example.advanceddrivingassistant.db.DrivingData
import com.example.advanceddrivingassistant.db.LocalDbManager
import com.example.advanceddrivingassistant.utils.EcoScoreUtils
import com.example.advanceddrivingassistant.utils.calculateConsumptionPer100km
import com.example.advanceddrivingassistant.utils.formatDouble
import com.github.pires.obd.enums.AvailableCommandNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DashboardActivity : ComponentActivity() {

    private val loggingContext = "DashboardActivityTag"

    private val averageCarModelFuelConsumptions = 6.2 // l / 100Km

    private val speedState = mutableStateOf("0")
    private val rpmState = mutableStateOf("0")
    private val fuelLevelState = mutableStateOf("0")
    private val instantFuelConsumptionState = mutableDoubleStateOf(0.0)

    private var fuelConsumptionsRates = mutableStateOf(emptyList<Double>())

    private var selectedCarDataRangeFilter = 0 // live
    private var selectedFuelDataRangeFilter = 0 // live

    private var coroutineDbJob: Job? = null
    private val coroutineDbScope = CoroutineScope(Dispatchers.IO)
    private val localDbManager: LocalDbManager = LocalDbManager(this)

    private val obdCommandIntentFilter = IntentFilter().apply {
        addAction(AvailableCommandNames.FUEL_LEVEL.value)
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
                        instantFuelConsumptionState.doubleValue = it.getDoubleExtra("EXTRA_COMMAND_INSTANT_CONSUMPTION", 0.0)
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
                        averageCarModelFuelConsumptions = averageCarModelFuelConsumptions
                    )
                }
            }
        }

        ContextCompat.registerReceiver(
            this, obdDataReceiver, obdCommandIntentFilter, ContextCompat.RECEIVER_EXPORTED
        )

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
) {
    val fuelConsumptionPer100km =
        calculateConsumptionPer100km(speed.toDoubleOrNull() ?: 0.0, fuelConsumptionsRates.average())
    val availableRange = ((fuelLevel.toIntOrNull() ?: 0) / fuelConsumptionsRates.average()) * (speed.toIntOrNull() ?: 0)

    val chartLineColor = if (fuelConsumptionPer100km <= averageCarModelFuelConsumptions) Color(
        0xFF8FBC8F
    ) else Color(0xFFB22222)

    val ecoScore = EcoScoreUtils.calculateEcoScore(speed.toDoubleOrNull() ?: 0.0, fuelConsumptionsRates.average())

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
            averageCarModelFuelConsumptions
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
                    .height(200.dp)
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
fun MapLocationInfoCard(modifier: Modifier) {
    val singapore = LatLng(44.3302, 23.7949)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(singapore, 10f)
    }

    Card(
        modifier = modifier.padding(start = 4.dp), shape = RoundedCornerShape(10)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false
            )
        ) {
            Marker(
                state = MarkerState(position = singapore),
                title = "Craiova",
                snippet = "Marker in Singapore"
            )
        }
    }
}

@Composable
fun CarInfoCard(modifier: Modifier, availableRange: Int = 0, fuelLevel: Int = 0, averageCarModelFuelConsumptions: Double = 0.0) {
    val image = painterResource(R.drawable.green_car)

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
                text = "Skoda Superb",
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