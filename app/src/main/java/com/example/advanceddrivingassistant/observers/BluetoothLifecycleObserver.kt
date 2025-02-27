package com.example.advanceddrivingassistant.observers

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class BluetoothLifecycleObserver(
    private val context: ComponentActivity,
    private val bluetoothSetupCallback: BluetoothSetupCallback,
) {

    private val loggingTag = "BluetoothSetupObserver"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothEnableLauncher: ActivityResultLauncher<Intent>
    private var bluetoothPermissionsLauncher: ActivityResultLauncher<Array<String>>

    init {
        val bluetoothManager =
            context.getSystemService(Activity.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        bluetoothEnableLauncher = context.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode == Activity.RESULT_OK) {
                bluetoothSetupCallback.bluetoothTurnedOn()
            } else {
                bluetoothSetupCallback.bluetoothRequestCancelled()
            }
        }

        bluetoothPermissionsLauncher = context.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                enableBluetooth()
                bluetoothSetupCallback.bluetoothPermissionsGranted()
            } else {
                bluetoothSetupCallback.bluetoothPermissionsRefused()
            }
        }
    }

    private fun enableBluetooth() {
        Log.d(loggingTag, "enableBluetooth: " + bluetoothAdapter?.isEnabled)

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        } else {
            bluetoothSetupCallback.bluetoothTurnedOn()
        }
    }

    fun requestBluetoothPermissions() {
        if ((ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED)
            || (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            val permissionsToRequest = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            bluetoothPermissionsLauncher.launch(permissionsToRequest)
        } else {
            bluetoothSetupCallback.bluetoothPermissionsGranted()
            enableBluetooth()
        }
    }

    interface BluetoothSetupCallback {
        fun bluetoothTurnedOn()
        fun bluetoothRequestCancelled()
        fun bluetoothPermissionsGranted()
        fun bluetoothPermissionsRefused()
    }
}
