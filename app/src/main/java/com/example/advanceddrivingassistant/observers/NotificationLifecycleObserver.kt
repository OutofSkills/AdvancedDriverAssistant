package com.example.advanceddrivingassistant.observers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class NotificationLifecycleObserver(
    private val context: ComponentActivity,
    private val notificationSetupCallback: NotificationSetupCallback,
) : DefaultLifecycleObserver {

    private val loggingTag = "NotificationLifecycleObserver";
    private lateinit var notificationsPermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        notificationsPermissionsLauncher = context.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                notificationSetupCallback.notificationsPermissionsGranted()
            } else {
                notificationSetupCallback.notificationsPermissionsRefused()
            }
        }

        requestNotificationsPermissions()
    }

    private fun requestNotificationsPermissions() {
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

