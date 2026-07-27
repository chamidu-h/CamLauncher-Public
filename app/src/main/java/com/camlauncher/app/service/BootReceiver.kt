package com.camlauncher.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Ensures the app wakes up after a device reboot or app update.
 * Starts the MonitorService which keeps the process alive so that
 * GestureTriggerService (AccessibilityService) can intercept triggers.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Boot/update detected (action=$action). Starting MonitorService...")
            MonitorService.start(context)
        }
    }
}
