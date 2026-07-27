package com.camlauncher.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.camlauncher.app.R
import com.camlauncher.app.data.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * A lightweight widget to display and cycle the current Trigger Mode.
 */
class TriggerModeWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
            } catch (e: Exception) {
                // Ignore
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.camlauncher.app.widget.ACTION_REFRESH"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TriggerModeWidget::class.java))
            CoroutineScope(Dispatchers.IO).launch {
                ids.forEach { updateWidget(context, manager, it) }
            }
        }

        private suspend fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            // Read settings asynchronously
            val settingsStore = com.camlauncher.app.data.SettingsStore(context.applicationContext)
            val currentMode = settingsStore.triggerType.first()

            val views = RemoteViews(context.packageName, R.layout.widget_trigger_mode)
            views.setTextViewText(R.id.widget_mode_value, currentMode.displayName)

            // Dynamic styling based on mode, but title remains static
            views.setTextViewText(R.id.widget_title, "CamLauncher")
            
            if (currentMode == TriggerType.BUTTON) {
                views.setTextColor(R.id.widget_mode_value, Color.parseColor("#AAAAAA"))
            } else {
                views.setTextColor(R.id.widget_mode_value, Color.parseColor("#00FF00"))
            }

            // Click listener for the CHANGE button
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_CYCLE_TRIGGER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_cycle, pendingIntent)
            
            manager.updateAppWidget(widgetId, views)
        }
    }
}
