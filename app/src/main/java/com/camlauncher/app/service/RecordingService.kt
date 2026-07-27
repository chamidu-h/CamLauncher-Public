package com.camlauncher.app.service

import android.content.Context
import android.content.Intent
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleService
import com.camlauncher.app.data.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Implementation provided by the closed-source core module.
 * This class interfaces with the open-core UI layer.
 */
class RecordingService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_START = "com.camlauncher.ACTION_START"
        const val ACTION_STOP = "com.camlauncher.ACTION_STOP"
        const val ACTION_PHOTO = "com.camlauncher.ACTION_PHOTO"

        var instance: RecordingService? = null

        private val _stateFlow = MutableStateFlow(RecordingState.STOPPED)
        val stateFlow: StateFlow<RecordingState> = _stateFlow

        private val _currentLensFlow = MutableStateFlow(0)
        val currentLensFlow: StateFlow<Int> = _currentLensFlow

        private val _currentZoomRatioFlow = MutableStateFlow(1.0f)
        val currentZoomRatioFlow: StateFlow<Float> = _currentZoomRatioFlow

        fun startRecording(context: Context) {
            // Implementation hidden
        }

        fun stopRecording(context: Context) {
            // Implementation hidden
        }

        fun onRecorderActivityCreated(activity: androidx.activity.ComponentActivity) {
            // Implementation hidden
        }

        fun onRecorderActivityDestroyed(activity: androidx.activity.ComponentActivity) {
            // Implementation hidden
        }

        fun attachPreviewView(activity: androidx.activity.ComponentActivity) {
            // Implementation hidden
        }

        fun flipCamera() {
            // Implementation hidden
        }

        fun setLiveZoom(ratio: Float) {
            // Implementation hidden
        }
    }
}
