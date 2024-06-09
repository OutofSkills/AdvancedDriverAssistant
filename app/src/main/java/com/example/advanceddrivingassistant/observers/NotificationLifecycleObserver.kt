package com.example.advanceddrivingassistant.observers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class NotificationLifecycleObserver(
private val context: ComponentActivity,
private val notificationSetupCallback: NotificationSetupCallback,
) {

    private val loggingTag = "NotificationLifecycleObserver"
    private var notificationsPermissionsLauncher: ActivityResultLauncher<Array<String>> = context.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            notificationSetupCallback.notificationsPermissionsGranted()
        } else {
            notificationSetupCallback.notificationsPermissionsRefused()
        }
    }

    fun requestNotificationsPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissionsToRequest = arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
            )
            notificationsPermissionsLauncher.launch(permissionsToRequest)
        } else {
            notificationSetupCallback.notificationsPermissionsGranted()
        }
    }

    interface NotificationSetupCallback {
        fun notificationsPermissionsGranted()
        fun notificationsPermissionsRefused()
    }
}
