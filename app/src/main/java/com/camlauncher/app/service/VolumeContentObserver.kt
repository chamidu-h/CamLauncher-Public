package com.camlauncher.app.service

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Detects volume changes by observing the system Settings database.
 *
 * When the screen is completely off (sleep / no AOD), neither
 * AccessibilityService.onKeyEvent() nor MediaSession callbacks
 * reliably fire on all devices. However, pressing volume buttons
 * ALWAYS updates android.provider.Settings.System, which triggers
 * this ContentObserver — even with the screen dead off.
 *
 * This observer watches the STREAM_MUSIC volume. Each time it
 * changes, we call [onVolumeChanged]. The multi-press logic
 * is handled by GestureTriggerService.handleVolumePress().
 */
class VolumeContentObserver(
    private val context: Context,
    private val onVolumeChanged: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var lastKnownVolume: Int = -1
    private var registered = false

    fun start() {
        if (registered) return
        lastKnownVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            context.contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                this
            )
            registered = true
            Log.d(TAG, "VolumeContentObserver registered (initial volume=$lastKnownVolume)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register VolumeContentObserver", e)
        }
    }

    fun stop() {
        if (!registered) return
        try {
            context.contentResolver.unregisterContentObserver(this)
            registered = false
            Log.d(TAG, "VolumeContentObserver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister VolumeContentObserver", e)
        }
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        // Only fire if volume actually decreased (volume DOWN button)
        if (currentVolume < lastKnownVolume) {
            Log.d(TAG, "Volume DOWN detected via ContentObserver: $lastKnownVolume → $currentVolume")
            onVolumeChanged()
        }
        lastKnownVolume = currentVolume
    }

    companion object {
        private const val TAG = "VolumeObserver"
    }
}
