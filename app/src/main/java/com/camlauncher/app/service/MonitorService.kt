package com.camlauncher.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.camlauncher.app.CamLauncherApp
import com.camlauncher.app.data.SettingsStore
import com.camlauncher.app.data.TriggerType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume

/**
 * Persistent foreground service that keeps the CamLauncher process alive.
 *
 * On MIUI and other aggressive OEMs, swiping an app from "Recents" kills
 * the entire process — including the AccessibilityService. A foreground 
 * service with a visible notification makes the process a "foreground" 
 * process in the eyes of the OS, which:
 * 1. Prevents MIUI from killing it when swiped from Recents
 * 2. Gives it the highest OOM-adjustment priority (oom_adj = 0)
 * 3. If still killed under extreme memory pressure, START_STICKY
 * tells the system to restart it automatically
 *
 * Additionally, this service registers a VolumeBroadcastReceiver to catch
 * volume key presses even during deep sleep (screen completely off). Since
 * this service has foreground priority, its broadcast receiver is more
 * reliably woken by the system than a background component.
 *
 * It also holds a PARTIAL_WAKE_LOCK to keep the CPU responsive enough
 * to process volume change broadcasts during deep sleep.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var volumeBroadcast: VolumeBroadcastReceiver? = null
    private var mediaSessionHelper: MediaSessionVolumeHelper? = null
    private var sensor: SensorTriggerManager? = null
    private var activeTriggerType: TriggerType = TriggerType.VOLUME_DOUBLE_PRESS

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MonitorService created")

        // Acquire a partial wake lock to keep CPU alive for broadcast delivery
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CamLauncher:MonitorWL")
        try { wakeLock?.acquire() } catch (_: Exception) {}

        // Start volume hooks once — they stay alive across all modes.
        // BUTTON mode gates the callbacks via activeTriggerType.
        startVolumeBroadcast()
        startMediaSessionHelper()

        // Track trigger type changes (for gating callbacks)
        setupTriggerTypeTracking()

        // Setup sensor-based trigger
        sensor = SensorTriggerManager(this) { fireTrigger() }
        setupShakeTrigger()
    }

    private fun setupShakeTrigger() {
        scope.launch {
            val settings = SettingsStore(this@MonitorService)
            settings.triggerType.collect { type ->
                if (type == TriggerType.SHAKE) {
                    sensor?.start()
                } else {
                    sensor?.stop()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MonitorService started (startId=$startId)")
        
        // Android 12+ / 14+ Synchronicity: IMMEDIATELY call startForeground here 
        // with a generic status. We'll update the text in showForegroundNotification()
        // after reading the settings.
        val openAppPi = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, com.camlauncher.app.ui.MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val initialNotification = androidx.core.app.NotificationCompat.Builder(this, com.camlauncher.app.CamLauncherApp.CHANNEL_MONITOR)
            .setContentTitle("Emergency Recording Active")
            .setContentText("Checking trigger settings...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(openAppPi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .build()
            
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, initialNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync startForeground failed", e)
        }

        showForegroundNotification()
        return START_STICKY
    }

    private fun setupTriggerTypeTracking() {
        scope.launch {
            val settings = SettingsStore(this@MonitorService)
            settings.triggerType.collect { type ->
                activeTriggerType = type
                Log.d(TAG, "Trigger type changed to: $type")
                if (type == com.camlauncher.app.data.TriggerType.BUTTON) {
                    Log.d(TAG, "Trigger type is BUTTON. Stopping MonitorService to save battery.")
                    stopSelf()
                }
            }
        }
    }

    private fun startMediaSessionHelper() {
        if (mediaSessionHelper != null) return
        mediaSessionHelper = MediaSessionVolumeHelper(
            this, this.javaClass,
            tag = "CamLauncherVol_Monitor"
        ) { onVolumeDetected("MediaSession") }
        mediaSessionHelper?.start()
    }

    private fun stopMediaSessionHelper() {
        mediaSessionHelper?.stop()
        mediaSessionHelper = null
    }

    private fun stopVolumeBroadcast() {
        volumeBroadcast?.unregister(this)
        volumeBroadcast = null
    }

    private fun startVolumeBroadcast() {
        if (volumeBroadcast != null) return
        volumeBroadcast = VolumeBroadcastReceiver { onVolumeDetected("Broadcast") }
        volumeBroadcast?.register(this)
        Log.d(TAG, "MonitorService VolumeBroadcastReceiver registered")
    }

    private fun onVolumeDetected(source: String) {
        // Gate: ignore volume events when not in a volume trigger mode
        val type = activeTriggerType
        if (type != TriggerType.VOLUME_DOUBLE_PRESS && type != TriggerType.VOLUME_TRIPLE_PRESS) return

        val now = System.currentTimeMillis()
        val rawGap = now - lastVolEventMs

        // 1. Double-intent suppression (from different sources)
        if (rawGap < 40L) return

        val previousEventMs = lastVolEventMs
        lastVolEventMs = now

        // 2. Hardware auto-repeat suppression (holding down button)
        if (previousEventMs > 0L && rawGap < HOLD_REPEAT_MS) {
            Log.d(TAG, "Hold auto-repeat in MonitorSvc (source=$source, gap=${rawGap}ms): Resetting")
            pressCount = 0
            pressResetJob?.cancel()
            return
        }

        Log.d(TAG, "Volume DOWN from MonitorSvc $source (Valid Press)")
        volumeDownCount++

        // 3. Gesture State Machine (Synchronous for low latency)
        val required = if (activeTriggerType == TriggerType.VOLUME_TRIPLE_PRESS) 3 else 2
        val gap = now - lastVolTime
        lastVolTime = now

        if (gap < GESTURE_WINDOW_MS) {
            pressCount++
            pressResetJob?.cancel()
            if (pressCount >= required) {
                pressCount = 0
                fireTrigger()
            } else {
                pressResetJob = scope.launch { delay(GESTURE_WINDOW_MS); pressCount = 0 }
            }
        } else {
            pressCount = 1
            pressResetJob?.cancel()
            pressResetJob = scope.launch { delay(GESTURE_WINDOW_MS); pressCount = 0 }
        }
    }

    private fun fireTrigger() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < 3000L) return
        lastTriggerMs = now

        val currentState = com.camlauncher.app.data.RecordingState.RECORDING
        val recState = RecordingService.stateFlow.value
        Log.d(TAG, "MonitorService TRIGGER FIRED — recState=$recState")
        val taskAction = if (recState == currentState) "STOP" else "START"

        scope.launch {
            // Check Pocket State upstream BEFORE initiating vibration or recording
            if (taskAction == "START") {
                val settingsStore = SettingsStore(this@MonitorService)
                val isAntiPocket = settingsStore.antiPocketEnabled.first()
                if (isAntiPocket) {
                    val isCovered = checkProximity(this@MonitorService)
                    if (isCovered) {
                        Log.w(TAG, "Anti-pocket blocked trigger")
                        return@launch
                    }
                }
            }

            // Vibrate post-verification
            try {
                val shouldVibrate = withTimeoutOrNull(500) { SettingsStore(this@MonitorService).vibrationOnTrigger.first() } != false
                if (shouldVibrate) {
                    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                        vm.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    }
                    val pattern = if (taskAction == "STOP") longArrayOf(0, 300) else longArrayOf(0, 150, 50, 150)
                    val attrs = android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1), attrs)
                }
            } catch (_: Exception) {}

            if (recState == com.camlauncher.app.data.RecordingState.STARTING ||
                recState == com.camlauncher.app.data.RecordingState.STOPPING) return@launch

            // --- THE CRITICAL FIX ---
            // On Android 14+, you CANNOT start a Foreground Service (RecordingService)
            // directly from a background Foreground Service (MonitorService) if you need
            // Camera/Mic permissions. You MUST launch an activity first to acquire focus.
            try {
                val intent = Intent(this@MonitorService, com.camlauncher.app.ui.TriggerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("action", taskAction) // Pass the EXPLICIT action (START/STOP)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Bridge activity failed", e)
                // Fallback: If activity launch fails, try starting service directly 
                // but this will likely fail on Android 14.
                if (taskAction == "STOP") RecordingService.stopRecording(this@MonitorService)
                else RecordingService.startRecording(this@MonitorService)
            }
        }
    }


    private suspend fun checkProximity(context: Context): Boolean = suspendCancellableCoroutine { cont ->
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val proximitySensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)

        if (proximitySensor == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        var listener: android.hardware.SensorEventListener? = null
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        val timeoutRunnable = Runnable {
            if (cont.isActive) {
                listener?.let { sensorManager.unregisterListener(it) }
                cont.resume(false)
            }
        }

        listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                if (cont.isActive) {
                    handler.removeCallbacks(timeoutRunnable)
                    sensorManager.unregisterListener(this)
                    val isCovered = event.values[0] < proximitySensor.maximumRange
                    cont.resume(isCovered)
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, proximitySensor, android.hardware.SensorManager.SENSOR_DELAY_FASTEST)
        handler.postDelayed(timeoutRunnable, 250L)
    }

    // Volume press tracking state (for MonitorService's own broadcast receiver)
    private var lastVolTime = 0L
    private var lastVolEventMs = 0L
    private var lastTriggerMs = 0L
    private var pressCount = 0
    private var volumeDownCount = 0
    private var pressResetJob: Job? = null
    private val GESTURE_WINDOW_MS = 400L
    private val HOLD_REPEAT_MS = 110L

    private fun showForegroundNotification() {
        scope.launch {
            val settings = SettingsStore(this@MonitorService)
            val type = try {
                withTimeoutOrNull(2000) { settings.triggerType.first() } 
                    ?: TriggerType.VOLUME_DOUBLE_PRESS
            } catch (_: Exception) {
                TriggerType.VOLUME_DOUBLE_PRESS
            }

            val triggerDesc = when (type) {
                TriggerType.VOLUME_DOUBLE_PRESS -> "Double-press Vol Down to trigger"
                TriggerType.VOLUME_TRIPLE_PRESS -> "Triple-press Vol Down to trigger"
                TriggerType.SHAKE -> "Shake 3 times to trigger"
                TriggerType.BUTTON -> "In-App Button Only"
            }

            val openAppPi = android.app.PendingIntent.getActivity(
                this@MonitorService, 0,
                Intent(this@MonitorService, com.camlauncher.app.ui.MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(
                this@MonitorService, CamLauncherApp.CHANNEL_MONITOR
            )
                .setContentTitle("Emergency Recording Active")
                .setContentText(triggerDesc)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentIntent(openAppPi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
                .build()

            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d(TAG, "Foreground notification updated: $triggerDesc")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update notification", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MonitorService destroyed — will be restarted by START_STICKY")
        volumeBroadcast?.unregister(this)
        mediaSessionHelper?.stop()
        sensor?.stop()
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        scope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed (swiped from recents) — service stays alive via startForeground")
        showForegroundNotification()
    }

    companion object {
        private const val TAG = "MonitorSvc"
        const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            try {
                val intent = Intent(context, MonitorService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "MonitorService start requested")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MonitorService", e)
            }
        }
    }
}