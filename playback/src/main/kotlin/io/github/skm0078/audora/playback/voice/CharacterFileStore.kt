// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/CharacterFileStore.kt
// Purpose: Keeps each book's character voices (plan item 63) in a small file of its own under filesDir/characters.

package io.github.skm0078.audora.playback.voice

import android.content.Context
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * One file per book, named by a hash of its id, a line per character: key, a tab, the voice's name. The keys hold
 * character names from the book, so they stay in this file and never reach a log. A missing or unreadable file is
 * a book with no voices given yet; a failed save is logged and the voices hold in [CharacterCasts] until the
 * process ends.
 */
class CharacterFileStore(private val dir: File) : CharacterFiles {
    override fun load(book: BookId): Map<String, String> {
        val file = fileOf(book)
        return try {
            if (!file.isFile) return emptyMap()
            file.readLines(Charsets.UTF_8).mapNotNull { line ->
                line.split(TAB, limit = 2).takeIf { it.size == 2 && it.all(String::isNotBlank) }?.let { it[0] to it[1] }
            }.toMap()
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Character voices unreadable for book=${book.logLabel}, starting afresh: ${e.message}")
            emptyMap()
        }
    }

    override fun save(book: BookId, cast: Map<String, String>) {
        val file = fileOf(book)
        val temp = File(file.path + ".tmp")
        try {
            dir.mkdirs()
            FileOutputStream(temp).use { out ->
                out.write(cast.entries.joinToString("\n") { "${it.key}$TAB${it.value}" }.toByteArray(Charsets.UTF_8))
                out.fd.sync() // on disk before the rename, so a power cut never leaves an empty file in its place
            }
            if (!temp.renameTo(file)) throw IOException("cannot replace ${file.name}")
            AudoraLog.d(TAG, "Saved ${cast.size} character voices for book=${book.logLabel}")
        } catch (e: IOException) {
            temp.delete()
            AudoraLog.e(TAG, "Could not save character voices for book=${book.logLabel}", e)
        }
    }

    private fun fileOf(book: BookId): File {
        val digest = MessageDigest.getInstance(HASH).digest(book.value.toByteArray(Charsets.UTF_8))
        return File(dir, digest.joinToString("") { "%02x".format(it) } + SUFFIX)
    }

    companion object {
        private const val TAG = "Playback.Characters"
        private const val DIR_NAME = "characters"
        private const val HASH = "SHA-256"
        private const val SUFFIX = ".txt"
        private const val TAB = '\t'

        fun forContext(context: Context): CharacterFileStore =
            CharacterFileStore(File(context.applicationContext.filesDir, DIR_NAME))
    }
}
