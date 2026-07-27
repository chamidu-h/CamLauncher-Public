package com.camlauncher.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.camlauncher.app.data.SettingsStore
import com.camlauncher.app.data.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles intent actions from the widget, performing asynchronous 
 * DataStore writes without blocking the main thread (which would cause ANRs).
 */
class WidgetActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CYCLE_TRIGGER) return
        
        val pendingResult = goAsync()
        
        scope.launch {
            try {
                val settingsStore = SettingsStore(context.applicationContext)
                val currentMode = settingsStore.triggerType.first()
                
                // Cycle logic
                val nextMode = when (currentMode) {
                    TriggerType.VOLUME_DOUBLE_PRESS -> TriggerType.VOLUME_TRIPLE_PRESS
                    TriggerType.VOLUME_TRIPLE_PRESS -> TriggerType.SHAKE
                    TriggerType.SHAKE -> TriggerType.BUTTON
                    TriggerType.BUTTON -> TriggerType.VOLUME_DOUBLE_PRESS
                }
                
                Log.d("WidgetAction", "Cycling TriggerMode from ${currentMode.name} to ${nextMode.name}")
                settingsStore.setTriggerType(nextMode)
                
                // If it's a hardware trigger, ensure MonitorService starts.
                // (If it's BUTTON, MonitorService stops itself by collecting SettingsStore).
                if (nextMode != TriggerType.BUTTON) {
                    com.camlauncher.app.service.MonitorService.start(context)
                }
                
                // Refresh the widget UI
                TriggerModeWidget.refreshAll(context)
            } catch (e: Exception) {
                Log.e("WidgetAction", "Error cycling trigger mode", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CYCLE_TRIGGER = "com.camlauncher.app.widget.ACTION_CYCLE_TRIGGER"
    }
}
