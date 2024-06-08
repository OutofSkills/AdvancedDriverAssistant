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
import com.example.advanceddrivingassistant.utils.BluetoothServiceActions
import com.example.advanceddrivingassistant.utils.ConnectionStateServiceActions
import com.example.advanceddrivingassistant.utils.Constants
import com.example.advanceddrivingassistant.utils.DeviceConnectionState
import com.example.advanceddrivingassistant.utils.logToFile

class BluetoothService : Service() {

    private val loggingContext = "BluetoothServiceTag"

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
        bluetoothConnectionManager.onCleanup()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            BluetoothServiceActions.START_DISCOVERY.toString() -> startDiscovery()
            BluetoothServiceActions.DEVICE_CONNECT.toString() -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothServiceActions.DEVICE_CONNECT.toString())
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

    private fun stopDiscovery() {
        bluetoothConnectionManager.stopDiscovery()
    }

    private fun connect(device: BluetoothDevice?) {
        if (device == null) {
            logToFile(this, loggingContext, "[connect] Device null")
        }

        bluetoothConnectionManager.connectToDevice(device)
    }

    private fun disconnect() {

    }

    private fun onDeviceStateChange(connectionState: DeviceConnectionState) {
        Log.d(loggingContext, "onDeviceStateChange")
        val intent = Intent()
        intent.action = connectionState.toString()

        when (connectionState) {
            is DeviceConnectionState.Connected -> {
                intent.action = ConnectionStateServiceActions.ACTION_DEVICE_CONNECTED.toString()
                intent.putExtra("EXTRA_DEVICE_ADDRESS", connectionState.socket.remoteDevice.address)
            }
            is DeviceConnectionState.Disconnected -> {
                intent.action = ConnectionStateServiceActions.ACTION_DEVICE_DISCONNECTED.toString()
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
}