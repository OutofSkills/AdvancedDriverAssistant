package com.example.advanceddrivingassistant.services.bluetooth

import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.advanceddrivingassistant.R
import com.example.advanceddrivingassistant.db.DrivingData
import com.example.advanceddrivingassistant.db.LocalDbManager
import com.example.advanceddrivingassistant.dto.CarDataDto
import com.example.advanceddrivingassistant.dto.EcoDrivingInference
import com.example.advanceddrivingassistant.obd.ObdCommandManager
import com.example.advanceddrivingassistant.obd.ObdCommandResult
import com.example.advanceddrivingassistant.tensorflow.EcoDrivingClassifier
import com.example.advanceddrivingassistant.utils.BluetoothServiceActions
import com.example.advanceddrivingassistant.utils.ConnectionStateServiceActions
import com.example.advanceddrivingassistant.utils.Constants
import com.example.advanceddrivingassistant.utils.DeviceConnectionState
import com.example.advanceddrivingassistant.utils.EcoDrivingClass
import com.example.advanceddrivingassistant.utils.calculateConsumptionPer100km
import com.example.advanceddrivingassistant.utils.formatDouble
import com.example.advanceddrivingassistant.utils.generateRandomDouble
import com.example.advanceddrivingassistant.utils.logToFile
import com.github.pires.obd.enums.AvailableCommandNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class BluetoothService : Service() {

    private val loggingContext = "BluetoothServiceTag"

    private val carData: CarDataDto = CarDataDto(0, 0, 0.0, 0.0)
    private val ecoDrivingInference: EcoDrivingInference =
        EcoDrivingInference(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)

    private val coroutineDbScope = CoroutineScope(Dispatchers.IO)
    private var coroutineDbJob: Job? = null

    private val coroutineNotificationScope = CoroutineScope(Dispatchers.IO)
    private var coroutineNotificationJob: Job? = null

    private lateinit var obdCommandManager: ObdCommandManager
    private val localDbManager: LocalDbManager = LocalDbManager(this)

    private lateinit var ecoDrivingClassifier: EcoDrivingClassifier

    private val bluetoothConnectionManager: BluetoothConnectionManager = BluetoothConnectionManager(
        this
    ) { connectionState -> onDeviceStateChange(connectionState) }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        bluetoothConnectionManager.init()
        ecoDrivingClassifier = EcoDrivingClassifier(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        bluetoothConnectionManager.onCleanup()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            BluetoothServiceActions.START_DISCOVERY.toString() -> startDiscovery()
            BluetoothServiceActions.DEVICE_CONNECT.toString() -> {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothServiceActions.DEVICE_CONNECT.toString())
                connect(device)
            }

            BluetoothServiceActions.DEVICE_DISCONNECT.toString() -> disconnect()
            BluetoothServiceActions.START_SERVICE.toString() -> startBluetoothForeground()
        }

        return START_NOT_STICKY
    }

    private fun startBluetoothForeground() {
        try {
            val remoteViews = RemoteViews(packageName, R.layout.notification_layout)

            remoteViews.setTextViewText(
                R.id.text_instant_fuel,
                "Instant fuel consumption: -"
            )
            remoteViews.setTextViewText(
                R.id.text_average_fuel,
                "Average fuel consumption: -"
            )
            remoteViews.setTextViewText(R.id.text_driving_style, "Driving style: -")

            val notification = NotificationCompat.Builder(this, Constants.notificationChannelID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()


            ServiceCompat.startForeground(
                this,
                100,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: Exception) {
            logToFile(this, loggingContext, "Could not start service: ${e.message}")
        }
    }

    private fun startDiscovery() {
        Log.d(loggingContext, "startDiscovery")
        bluetoothConnectionManager.startDiscovery()
    }

    private fun connect(device: BluetoothDevice?) {
        if (device == null) {
            logToFile(this, loggingContext, "[connect] Device null")
        }

        bluetoothConnectionManager.connectToDevice(device)
    }

    private fun disconnect() {
        coroutineDbJob?.cancel()
    }

    private fun onDeviceStateChange(connectionState: DeviceConnectionState) {
        Log.d(loggingContext, "onDeviceStateChange")
        val intent = Intent()

        when (connectionState) {
            is DeviceConnectionState.Connected -> {
                intent.action = ConnectionStateServiceActions.ACTION_DEVICE_CONNECTED.toString()
                intent.putExtra("EXTRA_DEVICE_ADDRESS", connectionState.socket.remoteDevice.address)

                obdCommandManager =
                    ObdCommandManager(this, connectionState.socket, ::onObdResultReceived)
                obdCommandManager.startCollectingData()

                startStoringDrivingData()
                startShowingNotificationUpdates()
            }

            is DeviceConnectionState.Disconnected -> {
                intent.action = ConnectionStateServiceActions.ACTION_DEVICE_DISCONNECTED.toString()

                obdCommandManager.stopCollectingData()
            }

            is DeviceConnectionState.Connecting -> {
                intent.action = ConnectionStateServiceActions.ACTION_DEVICE_CONNECTING.toString()
                intent.putExtra("EXTRA_DEVICE_ADDRESS", connectionState.bluetoothDevice.address)
            }

            is DeviceConnectionState.ConnectionFailed -> {
                intent.action = ConnectionStateServiceActions.ACTION_CONNECTION_FAILED.toString()
                intent.putExtra("EXTRA_FAILURE_REASON", connectionState.failureReason)
            }
        }

        this@BluetoothService.sendBroadcast(intent)
    }

    private fun startShowingNotificationUpdates() {
        coroutineNotificationJob = coroutineNotificationScope.launch {
            while (true) {
                try {
//                    val drivingStyle = ecoDrivingClassifier.classify(ecoDrivingInference.toFloatArray())

                    val ecoDrivingData = EcoDrivingInference.randomInstance()
                    val drivingStyle = ecoDrivingClassifier.classify(ecoDrivingData.toFloatArray())

                    val speedAndConsumptionRateRecords =
                        localDbManager.getAverageSpeedAndConsumptionRate(20)
                    val averageConsumptionRate = calculateConsumptionPer100km(
                        speedAndConsumptionRateRecords[0],
                        speedAndConsumptionRateRecords[1]
                    )

                    Log.d(
                        loggingContext,
                        "Average consumption rate: $speedAndConsumptionRateRecords"
                    )

                    val instantConsumption = calculateConsumptionPer100km(
                        carData.speed.toDouble(),
                        ecoDrivingData.consumptionRate.toDouble()
                    )
                    carData.instantConsumption = instantConsumption

                    updateNotification(drivingStyle, instantConsumption, averageConsumptionRate)

                    delay(2000)
                } catch (e: Exception) {
                    logToFile(
                        this@BluetoothService,
                        loggingContext,
                        "Error saving data: ${e.message}"
                    )
                }
            }
        }
    }

    private fun startStoringDrivingData() {
        coroutineDbJob = coroutineDbScope.launch {
            while (true) {
                try {
                    localDbManager.saveData(
                        DrivingData(
                            null,
                            null,
                            null,
                            carData.speed.toString(),
                            carData.rpm.toString(),
                            generateRandomDouble(0.0, 10.0).toString(),
                            0
                        )
                    )

                    delay(500)
                } catch (e: Exception) {
                    logToFile(
                        this@BluetoothService,
                        loggingContext,
                        "Error saving data: ${e.message}"
                    )
                }
            }
        }
    }

    private fun onObdResultReceived(result: ObdCommandResult) {
        Log.d(loggingContext, "[onObdResultReceived] result: ${result.name} - ${result.value}")

        when (result.name) {
            AvailableCommandNames.FUEL_LEVEL.value -> {
                carData.fuelLevel = result.value.toDoubleOrNull() ?: 0.0
            }

            AvailableCommandNames.ENGINE_RPM.value -> {
                carData.rpm = result.value.toIntOrNull() ?: 0
                ecoDrivingInference.rpm = result.value.toFloatOrNull() ?: 0.0f
            }

            AvailableCommandNames.SPEED.value -> {
                carData.speed = result.value.toIntOrNull() ?: 0
            }

            AvailableCommandNames.ENGINE_LOAD.value -> {
                ecoDrivingInference.load = result.value.toFloatOrNull() ?: 0.0f
            }

            AvailableCommandNames.FUEL_CONSUMPTION_RATE.value -> {
                ecoDrivingInference.consumptionRate = result.value.toFloatOrNull() ?: 0.0f
            }

            "Driver's Demand Engine - Percent Torque" -> {
                ecoDrivingInference.driversDemandEnginePercentTorque =
                    result.value.toFloatOrNull() ?: 0.0f
            }

            "Actual Engine - Percent Torque" -> {
                ecoDrivingInference.actualEnginePercentTorque = result.value.toFloatOrNull() ?: 0.0f
            }

            "Accelerator Pedal Position D" -> {
                ecoDrivingInference.acceleratorPedalPositionD = result.value.toFloatOrNull() ?: 0.0f
            }

            "Accelerator Pedal Position E" -> {
                ecoDrivingInference.acceleratorPedalPositionE = result.value.toFloatOrNull() ?: 0.0f
            }

            "Relative Throttle Position" -> {
                ecoDrivingInference.relativeThrottlePosition = result.value.toFloatOrNull() ?: 0.0f
            }
        }

        val intent = Intent()
        intent.action = result.name

        intent.putExtra("EXTRA_COMMAND_NAME", result.name)
        intent.putExtra("EXTRA_COMMAND_VALUE", result.value)
        intent.putExtra("EXTRA_COMMAND_UNIT", result.unit)
        intent.putExtra("EXTRA_COMMAND_INSTANT_CONSUMPTION", carData.instantConsumption)

        this@BluetoothService.sendBroadcast(intent)
    }

    private fun updateNotification(
        drivingStyle: EcoDrivingClass,
        instantConsumption: Double,
        averageConsumptionRate: Double
    ) {
        val remoteViews = RemoteViews(packageName, R.layout.notification_layout)

        remoteViews.setTextViewText(
            R.id.text_instant_fuel,
            "Instant fuel consumption: ${
                formatDouble(instantConsumption)
            } l/100km"
        )
        remoteViews.setTextViewText(
            R.id.text_average_fuel,
            "Average fuel consumption: ${
                formatDouble(averageConsumptionRate)
            } l/100km"
        )
        remoteViews.setTextViewText(R.id.text_driving_style, "Driving style: $drivingStyle")

        val notification = NotificationCompat.Builder(this, Constants.notificationChannelID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.notify(100, notification)
    }
}