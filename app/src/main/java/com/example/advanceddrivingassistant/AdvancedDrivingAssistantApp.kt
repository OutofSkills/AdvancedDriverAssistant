package com.example.advanceddrivingassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.advanceddrivingassistant.utils.Constants

class AdvancedDrivingAssistantApp: Application() {
    override fun onCreate() {
        super.onCreate()

        val channel = NotificationChannel(
            Constants.notificationChannelID,
            "Driving Assistant Notifications",
            NotificationManager.IMPORTANCE_DEFAULT,
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}