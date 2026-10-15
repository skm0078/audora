// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/Fixtures.kt
// Purpose: Shared test data: sentence units and PCM of a given length.

package io.github.skm0078.audora.playback

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.speech.audio.PcmAudio

const val TEST_SAMPLE_RATE = 1_000

/** Sentence [index] of [page]; each sentence covers 10 characters of the page. */
fun unit(page: Int, index: Int, lang: Lang = Lang.EN): ReadingUnit = ReadingUnit(
    page = page,
    index = index,
    charStart = index * 10,
    charEnd = index * 10 + 9,
    text = "Sentence $index of page $page.",
    lang = lang,
    pauseAfterMs = 0,
    bounds = null,
)

fun page(page: Int, count: Int): List<ReadingUnit> = List(count) { unit(page, it) }

fun pcm(samples: Int, fill: Short = 1): PcmAudio = PcmAudio(ShortArray(samples) { fill }, TEST_SAMPLE_RATE)
