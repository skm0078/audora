// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/listen/ScriptFilteredListener.kt
// Purpose: Drops heard stretches that are mostly in another script than the page's (Hindi heard as Urdu; item 67).

package io.github.skm0078.audora.speech.listen

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.tutor.PageWords

/** [inner], keeping only stretches mostly in [lang]'s script; counts what it drops, never the text. */
class ScriptFilteredListener(private val inner: Listener, private val lang: Lang) : Listener {
    override fun accept(samples: FloatArray): List<Heard> = keep(inner.accept(samples))

    override fun finish(): List<Heard> = keep(inner.finish())

    override fun close() = inner.close()

    private fun keep(heard: List<Heard>): List<Heard> {
        val kept = heard.filter { PageWords.mostlyIn(it.text, lang) }
        if (kept.size < heard.size) {
            AudoraLog.i(TAG, "Dropped ${heard.size - kept.size} stretch(es) in another script, lang=${lang.bcp47Tag}")
        }
        return kept
    }

    private companion object {
        const val TAG = "Speech.Listen"
    }
}
