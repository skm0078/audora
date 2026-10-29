// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/Motion.kt
// Purpose: Whether a decorative animation may run: never when the phone asks for less motion or saves battery.

package io.github.skm0078.audora.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * False while "Remove animations" is on (Android's animator scale at 0) or battery saver is on; true otherwise.
 * Battery saver is followed as it changes; the animation setting is read again each time the screen comes back,
 * since Android sends no broadcast for it. Only for decoration: nothing a person needs may depend on motion.
 */
@Composable
fun rememberDecorativeMotion(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(decorativeMotionAllowed(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(received: Context, intent: Intent) {
                allowed = decorativeMotionAllowed(context)
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        // A system broadcast still reaches a receiver that other apps cannot send to.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LifecycleResumeEffect(context) {
        allowed = decorativeMotionAllowed(context)
        onPauseOrDispose {}
    }
    return allowed
}

private fun decorativeMotionAllowed(context: Context): Boolean {
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val saving = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    return scale > 0f && !saving
}
