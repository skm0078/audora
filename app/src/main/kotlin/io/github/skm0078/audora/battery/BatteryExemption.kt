// File: app/src/main/kotlin/io/github/skm0078/audora/battery/BatteryExemption.kt
// Purpose: Android side of battery onboarding: is Audora exempt, the phone's brand, and the settings to open.

package io.github.skm0078.audora.battery

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import io.github.skm0078.audora.core.battery.PhoneBrand
import io.github.skm0078.audora.core.logging.AudoraLog

/**
 * Only public Settings intents: brand-specific battery screens are private activities that move between OS
 * versions, so the brand steps are shown as text and the app opens the app info page they all start from.
 */
class BatteryExemption(private val context: Context) {
    val brand: PhoneBrand = PhoneBrand.of(Build.MANUFACTURER, Build.BRAND)

    /** True when Android's battery optimization is off for Audora ("Unrestricted" on Android 12+). */
    fun isExempt(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * Android's battery optimization list, where the user picks Audora and "Don't optimize" (the hint under the
     * button says how). Not the one-tap REQUEST_IGNORE_BATTERY_OPTIMIZATIONS prompt: Google Play allows that only
     * when Doze breaks an app's core function, and a media player's foreground service already keeps playing.
     */
    fun requestExemption(start: (Intent) -> Unit) = startFirst(
        start,
        "optimization list",
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetailsIntent(),
    )

    /** App info for Audora, where every brand's battery and background settings start. */
    fun openAppSettings(start: (Intent) -> Unit) = startFirst(start, "app settings", appDetailsIntent())

    private fun appDetailsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

    private fun packageUri(): Uri = Uri.fromParts("package", context.packageName, null)

    /** Some OEM builds drop a Settings screen; fall through to the next intent instead of crashing. */
    private fun startFirst(start: (Intent) -> Unit, what: String, vararg intents: Intent) {
        for (intent in intents) {
            try {
                start(intent)
                AudoraLog.i(TAG, "Opened battery $what screen: ${intent.action} brand=$brand")
                return
            } catch (e: ActivityNotFoundException) {
                AudoraLog.w(TAG, "No screen for ${intent.action} brand=$brand", e)
            }
        }
        AudoraLog.e(TAG, "No battery $what screen at all, brand=$brand sdk=${Build.VERSION.SDK_INT}")
    }

    private companion object {
        const val TAG = "Audora.Battery"
    }
}
