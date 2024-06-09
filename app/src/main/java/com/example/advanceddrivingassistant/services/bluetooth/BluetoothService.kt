package com.example.advanceddrivingassistant.services.bluetooth

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.advanceddrivingassistant.R
import com.example.advanceddrivingassistant.db.DrivingData
import com.example.advanceddrivingassistant.db.LocalDbManager
import com.example.advanceddrivingassistant.dto.CarDataDto
import com.example.advanceddrivingassistant.obd.ObdCommandManager
import com.example.advanceddrivingassistant.obd.ObdCommandResult
import com.example.advanceddrivingassistant.utils.BluetoothServiceActions
import com.example.advanceddrivingassistant.utils.ConnectionStateServiceActions
import com.example.advanceddrivingassistant.utils.Constants
import com.example.advanceddrivingassistant.utils.DeviceConnectionState
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

    private val carData: CarDataDto = CarDataDto(0 ,0, 0.0,)

    private val coroutineDbScope = CoroutineScope(Dispatchers.IO)
    private var coroutineDbJob: Job? = null

    private lateinit var obdCommandManager: ObdCommandManager
    private val localDbManager: LocalDbManager = LocalDbManager(this)


    private val bluetoothConnectionManager: BluetoothConnectionManager = BluetoothConnectionManager(
        this
    ) { connectionState -> onDeviceStateChange(connectionState) }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        bluetoothConnectionManager.init()
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
            val notification = NotificationCompat.Builder(this, Constants.notificationChannelID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Monitoring driving events")
                .setContentText("Average fuel consumption: 1.00 l/100km")
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
        val intent = Intent()
        intent.action = result.name

        intent.putExtra("EXTRA_COMMAND_NAME", result.name)
        intent.putExtra("EXTRA_COMMAND_VALUE", result.value)
        intent.putExtra("EXTRA_COMMAND_UNIT", result.unit)

        this@BluetoothService.sendBroadcast(intent)

        when (result.name) {
            AvailableCommandNames.FUEL_LEVEL.value -> {
                carData.fuelLevel = result.value.toDoubleOrNull() ?: 0.0
            }
            AvailableCommandNames.ENGINE_RPM.value -> {
                carData.rpm = result.value.toIntOrNull() ?: 0
            }
            AvailableCommandNames.SPEED.value -> {
                carData.speed = result.value.toIntOrNull() ?: 0
            }
        }
    }
}