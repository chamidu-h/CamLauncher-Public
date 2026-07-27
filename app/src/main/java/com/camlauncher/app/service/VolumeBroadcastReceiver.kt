package com.camlauncher.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Detects volume-down key presses via the system VOLUME_CHANGED_ACTION broadcast.
 *
 * This is the MOST RELIABLE method for detecting volume changes when the screen  
 * is completely off (deep sleep / no AOD). Unlike:
 *   - AccessibilityService.onKeyEvent → not delivered when screen is off
 *   - MediaSession callbacks → not routed when no active audio focus
 *   - ContentObserver on Settings.System → delayed or batched during Doze
 *
 * The system audio layer processes hardware volume keys at the kernel level and  
 * always broadcasts VOLUME_CHANGED_ACTION, even during deep sleep, as long as  
 * the receiver is registered and the process is alive (which MonitorService  
 * guarantees via startForeground).
 *
 * This receiver MUST be registered dynamically (not in the manifest) because  
 * VOLUME_CHANGED_ACTION is a protected broadcast on Android 8+.
 */
class VolumeBroadcastReceiver(
    private val onVolumeDown: () -> Unit
) : BroadcastReceiver() {

    private var lastVolume = -1
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            lastVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(this, filter)
            }
            registered = true
            Log.d(TAG, "Registered — tracking STREAM_MUSIC (current=$lastVolume)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register", e)
        }
    }

    fun unregister(context: Context) {
        if (!registered) return
        try {
            context.unregisterReceiver(this)
            registered = false
            Log.d(TAG, "Unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.media.VOLUME_CHANGED_ACTION") return

        val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
        val newVolume = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
        val prevVolume = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)

        // We only care about the main media/ring stream going DOWN
        // Stream types: STREAM_MUSIC=3, STREAM_RING=2, STREAM_NOTIFICATION=5
        if (streamType == AudioManager.STREAM_MUSIC || 
            streamType == AudioManager.STREAM_RING) {
            
            val volumeDecreased = when {
                prevVolume >= 0 && newVolume >= 0 -> newVolume < prevVolume
                newVolume >= 0 && lastVolume >= 0 -> newVolume < lastVolume
                else -> false
            }

            if (volumeDecreased) {
                Log.d(TAG, "Volume DOWN broadcast: stream=$streamType, $prevVolume→$newVolume")
                onVolumeDown()
            }

            if (newVolume >= 0) lastVolume = newVolume
        }
    }

    companion object {
        private const val TAG = "VolumeBroadcast"
    }
}
