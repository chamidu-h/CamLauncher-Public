package com.camlauncher.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CamLauncherApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val recordingChannel = NotificationChannel(
            CHANNEL_RECORDING,
            getString(R.string.notification_channel_recording),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when CamLauncher is actively recording"
            setShowBadge(false)
        }

        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            getString(R.string.notification_channel_status),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Recording save confirmations and status updates"
        }

        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR,
            "Background Monitoring",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps the emergency triggers active in the background"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(recordingChannel)
        notificationManager.createNotificationChannel(statusChannel)
        notificationManager.createNotificationChannel(monitorChannel)
    }

    companion object {
        const val CHANNEL_RECORDING = "recording_channel"
        const val CHANNEL_STATUS = "status_channel"
        const val CHANNEL_MONITOR = "monitor_channel"
    }
}
