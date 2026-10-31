// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/JoinWantedTest.kt
// Purpose: The mini player and the lock's saver join the playback service only while shown and while it is up.

package io.github.skm0078.audora.ui.reader

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinWantedTest {
    private val visible = MutableStateFlow(true)
    private val up = MutableStateFlow(false)

    @Test
    fun `a fresh app on screen does not start the service`() = runTest {
        val wanted = mutableListOf<Boolean>()
        val job = launch { joinWanted(visible, up).toList(wanted) }
        runCurrent()
        job.cancel()
        assertEquals(listOf(false), wanted)
    }

    @Test
    fun `it joins once the reader starts the service, and lets go when hidden or when the service ends`() = runTest {
        val wanted = mutableListOf<Boolean>()
        val job = launch { joinWanted(visible, up).toList(wanted) }
        runCurrent()
        up.value = true
        runCurrent()
        visible.value = false
        runCurrent()
        visible.value = true
        runCurrent()
        up.value = false
        runCurrent()
        job.cancel()
        assertEquals(listOf(false, true, false, true, false), wanted)
    }

    @Test
    fun `a service that comes up while the app is hidden is joined when the app is shown again`() = runTest {
        visible.value = false
        val wanted = mutableListOf<Boolean>()
        val job = launch { joinWanted(visible, up).toList(wanted) }
        runCurrent()
        up.value = true
        runCurrent()
        visible.value = true
        runCurrent()
        job.cancel()
        assertEquals(listOf(false, true), wanted)
    }
}
