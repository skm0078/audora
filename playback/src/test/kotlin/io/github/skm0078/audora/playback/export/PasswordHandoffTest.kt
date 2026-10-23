// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/PasswordHandoffTest.kt
// Purpose: The in-memory password handoff is taken once, expires, and is removed when the job cannot start.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.library.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PasswordHandoffTest {
    private var now = 0L
    private val handoff = PasswordHandoff(ttlMs = TTL_MS, clock = { now })
    private val book = BookId("book")

    @Test
    fun `a password is taken once`() {
        handoff.put(book, "secret")
        assertEquals("secret", handoff.take(book))
        assertNull(handoff.take(book))
    }

    @Test
    fun `a password is not taken after it expires, and the next call drops it`() {
        handoff.put(book, "secret")
        now = TTL_MS + 1
        assertNull(handoff.take(book))
        assertEquals(0, handoff.heldCount)
    }

    @Test
    fun `an expired password of another book is dropped by the next put`() {
        handoff.put(BookId("other"), "old")
        now = TTL_MS + 1
        handoff.put(book, "new")
        assertEquals(1, handoff.heldCount)
    }

    @Test
    fun `no password clears an earlier one`() {
        handoff.put(book, "secret")
        handoff.put(book, null)
        assertNull(handoff.take(book))
    }

    @Test
    fun `a start that throws removes the password`() {
        assertThrows(IllegalStateException::class.java) {
            handoff.handOver(book, "secret") { throw IllegalStateException("not allowed") }
        }
        assertEquals(0, handoff.heldCount)
    }

    private companion object {
        const val TTL_MS = 1_000L
    }
}
