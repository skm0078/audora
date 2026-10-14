// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/service/SpikeCommands.kt
// Purpose: Custom session commands the activity sends to start and stop a run.

package io.github.skm0078.audora.spike.playback.service

import android.os.Bundle
import androidx.media3.session.SessionCommand
import io.github.skm0078.audora.spike.playback.config.SpikePreset

object SpikeCommands {
    const val ACTION_START = "io.github.skm0078.audora.spike.playback.START_RUN"
    const val ACTION_STOP = "io.github.skm0078.audora.spike.playback.STOP_RUN"
    const val ARG_PRESET = "preset"
    const val ARG_OWN_WAKE_LOCK = "own_wake_lock"

    val START = SessionCommand(ACTION_START, Bundle.EMPTY)
    val STOP = SessionCommand(ACTION_STOP, Bundle.EMPTY)

    fun startArgs(preset: SpikePreset, ownWakeLock: Boolean): Bundle = Bundle().apply {
        putString(ARG_PRESET, preset.name)
        putBoolean(ARG_OWN_WAKE_LOCK, ownWakeLock)
    }

    fun presetFrom(args: Bundle): SpikePreset = args.getString(ARG_PRESET)
        ?.let { name -> SpikePreset.entries.firstOrNull { it.name == name } }
        ?: SpikePreset.GATE
}
