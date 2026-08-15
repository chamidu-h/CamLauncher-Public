package com.camlauncher.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.camlauncher.app.data.RecordingState
import com.camlauncher.app.data.SettingsStore
import com.camlauncher.app.data.TriggerType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume

class GestureTriggerService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var settings: SettingsStore
    private var sensor: SensorTriggerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSessionHelper: MediaSessionVolumeHelper? = null
    private var volumeBroadcast: VolumeBroadcastReceiver? = null
    private var activeTriggerType: TriggerType = TriggerType.VOLUME_DOUBLE_PRESS

    private var lastVolTime = 0L
    private var presses = 0
    private var pressJob: Job? = null
    private val lastEventMsMap = mutableMapOf<String, Long>()
    private var lastGlobalEventMs = 0L
    private val DEDUP_WINDOW_MS = 40L
    private val HOLD_REPEAT_MS = 110L

    @Volatile private var lastTriggerMs = 0L
    private val COOLDOWN_MS = 3000L
    private val GESTURE_WINDOW_MS = 400L

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        Log.d(TAG, "GestureTriggerService created")

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CamLauncher:GestureWL")

        sensor = SensorTriggerManager(this) { onTriggerFired() }

        // Always start volume hooks once — they stay alive across all modes.
        // BUTTON mode simply gates the callbacks via activeTriggerType.
        startMediaSessionHelper()
        startVolumeBroadcast()

        scope.launch {
            settings.triggerType.collect { type ->
                activeTriggerType = type
                Log.d(TAG, "Trigger type changed to: $type")

                val needSensor = type == TriggerType.SHAKE
                if (needSensor) sensor?.start() else sensor?.stop()

                // Keep MonitorService alive for all hardware trigger modes
                // (volume + shake). Only stop it for pure BUTTON mode.
                if (type != TriggerType.BUTTON) {
                    try { if (wakeLock?.isHeld != true) wakeLock?.acquire() } catch (_: Exception) {}
                    MonitorService.start(this@GestureTriggerService)
                } else {
                    try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun startMediaSessionHelper() {
        if (mediaSessionHelper != null) return
        try {
            mediaSessionHelper = MediaSessionVolumeHelper(
                this, this.javaClass,
                tag = "CamLauncherVol_Gesture"
            ) { onVolumeDetected("MediaSession") }
            mediaSessionHelper?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaSession helper", e)
        }
    }

    private fun stopMediaSessionHelper() {
        mediaSessionHelper?.stop()
        mediaSessionHelper = null
    }

    private fun startVolumeBroadcast() {
        if (volumeBroadcast != null) return
        volumeBroadcast = VolumeBroadcastReceiver { onVolumeDetected("Broadcast") }
        volumeBroadcast?.register(this)
    }

    private fun stopVolumeBroadcast() {
        volumeBroadcast?.unregister(this)
        volumeBroadcast = null
    }

    private fun onVolumeDetected(source: String, isRepeat: Boolean = false) {
        // Gate: ignore volume events when not in a volume trigger mode
        val type = activeTriggerType
        if (type != TriggerType.VOLUME_DOUBLE_PRESS && type != TriggerType.VOLUME_TRIPLE_PRESS) return

        val now = System.currentTimeMillis()

        if (isRepeat) {
            presses = 0; pressJob?.cancel(); return
        }

        val lastForSource = lastEventMsMap[source] ?: 0L
        val sourceGap = now - lastForSource

        if (lastForSource > 0L && sourceGap < 40L) return
        lastEventMsMap[source] = now

        if (lastForSource > 0L && sourceGap < HOLD_REPEAT_MS) {
            presses = 0; pressJob?.cancel(); return
        }

        val globalGap = now - lastGlobalEventMs
        if (globalGap < DEDUP_WINDOW_MS) return
        lastGlobalEventMs = now

        handleVolumePress(type)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try { if (wakeLock?.isHeld != true) wakeLock?.acquire() } catch (_: Exception) {}
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            onVolumeDetected("onKeyEvent", event.repeatCount > 0)
        }
        return super.onKeyEvent(event)
    }

    private fun handleVolumePress(type: TriggerType) {
        val now = System.currentTimeMillis()
        val required = if (type == TriggerType.VOLUME_TRIPLE_PRESS) 3 else 2
        val gap = now - lastVolTime
        lastVolTime = now

        if (gap < GESTURE_WINDOW_MS) {
            presses++
            pressJob?.cancel()
            if (presses >= required) {
                presses = 0
                onTriggerFired()
            } else {
                pressJob = scope.launch { delay(GESTURE_WINDOW_MS); presses = 0 }
            }
        } else {
            presses = 1
            pressJob?.cancel()
            pressJob = scope.launch { delay(GESTURE_WINDOW_MS); presses = 0 }
        }
    }

    private fun onTriggerFired() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < COOLDOWN_MS) return

        // Engine guard: block triggers if license is not activated
        if (!com.camlauncher.app.data.LicenseManager.isActivated(this)) {
            Log.w(TAG, "onTriggerFired() blocked — license not activated")
            return
        }

        lastTriggerMs = now

        val currentState = RecordingService.stateFlow.value
        val taskAction = if (currentState == RecordingState.RECORDING) "STOP" else "START"

        scope.launch {
            if (taskAction == "START") {
                val isAntiPocket = settings.antiPocketEnabled.first()
                if (isAntiPocket && checkProximity(this@GestureTriggerService)) return@launch
            }

            try {
                if (withTimeoutOrNull(500) { settings.vibrationOnTrigger.first() } != false) {
                    val pattern = if (taskAction == "STOP") longArrayOf(0, 300) else longArrayOf(0, 150, 50, 150)
                    vibrateRobust(pattern)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vibration launch failed", e)
            }

            if (currentState == RecordingState.STARTING || currentState == RecordingState.STOPPING) return@launch

            // --- THE CRITICAL FIX ---
            // DO NOT call RecordingService directly. Route the command to TriggerActivity so 
            // the app gains foreground focus BEFORE the FGS requests Camera permissions.
            try {
                val intent = Intent(this@GestureTriggerService, com.camlauncher.app.ui.TriggerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("action", taskAction)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Activity bridge failed", e)
            }
        }
    }

    private fun vibrateRobust(pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), attrs)
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {}
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
                    cont.resume(event.values[0] < proximitySensor.maximumRange)
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, proximitySensor, android.hardware.SensorManager.SENSOR_DELAY_FASTEST)
        handler.postDelayed(timeoutRunnable, 250L) 
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        sensor?.stop()
        stopMediaSessionHelper()
        stopVolumeBroadcast()
        scope.cancel()
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "GestureSvc"
    }
}