package com.camlauncher.app.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.camlauncher.app.data.RecordingState
import com.camlauncher.app.service.RecordingService
import kotlinx.coroutines.launch

class TriggerActivity : ComponentActivity() {
    private var action: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        
        action = intent?.getStringExtra("action")
        
        // Android 14 requires focus to grant camera permissions to the resulting FGS.
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    override fun onResume() {
        super.onResume()

        if (action == null || action == "NONE") {
            finish()
            return
        }

        // FGS is safely started HERE, while the app is in the Top Foreground state
        when (action) {
            "START" -> RecordingService.startRecording(this)
            "STOP" -> RecordingService.stopRecording(this)
        }
        
        lifecycleScope.launch {
            RecordingService.stateFlow.collect { state ->
                if (state == RecordingState.RECORDING || state == RecordingState.ERROR) {
                    finish()
                }
            }
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing) finish()
        }, 5000)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        action = intent.getStringExtra("action")
        
        if (action != null && action != "NONE") {
            when (action) {
                "START" -> RecordingService.startRecording(this)
                "STOP" -> RecordingService.stopRecording(this)
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}