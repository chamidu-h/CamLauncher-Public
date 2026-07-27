package com.camlauncher.app.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

/**
 * [STUB] Proprietary hardware sensor logic is closed-source to prevent unauthorized cloning.
 * This file is a stub to allow the open-source UI repository to compile.
 */
class SensorTriggerManager(
    context: Context,
    private val onTrigger: () -> Unit
) : SensorEventListener {

    fun start() {
        // Implementation hidden
    }

    fun stop() {
        // Implementation hidden
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Implementation hidden
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Implementation hidden
    }

    companion object {
        private const val TAG = "SensorTriggerManagerStub"
    }
}
