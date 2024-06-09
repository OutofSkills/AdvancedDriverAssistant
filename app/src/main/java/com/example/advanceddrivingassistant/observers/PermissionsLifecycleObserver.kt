package com.example.advanceddrivingassistant.observers

import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class PermissionsLifecycleObserver(
    private val context: ComponentActivity,
    private val permissionsSetupCallback: PermissionsSetupCallback,
) : DefaultLifecycleObserver {

    private val loggingTag = "PermissionsLifecycleObserver"

    private lateinit var notificationObserver: NotificationLifecycleObserver
    private lateinit var bluetoothObserver: BluetoothLifecycleObserver

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        // Initialize observers
        notificationObserver = NotificationLifecycleObserver(context, object : NotificationLifecycleObserver.NotificationSetupCallback {
            override fun notificationsPermissionsGranted() {
                requestBluetoothPermissions()
            }

            override fun notificationsPermissionsRefused() {
                permissionsSetupCallback.notificationsPermissionsRefused()
            }
        })

        bluetoothObserver = BluetoothLifecycleObserver(context, object : BluetoothLifecycleObserver.BluetoothSetupCallback {
            override fun bluetoothTurnedOn() {
                permissionsSetupCallback.bluetoothTurnedOn()
            }

            override fun bluetoothRequestCancelled() {
                permissionsSetupCallback.bluetoothRequestCancelled()
            }

            override fun bluetoothPermissionsGranted() {
                permissionsSetupCallback.bluetoothPermissionsGranted()
            }

            override fun bluetoothPermissionsRefused() {
                permissionsSetupCallback.bluetoothPermissionsRefused()
            }
        })

        // Start requesting notification permissions
        notificationObserver.requestNotificationsPermissions()
    }

    private fun requestBluetoothPermissions() {
        bluetoothObserver.requestBluetoothPermissions()
    }

    interface PermissionsSetupCallback {
        fun notificationsPermissionsRefused()
        fun bluetoothTurnedOn()
        fun bluetoothRequestCancelled()
        fun bluetoothPermissionsGranted()
        fun bluetoothPermissionsRefused()
    }
}
