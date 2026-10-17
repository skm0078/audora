// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/ChargingGate.kt
// Purpose: Lets studio mode render only while the phone charges and nothing is playing (ADR-039).

package io.github.skm0078.audora.playback.studio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import io.github.skm0078.audora.core.config.StudioConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Called on the studio thread before each sentence. While it waits for the charger the wake lock is released, so the
 * phone sleeps and the wait costs no battery; plugging in wakes the wait at once (a receiver registered for the run's
 * life). While other audio plays (Audora itself, or any app) it waits with the wake lock kept, checking every
 * [StudioConfig.waitPollMs], so studio mode never competes with listening for the CPU.
 *
 * [onWaiting] gets the reason when a wait starts and null when rendering goes on; it updates the notification.
 */
class ChargingGate(
    context: Context,
    private val config: StudioConfig,
    private val wakeLock: PowerManager.WakeLock,
    private val onWaiting: (Wait?) -> Unit,
) : StudioGate {
    enum class Wait { CHARGER, AUDIO }

    private val appContext = context.applicationContext
    private val battery = appContext.getSystemService(BatteryManager::class.java)
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val wakeUps = Semaphore(0)
    private var shown: Wait? = null

    @Volatile
    private var onBattery = false

    private val power = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = wake()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // System broadcasts only; never open to other apps.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(power, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(power, filter)
        }
    }

    override fun await() {
        while (true) {
            wakeUps.drainPermits() // Before checking, so a plug-in after the check still ends the wait.
            val wait = blockedBy()
            if (wait == null) {
                if (!wakeLock.isHeld) wakeLock.acquire(config.maxRunMs)
                report(null)
                return
            }
            if (wait == Wait.CHARGER && wakeLock.isHeld) wakeLock.release()
            report(wait)
            wakeUps.tryAcquire(config.waitPollMs, TimeUnit.MILLISECONDS) // Throws InterruptedException on Stop.
        }
    }

    /** "Start now" on the notification: render on battery for the rest of this run. */
    fun allowBattery() {
        AudoraLog.i(TAG, "Studio mode renders on battery for this run")
        onBattery = true
        wake()
    }

    fun close() {
        try {
            appContext.unregisterReceiver(power)
        } catch (e: IllegalArgumentException) {
            AudoraLog.w(TAG, "Power receiver was not registered", e)
        }
    }

    private fun blockedBy(): Wait? = when {
        !onBattery && !battery.isCharging -> Wait.CHARGER
        audio.isMusicActive -> Wait.AUDIO
        else -> null
    }

    private fun report(wait: Wait?) {
        if (wait == shown) return
        shown = wait
        AudoraLog.i(TAG, if (wait == null) "Studio mode rendering" else "Studio mode waiting: $wait")
        onWaiting(wait)
    }

    private fun wake() = wakeUps.release()

    private companion object {
        const val TAG = "Studio.Gate"
    }
}
