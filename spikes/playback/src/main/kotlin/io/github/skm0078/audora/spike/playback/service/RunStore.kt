// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/service/RunStore.kt
// Purpose: Persists the run's state and latest report, so a killed process still leaves evidence behind.

package io.github.skm0078.audora.spike.playback.service

import android.content.Context
import androidx.core.content.edit
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.spike.playback.config.SpikePreset
import io.github.skm0078.audora.spike.playback.device.ExitSummary
import java.text.DateFormat
import java.util.Date

/**
 * Writes with commit(), not apply(): an async write can be lost when the process is killed,
 * and a killed process is exactly what this store must survive. Writes are small and every 30 s.
 */
class RunStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedPreset: SpikePreset
        get() = prefs.getString(KEY_SELECTED_PRESET, null)
            ?.let { name -> SpikePreset.entries.firstOrNull { it.name == name } }
            ?: SpikePreset.GATE
        set(value) = prefs.edit { putString(KEY_SELECTED_PRESET, value.name) }

    var ownWakeLock: Boolean
        get() = prefs.getBoolean(KEY_OWN_WAKE_LOCK, false)
        set(value) = prefs.edit { putBoolean(KEY_OWN_WAKE_LOCK, value) }

    val lastReport: String? get() = prefs.getString(KEY_LAST_REPORT, null)

    /** Set when the previous run's process died mid-run. */
    val interruption: String? get() = prefs.getString(KEY_INTERRUPTION, null)

    val startBatteryPercent: Int? get() = prefs.getInt(KEY_START_BATTERY, -1).takeIf { it >= 0 }

    fun beginRun(startedWallMs: Long, startBatteryPercent: Int?) {
        prefs.edit(commit = true) {
            putBoolean(KEY_ACTIVE, true)
            putLong(KEY_STARTED_WALL, startedWallMs)
            putLong(KEY_LAST_HEARTBEAT_WALL, startedWallMs)
            putInt(KEY_START_BATTERY, startBatteryPercent ?: -1)
            remove(KEY_INTERRUPTION)
            remove(KEY_LAST_REPORT)
        }
    }

    fun saveProgress(report: String, nowWallMs: Long) {
        prefs.edit(commit = true) {
            putString(KEY_LAST_REPORT, report)
            putLong(KEY_LAST_HEARTBEAT_WALL, nowWallMs)
        }
    }

    fun finishRun(report: String) {
        prefs.edit(commit = true) {
            putBoolean(KEY_ACTIVE, false)
            putString(KEY_LAST_REPORT, report)
        }
    }

    /** Called at process start. A run still marked active belonged to a process that was killed. */
    fun markOrphanedRun(lastExit: ExitSummary?) {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return
        val startedWall = prefs.getLong(KEY_STARTED_WALL, 0)
        val lastBeat = prefs.getLong(KEY_LAST_HEARTBEAT_WALL, startedWall)
        val exitText = lastExit?.takeIf { it.timestampMs >= startedWall }?.text ?: "no exit record for this run"
        val note = "Verdict: FAIL. The app process was killed during the run. " +
            "Last heartbeat ${formatTime(lastBeat)} (${(lastBeat - startedWall) / MS_PER_MINUTE} min in). " +
            "Android exit reason: $exitText."
        AudoraLog.w(TAG, note)
        prefs.edit(commit = true) {
            putBoolean(KEY_ACTIVE, false)
            putString(KEY_INTERRUPTION, note)
        }
    }

    private fun formatTime(wallMs: Long) = DateFormat.getDateTimeInstance().format(Date(wallMs))

    private companion object {
        const val TAG = "S3.RunStore"
        const val PREFS_NAME = "s3_run"
        const val KEY_ACTIVE = "active"
        const val KEY_STARTED_WALL = "started_wall_ms"
        const val KEY_LAST_HEARTBEAT_WALL = "last_heartbeat_wall_ms"
        const val KEY_START_BATTERY = "start_battery_percent"
        const val KEY_LAST_REPORT = "last_report"
        const val KEY_INTERRUPTION = "interruption"
        const val KEY_SELECTED_PRESET = "selected_preset"
        const val KEY_OWN_WAKE_LOCK = "own_wake_lock"
        const val MS_PER_MINUTE = 60_000L
    }
}
