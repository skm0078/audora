// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/QueueConfig.kt
// Purpose: Tunables for playback (P1-04): render-ahead depth, playlist feeding, audio cache and error recovery.

package io.github.skm0078.audora.core.config

/**
 * @property prefetchUnits sentences rendered ahead of the one playing at least (Build Spec §6: 3 for fast voices).
 *   Slower voices get more, sized from their measured speed by [renderAhead] (P4-02, ADR-027).
 * @property minUnitsAhead the playlist is topped up with the next page whenever fewer sentences than this are
 *   queued after the current one ("at least the next page of items").
 * @property keepBehindUnits sentences kept in the playlist behind the current one, so skip-back works; older
 *   items are dropped so a 1,000-page book never builds a 20,000-item playlist.
 * @property recentAudioBytes PCM of already-played sentences kept in memory, so skipping back replays instantly
 *   instead of rendering again. 8 MiB is about three minutes of 22 kHz speech.
 * @property renderRetryLimit consecutive sentences that may fail to render (each is skipped) before reading
 *   stops with an error: one bad sentence is skipped, a broken voice stops instead of skipping a whole book.
 * @property stuckRetryLimit times the player may report stuck buffering on the same sentence (Media3's
 *   10-minute timeout, S3 finding 3) before that sentence is skipped; the first time restarts the render queue.
 * @property stuckBufferingTimeoutMs Media3's stuck-buffering timeout. Its default (10 min) in production;
 *   tests lower it. Setting it always comes with an explicit wake mode (S3 finding 4).
 * @property playbackStartBufferMs audio the player needs buffered before it starts or resumes. 0: a sentence
 *   plays as soon as it is rendered. Media3's defaults (1 s, 2 s after a rebuffer) never fill when the
 *   rendered sentences are short and the next one waits for the playhead to move (render window), so the
 *   player would sit in BUFFERING for good.
 * @property renderAhead how far ahead to render for a voice of a given speed; its `maxAheadUnits` also caps the
 *   window the render queue accepts.
 * @property resumeRewind how far reading steps back when it resumes after a pause (ADR-033).
 * @property jumps the jump history behind "Undo jump" (plan item 78).
 * @property sleep the sleep timer extras: fade-out and shake to extend (plan item 37).
 */
data class QueueConfig(
    val prefetchUnits: Int = 3,
    val minUnitsAhead: Int = 20,
    val keepBehindUnits: Int = 30,
    val recentAudioBytes: Long = DEFAULT_RECENT_AUDIO_BYTES,
    val renderRetryLimit: Int = 3,
    val stuckRetryLimit: Int = 2,
    val stuckBufferingTimeoutMs: Int = 600_000,
    val playbackStartBufferMs: Int = 0,
    val renderAhead: RenderAheadConfig = RenderAheadConfig(),
    val resumeRewind: ResumeRewindConfig = ResumeRewindConfig(),
    val jumps: JumpConfig = JumpConfig(),
    val sleep: SleepConfig = SleepConfig(),
) {
    init {
        require(prefetchUnits >= 1) { "prefetchUnits must be >= 1, was $prefetchUnits" }
        require(minUnitsAhead >= 1) { "minUnitsAhead must be >= 1, was $minUnitsAhead" }
        require(keepBehindUnits >= 0) { "keepBehindUnits must be >= 0, was $keepBehindUnits" }
        require(recentAudioBytes >= 0) { "recentAudioBytes must be >= 0, was $recentAudioBytes" }
        require(renderRetryLimit >= 1) { "renderRetryLimit must be >= 1, was $renderRetryLimit" }
        require(stuckRetryLimit >= 1) { "stuckRetryLimit must be >= 1, was $stuckRetryLimit" }
        require(stuckBufferingTimeoutMs > 0) { "stuckBufferingTimeoutMs must be > 0, was $stuckBufferingTimeoutMs" }
        require(playbackStartBufferMs >= 0) { "playbackStartBufferMs must be >= 0, was $playbackStartBufferMs" }
        require(renderAhead.maxAheadUnits >= prefetchUnits) {
            "renderAhead.maxAheadUnits (${renderAhead.maxAheadUnits}) must be >= prefetchUnits ($prefetchUnits)"
        }
    }

    private companion object {
        const val DEFAULT_RECENT_AUDIO_BYTES = 8L shl 20 // 8 MiB
    }
}
