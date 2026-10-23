// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/sherpa/VoiceRespellings.kt
// Purpose: Spellings a particular voice model misreads, given to it the way it reads them right (pronunciation check).

package io.github.skm0078.audora.speech.sherpa

import java.text.Normalizer

/**
 * Fixes for one voice model, found by `scripts/pronunciation_check.py` and confirmed by ear. They are keyed on
 * catalog voice ids, like [CastSpeakers], so an installed voice gets them without a new download, and they never
 * touch another voice: Supertonic says the nukta letters these Piper voices garble.
 *
 * Piper's Hindi voices (espeak-ng phonemes, Indian speakers): ग़, ख़ and क़ come out garbled ("ग़ज़ल" heard as
 * "फ़ज़ल" or "ओजल"); written without the nukta they read as most Hindi speakers say them ("गज़ल"). ज़ and फ़ are
 * read well and stay.
 */
object VoiceRespellings {
    private val PIPER_HINDI = listOf("ग़" to "ग", "ख़" to "ख", "क़" to "क")

    private val BY_VOICE: Map<String, List<Pair<String, String>>> = mapOf(
        "piper-hi-pratham" to PIPER_HINDI,
        "piper-hi-priyamvada" to PIPER_HINDI,
    ).mapValues { (_, pairs) -> pairs.map { (from, to) -> nfc(from) to nfc(to) } }

    /** [text] as the voice [voiceId] should be given it; unchanged for a voice without fixes. */
    fun apply(voiceId: String, text: String): String {
        val fixes = BY_VOICE[voiceId] ?: return text
        return fixes.fold(nfc(text)) { current, (from, to) -> current.replace(from, to) }
    }

    /** NFC splits ग़ into ग and the nukta (a composition exclusion), so text and fixes meet in one form. */
    private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
}
