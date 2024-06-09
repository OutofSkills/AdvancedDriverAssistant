package com.example.advanceddrivingassistant.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

private fun getValuePercentageForRange(value: Double, max: Double, min: Double) =
    (value - min) / (max - min)

@Composable
@Preview(heightDp = 300, widthDp = 300, backgroundColor = 0xFFFFFFFF, showBackground = true)
fun PerformanceChart(
    modifier: Modifier = Modifier,
    list: List<Double> = listOf(10.0, 20.0, 3.0, 1.0),
    lineColor: Color = Color.Blue,
    maxDataPoints: Int = 50,
) {
    // Sample the data to reduce the number of data points
    val sampledList = sampleData(list, maxDataPoints)

    val zipList: List<Pair<Double, Double>> = sampledList.zipWithNext()

    var dragX by remember { mutableFloatStateOf(-1f) }
    var interpolatedValue by remember { mutableStateOf<Double?>(null) }

    Box(modifier = modifier
        .fillMaxWidth()
        .pointerInput(Unit) {
            detectDragGestures(onDragStart = { change ->
                dragX = change.x
            }, onDragEnd = {
                dragX = -1f
                interpolatedValue = null
            }, onDragCancel = {
                dragX = -1f
                interpolatedValue = null
            }, onDrag = { change, _ ->
                dragX = change.position.x
            })
        }) {
        val max = sampledList.maxOrNull() ?: 1.0
        val min = sampledList.minOrNull() ?: 0.0

        Canvas(modifier = Modifier.fillMaxSize(), onDraw = {
            var startX = 0f
            for (pair in zipList) {
                val fromValuePercentage = getValuePercentageForRange(pair.first, max, min)
                val toValuePercentage = getValuePercentageForRange(pair.second, max, min)

                val fromPoint =
                    Offset(x = startX, y = (size.height * (1 - fromValuePercentage)).toFloat())
                val toPoint = Offset(
                    x = startX + size.width / zipList.size,
                    y = (size.height * (1 - toValuePercentage)).toFloat()
                )

                drawLine(
                    color = lineColor, start = fromPoint, end = toPoint, strokeWidth = 3f
                )

                startX += size.width / zipList.size
            }

            if (dragX >= 0) {
                val chartWidth = size.width
                val segmentWidth = chartWidth / zipList.size

                val touchedIndex = (dragX / segmentWidth).roundToInt().coerceIn(0, zipList.size - 1)
                val segmentStartX = touchedIndex * segmentWidth
                val from = zipList[touchedIndex].first
                val to = zipList[touchedIndex].second
                val proportion = ((dragX - segmentStartX) / segmentWidth).coerceIn(0f, 1f)
                interpolatedValue = from + proportion * (to - from)

                val intersectY = size.height * (1 - getValuePercentageForRange(
                    interpolatedValue!!, max, min
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
                    center = Offset(dragX, intersectY.toFloat())
                )

                drawContext.canvas.nativeCanvas.apply {
                    drawText("%.2f".format(interpolatedValue),
                        dragX,
                        (intersectY - 10).toFloat(),
                        Paint().asFrameworkPaint().apply {
                            isAntiAlias = true
                            textSize = 30f
                            color = android.graphics.Color.BLACK
                        })
                }
            }
        })
    }
}

// Function to sample the data
fun sampleData(data: List<Double>, maxPoints: Int): List<Double> {
    val step = maxOf(1, data.size / maxPoints)
    return data.filterIndexed { index, _ -> index % step == 0 }
}
