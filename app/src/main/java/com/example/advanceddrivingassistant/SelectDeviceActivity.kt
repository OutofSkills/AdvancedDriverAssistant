package com.example.advanceddrivingassistant

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.advanceddrivingassistant.components.OverlaySpinner
import com.example.advanceddrivingassistant.observers.NotificationLifecycleObserver
import com.example.advanceddrivingassistant.observers.PermissionsLifecycleObserver
import com.example.advanceddrivingassistant.services.bluetooth.BluetoothService
import com.example.advanceddrivingassistant.ui.theme.AdvancedDrivingAssistantTheme
import com.example.advanceddrivingassistant.utils.BluetoothServiceActions
import com.example.advanceddrivingassistant.utils.ConnectionStateServiceActions
import com.example.advanceddrivingassistant.utils.logToFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SelectDeviceActivity : ComponentActivity(), PermissionsLifecycleObserver.PermissionsSetupCallback {
    private val loggingTag = "SelectDeviceActivityTag"

    private val bluetoothDevices = mutableStateOf<List<BluetoothDevice?>>(emptyList())
    private val isBluetoothDiscovering = mutableStateOf(false)

    private val isBluetoothConnecting = mutableStateOf(false)
    private val connectionMessage = mutableStateOf("")

    private val bluetoothStateReceiverIntentFilter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    }

    private val deviceConnectionStateReceiverIntentFilter = IntentFilter().apply {
        addAction(ConnectionStateServiceActions.ACTION_DEVICE_CONNECTED.toString())
        addAction(ConnectionStateServiceActions.ACTION_DEVICE_DISCONNECTED.toString())
        addAction(ConnectionStateServiceActions.ACTION_DEVICE_CONNECTING.toString())
        addAction(ConnectionStateServiceActions.ACTION_CONNECTION_FAILED.toString())
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    logToFile(context, loggingTag, "[BroadcastReceiver] Bluetooth started scanning")
                    isBluetoothDiscovering.value = true
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    logToFile(
                        context,
                        loggingTag,
                        "[BroadcastReceiver] Bluetooth finished scanning"
                    )
                    isBluetoothDiscovering.value = false
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    logToFile(context, loggingTag, "[BroadcastReceiver] Bluetooth state changed")
                    val data = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    );
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!

                    if (!bluetoothDevices.value.contains(device)) {
                        bluetoothDevices.value += device
                    }

                    logToFile(
                        context,
                        loggingTag,
                        "[BroadcastReceiver] Bluetooth device found: ${device?.name}"
                    )
                }

                else -> {
                    logToFile(
                        context,
                        loggingTag,
                        "[BroadcastReceiver] Unknown action: ${intent.action}"
                    )
                }
            }
        }
    }

    private val deviceConnectionStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(loggingTag, "Received intent: $intent")
            intent?.let {
                val action = it.action
                when (action) {
                    "ACTION_DEVICE_CONNECTED" -> {
                        val deviceAddress = it.getStringExtra("EXTRA_DEVICE_ADDRESS")
                        handleDeviceConnected(deviceAddress)
                    }
                    "ACTION_DEVICE_DISCONNECTED" -> {
                        handleDeviceDisconnected()
                    }
                    "ACTION_DEVICE_CONNECTING" -> {
                        val deviceAddress = it.getStringExtra("EXTRA_DEVICE_ADDRESS")
                        handleDeviceConnecting(deviceAddress)
                    }
                    "ACTION_CONNECTION_FAILED" -> {
                        val failureReason = it.getStringExtra("EXTRA_FAILURE_REASON")
                        handleConnectionFailed(failureReason)
                    }
                }
            }
        }
    }

    private lateinit var permissionsObserver: PermissionsLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdvancedDrivingAssistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val isDiscoveryActive by remember { isBluetoothDiscovering }
                    val spinnerMessage by remember { connectionMessage }
                    val isSpinnerDisplayed by remember { isBluetoothConnecting }

                    SelectDeviceScreen(
                        modifier = Modifier.padding(innerPadding),
                        availableDevices = bluetoothDevices.value,
                        isDiscoveryActive = isDiscoveryActive,
                        onDeviceSelected = { onDeviceSelected(it) },
                        startDiscovery = { startDiscovery() },
                    )

                    OverlaySpinner(message = spinnerMessage, isDisplayed = isSpinnerDisplayed)
                }
            }
        }

        registerReceiver(bluetoothStateReceiver, bluetoothStateReceiverIntentFilter)
        ContextCompat.registerReceiver(
            this,
            deviceConnectionStateReceiver,
            deviceConnectionStateReceiverIntentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )

        permissionsObserver = PermissionsLifecycleObserver(this, this)
        lifecycle.addObserver(permissionsObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
        unregisterReceiver(deviceConnectionStateReceiver)
        lifecycle.removeObserver(permissionsObserver)
    }

    private fun startDiscovery() {
        bluetoothDevices.value = emptyList()
        try {
            Intent(applicationContext, BluetoothService::class.java).also {
                it.action = BluetoothServiceActions.START_DISCOVERY.toString()
                startService(it)
            }
        } catch (e: Exception) {
            logToFile(this, loggingTag, "Error starting discovery: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun onDeviceSelected(device: BluetoothDevice?) {
        Log.d(loggingTag, "Device selected: $device")
        Intent(applicationContext, BluetoothService::class.java).also {
            it.action = BluetoothServiceActions.DEVICE_CONNECT.toString()
            it.putExtra(BluetoothServiceActions.DEVICE_CONNECT.toString(), device)
            startService(it)
        }
    }

    private fun handleDeviceConnected(deviceAddress: String?) {
        isBluetoothConnecting.value = false
        connectionMessage.value = ""

        Intent(this, DashboardActivity::class.java).also {
            it.putExtra("device_address_key", deviceAddress)
            startActivity(it)
        }
    }

    private fun handleDeviceDisconnected() {
        isBluetoothConnecting.value = false
        connectionMessage.value = ""
    }

    private fun handleDeviceConnecting(deviceAddress: String?) {
        isBluetoothConnecting.value = true
        connectionMessage.value = "Connecting to device..."
    }

    private fun handleConnectionFailed(failureReason: String?) {
        isBluetoothConnecting.value = false
        connectionMessage.value = ""
        Toast.makeText(this, "Connection failed. Make sure you selected the correct device.", Toast.LENGTH_SHORT).show()
    }

    override fun notificationsPermissionsRefused() {
        logToFile(this@SelectDeviceActivity, loggingTag, "Notifications permissions refused")
    }

    override fun bluetoothTurnedOn() {
        logToFile(this@SelectDeviceActivity, loggingTag, "Bluetooth is turned on")

        Intent(applicationContext, BluetoothService::class.java).also {
            it.action = BluetoothServiceActions.START_SERVICE.toString()
            startForegroundService(it)
        }.also {
            startDiscovery()
        }
    }

    override fun bluetoothRequestCancelled() {
        logToFile(
            this@SelectDeviceActivity,
            loggingTag,
            "Bluetooth turn on request cancelled"
        )
        // TODO: Show a message in app and probably a button to retry
    }

    override fun bluetoothPermissionsGranted() {
        logToFile(this@SelectDeviceActivity, loggingTag, "Bluetooth permissions granted")
    }

    override fun bluetoothPermissionsRefused() {
        logToFile(this@SelectDeviceActivity, loggingTag, "Bluetooth permissions refused")
    }
}

@Composable
fun SelectDeviceScreen(
    modifier: Modifier = Modifier,
    availableDevices: List<BluetoothDevice?>,
    isDiscoveryActive: Boolean,
    onDeviceSelected: (BluetoothDevice?) -> Unit,
    startDiscovery: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "Select Device",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.CenterHorizontally),
        )

        // Description
        Text(
            text = "Please select your OBD-II Bluetooth device from the list below.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Loader or Feedback
        if (isDiscoveryActive) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        } else {
            Text(
                text = "Discovery paused. Tap to refresh.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
                    .clickable { startDiscovery() }
            )
        }

        // List of available Bluetooth devices
        LazyColumn {
            items(availableDevices) { device ->
                DeviceItem(device = device, onDeviceSelected = onDeviceSelected)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice?, onDeviceSelected: (BluetoothDevice?) -> Unit) {
    val iconDrawable = if (device?.name === null) R.drawable.outline_device_unknown_24
    else R.drawable.smartphone

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onDeviceSelected(device) }
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = ImageVector.vectorResource(iconDrawable),
                contentDescription = "Bluetooth Device",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = device?.name ?: "Unknown device",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSelectDeviceScreen() {
    SelectDeviceScreen(
        availableDevices = emptyList(),
        isDiscoveryActive = false,
        onDeviceSelected = {}
    )
}