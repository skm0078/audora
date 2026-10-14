// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/ui/SpikeUiState.kt
// Purpose: Everything the spike screen shows, and the actions it can trigger.

package io.github.skm0078.audora.spike.playback.ui

import io.github.skm0078.audora.spike.playback.config.SpikePreset

data class SpikeUiState(
    val selectedPreset: SpikePreset = SpikePreset.GATE,
    val ownWakeLock: Boolean = false,
    val visible: Boolean = false,
    val connected: Boolean = false,
    val runActive: Boolean = false,
    val report: String? = null,
    val interruption: String? = null,
    val batteryLines: List<Pair<String, String>> = emptyList(),
    val batteryExempt: Boolean = false,
)

/** What the screen can ask for; the activity implements it. */
interface SpikeActions {
    fun selectPreset(preset: SpikePreset)

    fun setOwnWakeLock(enabled: Boolean)

    fun startRun()

    fun stopRun()

    fun refresh()

    fun requestBatteryExemption()

    fun openAppSettings()

    fun shareReport()

    fun shareLog()
}
