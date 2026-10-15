// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/device/DeviceInfo.kt
// Purpose: Phone facts for the report (model, chip, RAM, battery, thermal state) and the live probe the
//          benchmark reads around each run. Thermal throttling changes RTF, so it is logged per run.

package io.github.skm0078.audora.spike.voices.device

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import io.github.skm0078.audora.spike.voices.bench.DeviceProbe

class DeviceInfo(private val context: Context) : DeviceProbe {
    private val power = context.getSystemService(PowerManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)

    override fun rssMb(): Long? = ProcStatus.currentRssMb()

    override fun thermalStatus(): String = when (power?.currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> "unknown"
    }

    fun describe(): List<Pair<String, String>> {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it)
        }
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = battery?.isCharging
        return listOf(
            "Phone" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
            "Chip" to
                "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}, ${Runtime.getRuntime().availableProcessors()} cores",
            "Android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "RAM" to "${memory.totalMem / BYTES_PER_MB} MB total, ${memory.availMem / BYTES_PER_MB} MB free",
            "Battery" to "$level %, charging=$charging, battery saver=${power?.isPowerSaveMode}",
            "Thermal" to thermalStatus(),
        )
    }

    private companion object {
        const val BYTES_PER_MB = 1_000_000L
    }
}
