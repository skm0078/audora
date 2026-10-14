// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/device/DeviceStatus.kt
// Purpose: The phone and battery-management facts that decide whether background playback survives.

package io.github.skm0078.audora.spike.playback.device

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object DeviceStatus {
    fun batteryPercent(context: Context): Int? = context.getSystemService(BatteryManager::class.java)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..FULL_BATTERY }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun isScreenOn(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive == true

    /** "key: value" pairs for the report. */
    fun describe(context: Context): List<Pair<String, String>> {
        val power = context.getSystemService(PowerManager::class.java)
        val activity = context.getSystemService(ActivityManager::class.java)
        val usage = context.getSystemService(UsageStatsManager::class.java)
        return listOf(
            "Phone" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
            "Android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}",
            "Battery optimization" to if (isIgnoringBatteryOptimizations(context)) "not optimized" else "optimized",
            "Background restricted" to (activity?.isBackgroundRestricted?.toString() ?: "unknown"),
            "Standby bucket" to (usage?.appStandbyBucket?.let(::bucketName) ?: "unknown"),
            "Battery saver" to (power?.isPowerSaveMode?.toString() ?: "unknown"),
            "Battery now" to (batteryPercent(context)?.let { "$it%" } ?: "unknown"),
        )
    }

    /** Android's own "stop optimizing this app?" prompt. Allowed for sideloaded apps (Build Spec §6). */
    fun requestIgnoreBatteryOptimizations(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))

    /** App info page; on OnePlus, Battery usage → "Allow background activity" lives here. */
    fun appDetailsSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    private fun bucketName(bucket: Int): String = when (bucket) {
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
        UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "RESTRICTED"
        else -> "OTHER($bucket)"
    }

    private const val FULL_BATTERY = 100
}
