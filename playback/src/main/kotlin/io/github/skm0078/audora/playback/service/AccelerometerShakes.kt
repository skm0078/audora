// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/AccelerometerShakes.kt
// Purpose: Shakes from the accelerometer, confirmed with a short buzz, for "shake to extend" (plan item 37).

package io.github.skm0078.audora.playback.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.skm0078.audora.core.config.SleepConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.session.ShakeDetector
import io.github.skm0078.audora.playback.session.ShakeSource

/**
 * The accelerometer at the UI rate (about 16 samples a second) is a low-power sensor; it is registered only while a
 * timer runs. Samples arrive on the main thread, where [ShakeDetector] and the callback run.
 */
class AccelerometerShakes(context: Context, private val config: SleepConfig) : ShakeSource {
    private val appContext = context.applicationContext
    private val sensors = appContext.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val main = Handler(Looper.getMainLooper())
    private var listener: SensorEventListener? = null

    override fun start(onShake: () -> Unit) {
        stop()
        val sensor = accelerometer
        if (sensors == null || sensor == null) {
            AudoraLog.w(TAG, "No accelerometer: shake to extend is unavailable on this phone")
            return
        }
        val detector = ShakeDetector(config)
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                if (detector.onSample(x, y, z, SystemClock.elapsedRealtime())) {
                    appContext.buzz(config.buzzMs)
                    onShake()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = newListener
        sensors.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_UI, main)
    }

    override fun stop() {
        listener?.let { sensors?.unregisterListener(it) }
        listener = null
    }

    private companion object {
        const val TAG = "Playback.Shake"
    }
}
