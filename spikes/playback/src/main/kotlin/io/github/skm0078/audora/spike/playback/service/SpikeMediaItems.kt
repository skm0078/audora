// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/service/SpikeMediaItems.kt
// Purpose: Maps fake-book units to Media3 playlist items, and Media3 player states to the metrics' phases.

package io.github.skm0078.audora.spike.playback.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import io.github.skm0078.audora.spike.playback.engine.FakeUnit
import io.github.skm0078.audora.spike.playback.metrics.PlayerPhase

object SpikeMediaItems {
    /** The media id is the unit index; the notification and lock screen show sentence and page. */
    fun forUnit(unit: FakeUnit, runLabel: String): MediaItem = MediaItem.Builder()
        .setMediaId(unit.index.toString())
        .setUri(TtsUri.forUnit(unit.index))
        .setMimeType(MimeTypes.AUDIO_WAV)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Sentence ${unit.index + 1}")
                .setArtist("Page ${unit.page + 1} · S3 $runLabel")
                .build(),
        )
        .build()

    fun phaseOf(state: Int): PlayerPhase = when (state) {
        Player.STATE_BUFFERING -> PlayerPhase.BUFFERING
        Player.STATE_READY -> PlayerPhase.READY
        Player.STATE_ENDED -> PlayerPhase.ENDED
        else -> PlayerPhase.IDLE
    }
}
