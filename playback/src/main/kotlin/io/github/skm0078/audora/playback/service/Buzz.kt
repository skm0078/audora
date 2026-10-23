// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/Buzz.kt
// Purpose: A short vibration that confirms an eyes-free action (a shake felt, a headset bookmark saved).

package io.github.skm0078.audora.playback.service

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** Uses the normal `VIBRATE` permission; a phone without a vibrator simply stays still. */
internal fun Context.buzz(durationMs: Long) {
    val vibrator = applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    if (vibrator?.hasVibrator() == true) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
