// File: app/src/main/kotlin/io/github/skm0078/audora/ui/licences/AppNotices.kt
// Purpose: Every licence text and notice the APK carries (assets/licences), read for "All licence texts".

package io.github.skm0078.audora.ui.licences

import android.content.res.AssetManager
import io.github.skm0078.audora.speech.voices.LicenceText

/**
 * The APK's licence folder. The GPL asks that its text goes with every copy of the app, and the BSD, IJG and FreeType
 * licences ask for their notices in the app's documentation: this folder is that documentation, all of it readable on
 * the "Voices & licences" screen. [INDEX] names every work and the file holding its licence.
 */
object AppNotices {
    const val ASSET_DIR = "licences"
    const val INDEX = "THIRD-PARTY-NOTICES.txt"
    const val APP_LICENCE = "listenlock-GPL-3.0.txt"

    /** [names] in reading order: the index first, the app's own licence second, then the rest by name. */
    fun order(names: List<String>): List<String> = names.sortedWith(compareBy({ rank(it) }, { it.lowercase() }))

    /** Throws IOException when a file cannot be read; the screen's controller logs it and shows nothing. */
    fun read(assets: AssetManager): List<LicenceText> = order(assets.list(ASSET_DIR).orEmpty().toList()).map { name ->
        LicenceText(name, assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() })
    }

    /**
     * [text] cut at its blank lines, so the longest file (ONNX Runtime's notices, ~330 KB) becomes a lazy list of short
     * paragraphs instead of one block of text that would be laid out whole.
     */
    fun paragraphs(text: String): List<String> = text.split(BLANK_LINE).map { it.trimEnd() }.filter { it.isNotBlank() }

    private fun rank(name: String): Int = when (name) {
        INDEX -> 0
        APP_LICENCE -> 1
        else -> 2
    }

    private val BLANK_LINE = Regex("\\r?\\n[ \\t]*\\r?\\n")
}
