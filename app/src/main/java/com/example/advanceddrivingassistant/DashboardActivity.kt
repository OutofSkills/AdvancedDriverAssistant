package com.example.advanceddrivingassistant

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

class DashboardActivity : ComponentActivity() {

    private val loggingContext = "DashboardActivityTag"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdvancedDrivingAssistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardLayout(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        val deviceAddress = intent.extras?.getString("device_address_key")
        Log.d(loggingContext, "Device Address: $deviceAddress")
    }
}

@Composable
fun DashboardLayout(modifier: Modifier) {
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
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
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
                    TimeDataFilterHeader()
                    PerformanceChart(
                        Modifier
                            .height(80.dp)
                            .padding(16.dp),
                        listOf(
                            113.518f,
                            113.799f,
                            113.333f,
                            113.235f,
                            114.099f,
                            113.506f,
                            113.985f,
                            114.212f,
                            114.125f,
                            113.531f,
                            114.228f,
                            113.284f,
                            114.031f,
                            113.493f,
                            113.112f
                        ),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "0.9", fontSize = 24.sp)
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimeDataFilterHeader()

                TextButton(onClick = { }) {
                    Text(text = "More", fontSize = 12.sp)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                LabeledValueRow(
                    label = "Eco Score",
                    value = "721pts",
                    icon = Icons.Filled.Info
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LabeledValueRow(
                    label = "Speed",
                    value = "64km/h",
                    icon = Icons.Filled.Info
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LabeledValueRow(
                    label = "RPM",
                    value = "64RPM",
                    icon = Icons.Filled.Info,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }

}

@Composable
fun LabeledValueRow(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
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
private fun TimeDataFilterHeader() {
    Row {
        TextButton(onClick = { }) {
            Text(text = "Day", fontSize = 10.sp)
        }
        TextButton(onClick = { }) {
            Text(text = "Week", fontSize = 10.sp)
        }
        TextButton(onClick = { }) {
            Text(text = "Month", fontSize = 10.sp)
        }
    }
}

@Composable
private fun NotificationsButton(onClick: () -> Unit = {}) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = "Notifications"
        )
        Text(text = "Notifications")
    }
}

@Composable
private fun InfoButton(onClick: () -> Unit = {}) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = "Information"
        )
        Text(text = "Information")
    }
}

@Composable
fun MapLocationInfoCard(modifier: Modifier) {
    val singapore = LatLng(1.35, 103.87)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(singapore, 10f)
    }

    Card(
        modifier = modifier
            .padding(start = 4.dp),
        shape = RoundedCornerShape(10)
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
                title = "Singapore",
                snippet = "Marker in Singapore"
            )
        }
    }
}

@Composable
fun CarInfoCard(modifier: Modifier) {
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
                painter = image,
                contentDescription = "My car"
            )

            Text(
                text = "Toyota Yaris",
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
                        Text(text = "~200Km", fontSize = 12.sp)
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
                        Text(text = "10L", fontSize = 12.sp)
                    }
                }
                Text(text = "5.4 l/100km", fontSize = 12.sp)
            }
        }
    }
}

private fun getValuePercentageForRange(value: Float, max: Float, min: Float) =
    (value - min) / (max - min)

@Composable
@Preview(heightDp = 300, widthDp = 300, backgroundColor = 0xFFFFFFFF, showBackground = true)
fun PerformanceChart(modifier: Modifier = Modifier, list: List<Float> = listOf(10f, 20f, 3f, 1f)) {
    val zipList: List<Pair<Float, Float>> = list.zipWithNext()

    var dragX by remember { mutableStateOf(-1f) }
    var interpolatedValue by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { change ->
                        dragX = change.x
                    },
                    onDragEnd = {
                        dragX = -1f
                        interpolatedValue = null
                    },
                    onDragCancel = {
                        dragX = -1f
                        interpolatedValue = null
                    },
                    onDrag = { change, _ ->
                        dragX = change.position.x
                    }
                )
            }
    ) {
        val max = list.maxOrNull() ?: 1f
        val min = list.minOrNull() ?: 0f

        val lineColor = if (list.last() < list.first()) Color(0xFF8FBC8F) else Color(0xFFB22222)

        Canvas(
            modifier = Modifier.fillMaxSize(),
            onDraw = {
                var startX = 0f
                for (pair in zipList) {
                    val fromValuePercentage = getValuePercentageForRange(pair.first, max, min)
                    val toValuePercentage = getValuePercentageForRange(pair.second, max, min)

                    val fromPoint = Offset(x = startX, y = size.height * (1 - fromValuePercentage))
                    val toPoint = Offset(
                        x = startX + size.width / zipList.size,
                        y = size.height * (1 - toValuePercentage)
                    )

                    drawLine(
                        color = lineColor,
                        start = fromPoint,
                        end = toPoint,
                        strokeWidth = 3f
                    )

                    startX += size.width / zipList.size
                }

                if (dragX >= 0) {
                    val chartWidth = size.width
                    val segmentWidth = chartWidth / zipList.size

                    val touchedIndex =
                        (dragX / segmentWidth).roundToInt().coerceIn(0, zipList.size - 1)
                    val segmentStartX = touchedIndex * segmentWidth
                    val from = zipList[touchedIndex].first
                    val to = zipList[touchedIndex].second
                    val proportion = ((dragX - segmentStartX) / segmentWidth).coerceIn(0f, 1f)
                    interpolatedValue = from + proportion * (to - from)

                    val intersectY = size.height * (1 - getValuePercentageForRange(
                        interpolatedValue!!,
                        max,
                        min
                    ))

                    drawLine(
                        color = Color.Gray,
                        start = Offset(dragX, 0f),
                        end = Offset(dragX, size.height),
                        strokeWidth = 2f
                    )

                    drawCircle(
                        color = Color.Blue,
                        radius = 4f,
                        center = Offset(dragX, intersectY)
                    )

                    // Draw the value at the intersection point
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(
                            "%.2f".format(interpolatedValue),
                            dragX,
                            intersectY - 10,
                            Paint().asFrameworkPaint().apply {
                                isAntiAlias = true
                                textSize = 30f
                                color = android.graphics.Color.BLACK
                            }
                        )
                    }
                }
            }
        )
    }
}