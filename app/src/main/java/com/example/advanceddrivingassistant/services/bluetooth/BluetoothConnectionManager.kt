package com.example.advanceddrivingassistant.services.bluetooth

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.advanceddrivingassistant.utils.Constants.standardUUID
import com.example.advanceddrivingassistant.utils.DeviceConnectionState
import com.example.advanceddrivingassistant.utils.logToFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class BluetoothConnectionManager(
    private val context: Context,
    private val onConnectionStateChanged: (DeviceConnectionState) -> Unit,
) {
    private val loggingTag = "BluetoothConnectionManager"
    private val connectionCoroutineScope = CoroutineScope(Dispatchers.IO);
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null

    fun init() {
        val bluetoothManager =
            context.getSystemService(Service.BLUETOOTH_SERVICE) as BluetoothManager;
        bluetoothAdapter = bluetoothManager.adapter;
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        try {
            bluetoothAdapter?.startDiscovery()
        } catch (e: Exception) {
            logToFile(
                context,
                loggingTag,
                "[startDiscovery] Error starting discovery: ${e.message}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: Exception) {
            logToFile(context, loggingTag, "[stopDiscovery] Error stopping discovery: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun openSocketConnection(bluetoothDevice: BluetoothDevice) = flow {
        emit(DeviceConnectionState.Connecting(bluetoothDevice))
        stopDiscovery()

        try {
            socket?.close()
            socket =
                bluetoothDevice.createInsecureRfcommSocketToServiceRecord(standardUUID)?.also {
                    it.connect()
                }
            socket?.let { emit(DeviceConnectionState.Connected(it)) }
        } catch (e: Exception) {
            emit(DeviceConnectionState.ConnectionFailed(e.message ?: "Failed to connect"))
            logToFile(
                context,
                loggingTag,
                "[openSocketConnection] Error connecting to device: ${e.message}"
            )
        }
    }.flowOn(Dispatchers.IO)

    fun connectToDevice(device: BluetoothDevice?) {
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device?.address)

        if (bluetoothDevice == null) {
            logToFile(context, loggingTag, "[connectToDevice] Device not found")
            onConnectionStateChanged(DeviceConnectionState.ConnectionFailed("Device not found"))
            return
        }

        connectionCoroutineScope.launch(Dispatchers.IO) {
            openSocketConnection(bluetoothDevice).collect { state ->
                logToFile(context, loggingTag, "[connectToDevice] Connection state: $state")
                onConnectionStateChanged(state)
            }
        }
    }

    fun onCleanup() {
        stopDiscovery()
        connectionCoroutineScope.cancel()
        socket?.close()
    }
}