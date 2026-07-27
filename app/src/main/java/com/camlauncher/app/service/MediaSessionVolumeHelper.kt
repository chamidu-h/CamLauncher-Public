package com.camlauncher.app.service

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent

/**
 * Uses a MediaSession to intercept volume key events globally.
 * 
 * Android routes hardware volume keys through the active MediaSession.
 * By creating a session with active playback state, we can intercept 
 * VOLUME_DOWN presses even when the accessibility onKeyEvent is suppressed 
 * by MIUI or other aggressive OEMs.
 * 
 * NOTE: This does NOT prevent normal volume adjustment — it simply 
 * gives us a callback alongside the system volume change.
 */
class MediaSessionVolumeHelper(
    private val context: Context,
    private val receiverClass: Class<*>,
    private val tag: String = "CamLauncherVolume",
    private val onVolumeKeyDown: () -> Unit
) {
    private var mediaSession: MediaSessionCompat? = null

    fun start() {
        try {
            val componentName = ComponentName(context, receiverClass)
            mediaSession = MediaSessionCompat(context, tag, componentName, null).apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onMediaButtonEvent(mediaButtonEvent: android.content.Intent?): Boolean {
                        val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(android.content.Intent.EXTRA_KEY_EVENT)
                        Log.d(TAG, "MediaButton event: keyCode=${keyEvent?.keyCode}, action=${keyEvent?.action}")
                        if (keyEvent?.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && 
                            keyEvent.action == KeyEvent.ACTION_DOWN) {
                            onVolumeKeyDown()
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }
                })

                // Set playback state to PLAYING so that media key routing considers us
                val state = PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .setState(
                        PlaybackStateCompat.STATE_PLAYING,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        0f,
                        SystemClock.elapsedRealtime()
                    )
                    .build()
                setPlaybackState(state)
                isActive = true
            }
            Log.d(TAG, "MediaSession started and active")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaSession", e)
        }
    }

    fun stop() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
            Log.d(TAG, "MediaSession stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop MediaSession", e)
        }
    }

    companion object {
        private const val TAG = "MediaSessionVolume"
    }
}
