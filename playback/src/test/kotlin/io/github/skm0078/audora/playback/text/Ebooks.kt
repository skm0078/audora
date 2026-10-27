// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/text/Ebooks.kt
// Purpose: The small test books in src/test/resources/ebooks (written by scripts/fixtures/make_ebook_fixtures.py).

package io.github.skm0078.audora.playback.text

object Ebooks {
    fun bytes(name: String): ByteArray =
        requireNotNull(Ebooks::class.java.getResourceAsStream("/ebooks/$name")) { "no test book $name" }
            .use { it.readBytes() }
}
