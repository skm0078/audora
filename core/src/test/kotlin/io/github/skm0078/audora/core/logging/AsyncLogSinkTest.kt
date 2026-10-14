// File: core/src/test/kotlin/io/github/skm0078/audora/core/logging/AsyncLogSinkTest.kt
// Purpose: Verifies async logging keeps order and that flush waits for queued entries.

package io.github.skm0078.audora.core.logging

import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncLogSinkTest {
    @Test
    fun `flush waits until queued entries are written, in order`() {
        val written = Collections.synchronizedList(mutableListOf<String>())
        val sink = AsyncLogSink(LogSink { written += it.message })

        (1..100).forEach { sink.write(LogEntry(0L, LogLevel.INFO, "T", "m$it", "main")) }

        assertTrue(sink.flush(timeoutMillis = 5_000))
        assertEquals((1..100).map { "m$it" }, written.toList())
    }
}
