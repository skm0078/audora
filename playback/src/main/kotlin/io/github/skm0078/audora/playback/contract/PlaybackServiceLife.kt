// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/PlaybackServiceLife.kt
// Purpose: Whether the playback service is up in this process, so screens that only show playback (the mini player,
//          the lock's saver) join it when it is instead of starting it (ADR 2026-10-30-lazy-player-service).

package io.github.skm0078.audora.playback.contract

import io.github.skm0078.audora.playback.service.AudoraPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Up from the end of [AudoraPlaybackService.onCreate] (its session built) to the start of its onDestroy. The service
 * runs in the app's process, so a new process starts with it down, as it is.
 */
object PlaybackServiceLife {
    private val mutableUp = MutableStateFlow(false)
    val up: StateFlow<Boolean> = mutableUp.asStateFlow()

    internal fun created() {
        mutableUp.value = true
    }

    internal fun destroyed() {
        mutableUp.value = false
    }
}
