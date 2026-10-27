// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/VoiceUseTerms.kt
// Purpose: Model licences whose use restrictions the user agrees to before a download (2026-10-27 licence audit).

package io.github.skm0078.audora.speech.voices

/**
 * A model licence that lets anyone use and share the model, commercially too, but only if its use restrictions are
 * passed on to every user as terms they agree to. The catalog names it (`useTerms`); the app shows the restrictions
 * and asks before the download starts, and the full text ships in the voice's zip as `MODEL_LICENSE`.
 *
 * @property key the catalog value.
 * @property licenceUrl the licence's own page, for "Read the full licence".
 */
enum class VoiceUseTerms(val key: String, val licenceUrl: String) {
    /** BigScience Open RAIL-M (Supertonic 3): section 4 asks for Attachment A to be an enforceable provision. */
    OPENRAIL_M("openrail-m", "https://huggingface.co/supertone-oss-archive/supertonic-3/blob/main/LICENSE"),
    ;

    companion object {
        fun fromKey(key: String): VoiceUseTerms? = entries.firstOrNull { it.key == key }
    }
}
