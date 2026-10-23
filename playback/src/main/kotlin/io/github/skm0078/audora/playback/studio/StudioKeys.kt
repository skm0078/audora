// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/StudioKeys.kt
// Purpose: What studio audio depends on, so it plays only when it sounds exactly like the live voice would.

package io.github.skm0078.audora.playback.studio

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import java.security.MessageDigest

/**
 * Everything a sentence's audio depends on besides its own text. Studio audio is stored under [folder] of it; a
 * change to any part (another voice, rate or pace, reading mode, pronunciation fix, a new :pdf pipeline or
 * [STUDIO_AUDIO_VERSION]) gives another folder, so playback simply finds nothing and renders live until studio mode
 * runs again.
 *
 * @property voices the voice chosen for each language, null = the language's default voice. The default is not
 *   resolved here (that binds the phone's TTS), so a new system voice installed in between is the one thing this
 *   does not notice.
 * @property fixes the book's pronunciation fixes (`PronunciationFixer.signature`): the listener's own words, so only
 *   the hash is ever written or logged.
 * @property audioVersion [STUDIO_AUDIO_VERSION] when it was rendered: an app update keeps audio that still sounds the
 *   same and drops the rest (ADR-064).
 * @property punctuation whether the punctuation model is installed: it changes how scanned run-ons are read (RI-11).
 */
data class StudioSignature(
    val pipelineVersion: Int,
    val audioVersion: Int,
    val style: SpeechStyle,
    val voices: Map<Lang, VoiceId?>,
    val fixes: String,
    val punctuation: Boolean = false,
) {
    /** 16 hex digits of SHA-256 over every field: the folder name, safe to log. */
    val hash: String by lazy {
        val voiceLine = Lang.entries.joinToString(",") { lang -> "${lang.name}=${voices[lang] ?: DEFAULT_VOICE}" }
        val line = listOf(
            "pv=$pipelineVersion",
            "audio=$audioVersion",
            "rate=${style.rate}",
            "pace=${style.pace.name}",
            "clauses=${style.clausePauses}",
            "modes=${style.modes.encode()}",
            "shaping=${style.shaping.encode()}",
            "voices=$voiceLine",
            "fixes=$fixes",
            "punct=$punctuation",
        ).joinToString("|")
        StudioKeys.hex(line, HASH_CHARS)
    }

    /** The folder its audio is kept in: the version first, so a folder of another version is known by its name. */
    val folder: String get() = "$FOLDER_PREFIX$audioVersion-$hash"

    override fun toString(): String = "StudioSignature($hash)"

    companion object {
        private const val DEFAULT_VOICE = "default"
        private const val HASH_CHARS = 16
        private const val FOLDER_PREFIX = "v"

        /** True when [name] is a folder of [version]'s audio; folders from before ADR-064 carry no version. */
        fun isFolderOf(name: String, version: Int): Boolean = name.startsWith("$FOLDER_PREFIX$version-")
    }
}

/** A book plus the signature its audio must have: what playback looks studio audio up by. */
data class StudioKey(val book: BookId, val signature: StudioSignature)

object StudioKeys {
    private const val BOOK_CHARS = 16
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val FNV_PRIME = 0x100000001b3L

    /**
     * A 64-bit FNV-1a hash of everything about [unit] that changes what is said: its text, language, kind, pause
     * and table or note data. Its place on the page (bounds, offsets) is left out: moving does not change the sound.
     */
    fun unitKey(unit: ReadingUnit): Long {
        val line = "${unit.lang.name}|${unit.kind.name}|${unit.pauseAfterMs}|${unit.speakScore}|${unit.noteRef}|" +
            "${unit.table}|${unit.text}"
        var hash = FNV_OFFSET
        for (char in line) {
            hash = (hash xor char.code.toLong()) * FNV_PRIME
        }
        return hash
    }

    /** The book's folder name: a hash, so the library's id never appears in a path. */
    fun bookFolder(book: BookId): String = hex(book.value, BOOK_CHARS)

    internal fun hex(text: String, chars: Int): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(chars)
}
