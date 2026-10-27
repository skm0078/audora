// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/RenderBook.kt
// Purpose: What a book's sentence renderer reads about the open book, for BookRenderers.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.ParagraphPlace
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.speech.normalize.PronunciationFixer
import io.github.skm0078.audora.speech.route.CharacterVoices
import io.github.skm0078.audora.speech.route.Speaker

/**
 * What a renderer reads about the open book, each read on the render thread for every sentence, so each must be
 * thread-safe: its chapter start pages (cue before a chapter's heading), whether a page was read by OCR (run-on
 * punctuation, RI-11), its pronunciation fixes (plan item 1) and whether a sentence starts inside a quote opened
 * earlier in its paragraph (cast voices, plan item 7), who speaks it and the book's character voices (item 63), and
 * what the book is (a story, a poem, the news; ADR-056), which sets how it is delivered, its language tag, which
 * has the phone's voice read a language none of the three is (ADR 2026-10-26), and the feeling the story brain heard
 * in a sentence (ADR story-brain; null when it was not asked).
 * [placeOf]: where a sentence sits in its paragraph, for the narration's rise and fall (Q-3); null when unknown.
 */
@Suppress("LongParameterList") // All optional: each caller sets what it knows about the book.
class RenderBook(
    val chapterPages: () -> Set<Int> = { emptySet() },
    val scanned: (Int) -> Boolean = { false },
    val fixer: () -> PronunciationFixer = { PronunciationFixer(emptyList()) },
    val startsInQuote: (ReadingUnit) -> Boolean = { false },
    val speakerOf: (ReadingUnit) -> Speaker? = { null },
    val moodOf: (ReadingUnit) -> Feeling? = { null },
    val judgedFeeling: (ReadingUnit) -> Feeling? = { null },
    val characters: CharacterVoices? = null,
    val intent: () -> ContentIntent = { ContentIntent.GENERAL },
    val language: () -> String? = { null },
    val placeOf: (ReadingUnit) -> ParagraphPlace? = { null },
)
