// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioAudioVersion.kt
// Purpose: Version of studio audio's sound; saved audio is kept across app updates until this changes (ADR-064).

package io.github.skm0078.audora.playback.studio

/**
 * Bump whenever a change can make the same sentence, voice and settings sound different (AGENTS.md): a speech engine
 * or voice model upgrade, how text is shaped for speech (numbers, pauses, prosody, character voices), or what a clip
 * holds. Studio audio of another version is never played and is deleted when the app opens. Until 064 the key held
 * the app's install time instead, so every update threw away hours of rendering.
 * 1 = ADR-064: first explicit version.
 * 2 = closing quotes after a sentence's end move inside it for Kitten voices (no babble after the sentence).
 * 3 = a lone "I" after book, part, class and the like stays the pronoun; Hindi and Bengali say a minus sign; "1:50,000"
 *     is not a clock time; a book's pronunciation fixes apply from its first sentence.
 * 4 = an exclamation without a cue word reads emphatic, not lively (Bengali Rasa: CONV, not HAPPY); characters are
 *     found in single quotes, short lines and Hindi and Bengali tags, so more lines get a character's voice.
 * 5 = tables read like a person explaining them ("Ram: Age 34 and City Delhi."); footnotes a little quicker and softer.
 * 6 = narration rises and falls across a paragraph, slows after a heading and breathes in long sentences (Q-3).
 * 7 = more characters get a voice of their gender (Hindi "I" forms, Hindi and Bengali name endings, clues later on
 *     the page); a one-speaker narrator's other gender borrows another installed voice (ADR character-genders).
 * 8 = arrows, maths signs, Greek letters and powers in sentences are said in the part's language; emoji, stars and
 *     broken-font glyphs are left out (ADR 2026-10-28-symbols-in-sentences).
 * 9 = Bengali voices that read letters (SYSPIN, Rasa) are handed words spelled as said, at 0.9 of the asked pace, and
 *     Rasa reads with calmer noise (ADR 2026-10-30-bengali-spoken-spelling).
 */
const val STUDIO_AUDIO_VERSION: Int = 9
