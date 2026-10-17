// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioSentenceRenderer.kt
// Purpose: Playback's renderer with studio audio in front: a sentence rendered ahead plays at once, the rest live.

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.core.config.StudioConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio

/**
 * Runs on the render queue's worker thread, like [live]. [key] is read per sentence (the open book and what its
 * audio must sound like now); null = no book, straight to [live]. A studio page is read from disk once and kept
 * while the queue stays on it; a page studio mode rewrites meanwhile is read again.
 *
 * Studio audio skips the live voice, so it is not timed for the render-ahead window: the window still sizes for the
 * live voice, which is what plays wherever studio audio runs out.
 */
class StudioSentenceRenderer(
    private val live: SentenceRenderer,
    private val store: StudioStore,
    config: StudioConfig = StudioConfig(),
    private val key: () -> StudioKey?,
) : SentenceRenderer {
    private val codec = LosslessPcm(config.blockSamples)
    private var loaded: Loaded? = null

    override fun render(unit: ReadingUnit): Outcome<PcmAudio, SpeechError> {
        val clip = key()?.let { pageFor(it, unit.page) }?.clipFor(unit)
        val studio = clip?.let { decode(unit, it) }
        return if (studio != null) Outcome.Success(studio) else live.render(unit)
    }

    private fun decode(unit: ReadingUnit, clip: StudioClip): PcmAudio? = try {
        clip.audio(codec)
    } catch (e: CorruptStudioData) {
        AudoraLog.w(TAG, "${UnitKey.of(unit)} studio audio damaged, rendering live", e)
        null
    }

    private fun pageFor(key: StudioKey, page: Int): StudioPage? {
        val stamp = store.pageStamp(key, page)
        val cached = loaded?.takeIf { it.key == key && it.page == page && it.stamp == stamp }
        return when {
            stamp == null -> null

            cached != null -> cached.studioPage

            else -> store.page(key, page)?.also { studioPage ->
                loaded = Loaded(key, page, stamp, studioPage)
                AudoraLog.d(TAG, "Studio audio for page $page, book=${key.book.logLabel}: ${studioPage.clips.size}")
            }
        }
    }

    private class Loaded(val key: StudioKey, val page: Int, val stamp: Long, val studioPage: StudioPage)

    private companion object {
        const val TAG = "Studio.Play"
    }
}
