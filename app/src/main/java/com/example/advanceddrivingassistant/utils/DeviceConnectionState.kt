package com.example.advanceddrivingassistant.utils

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket

sealed class DeviceConnectionState {
    class Connecting(val bluetoothDevice: BluetoothDevice) : DeviceConnectionState()
    class Connected(val socket: BluetoothSocket) : DeviceConnectionState()
    class ConnectionFailed(val failureReason: String) : DeviceConnectionState()
    data object Disconnected : DeviceConnectionState()
}