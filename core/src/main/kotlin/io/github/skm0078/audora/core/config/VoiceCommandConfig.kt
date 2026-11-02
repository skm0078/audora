// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/VoiceCommandConfig.kt
// Purpose: Tunables for spoken commands ("ListenLock, pause") heard by the phone's own speech recogniser (plan
//          item 38, ADR 2026-11-02-voice-commands-system-recogniser).

package io.github.skm0078.audora.core.config

/**
 * @property languages the recogniser languages tried in order (BCP 47): Indian English first, as most listeners
 *   speak it, then US English, which every on-device recogniser has. The commands are English words either way.
 * @property failureRetryMs a recognition session that failed (not one that simply heard nothing) is started again
 *   after this, so a recogniser in trouble is not restarted in a tight loop.
 * @property maxFailuresInARow this many failed sessions in a row stop listening, and the reader says so, instead of
 *   retrying for ever and costing battery for nothing.
 * @property repeatGapMs a phrase heard again within this time is the same utterance, not a second command.
 * @property narratorHoldMs commands stay ignored this long after the book itself said the app's name: past the
 *   recogniser's lag after a phrase ends.
 * @property screenOffIdleMs screen-off listening ends once reading has stayed paused this long and the reader is
 *   not on screen: a battery choice (the recogniser costs power for as long as it runs). It matches Media3's
 *   foreground timeout after a pause (its default and maximum, 10 minutes), after which the playback notification
 *   goes too, so the clock starts at the pause, not when the reader is hidden (ADR-051).
 * @property toneVolume the short tone that confirms each command heard (ToneGenerator's 0..100).
 * @property toneMs how long that tone plays.
 */
data class VoiceCommandConfig(
    val languages: List<String> = listOf("en-IN", "en-US"),
    val failureRetryMs: Long = 1_000,
    val maxFailuresInARow: Int = 5,
    val repeatGapMs: Long = 1_500,
    val narratorHoldMs: Long = 2_000,
    val screenOffIdleMs: Long = 600_000,
    val toneVolume: Int = 60,
    val toneMs: Int = 120,
) {
    init {
        require(languages.isNotEmpty()) { "languages must not be empty" }
        require(failureRetryMs >= 0 && maxFailuresInARow >= 1) {
            "failureRetryMs must be >= 0 and maxFailuresInARow >= 1"
        }
        require(repeatGapMs >= 0 && narratorHoldMs >= 0) { "repeatGapMs and narratorHoldMs must be >= 0" }
        require(screenOffIdleMs >= 0) { "screenOffIdleMs must be >= 0, was $screenOffIdleMs" }
        require(toneVolume in 0..MAX_TONE_VOLUME && toneMs > 0) { "toneVolume must be in 0..100 and toneMs > 0" }
    }

    /** Whole minutes of [screenOffIdleMs], for the texts that name it. */
    val screenOffIdleMinutes: Int get() = (screenOffIdleMs / MS_PER_MINUTE).toInt()

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val MAX_TONE_VOLUME = 100
    }
}
