// File: app/src/test/kotlin/io/github/skm0078/audora/StudioSaveTest.kt
// Purpose: Studio "Save as file" runs one save at a time and always finishes what it started, whichever screen asked.

package io.github.skm0078.audora

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudioSaveTest {
    private val saving = mutableStateOf<String?>(null)
    private val finished = mutableListOf<String>()

    @Test
    fun `a second save is refused while one runs, and the first one finishes`() = runTest(UnconfinedTestDispatcher()) {
        val encode = CompletableDeferred<String>()

        val first = startStudioSave(saving, backgroundScope, "moby", { encode.await() }) { finished += it }
        val second = startStudioSave(saving, backgroundScope, "moby", { "again" }) { finished += it }

        assertTrue(first)
        assertFalse(second)
        assertEquals("moby", saving.value)
        encode.complete("Moby Dick.m4a")
        assertEquals(listOf("Moby Dick.m4a"), finished)
        assertNull(saving.value)
    }

    @Test
    fun `a failed save still lets the next one start`() = runTest(UnconfinedTestDispatcher()) {
        // Like the app's scope, which logs a failed task instead of crashing.
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, _ -> })

        startStudioSave<String>(saving, scope, "moby", { error("disk full") }) { finished += it }

        assertNull(saving.value)
        assertTrue(startStudioSave(saving, scope, "moby", { "Moby Dick.m4a" }) { finished += it })
        assertEquals(listOf("Moby Dick.m4a"), finished)
    }
}
