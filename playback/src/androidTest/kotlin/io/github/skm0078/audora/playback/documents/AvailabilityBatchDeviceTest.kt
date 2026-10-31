// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/documents/AvailabilityBatchDeviceTest.kt
// Purpose: The batched file check (ADR 2026-10-31-lighter-file-checks) answers exactly as one check per file does:
//          Audora's own copy present or deleted, and a file with no lasting grant.

package io.github.skm0078.audora.playback.documents

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.library.BookAvailability
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvailabilityBatchDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val copies = SharedPdfCopies(context)
    private val made = mutableListOf<File>()

    @After
    fun cleanUp() {
        made.forEach { it.parentFile?.deleteRecursively() }
    }

    @Test
    fun aBatchAnswersAsOneCheckPerFileDoes() {
        val kept = copy("kept.pdf")
        val deleted = copy("deleted.pdf").also { File(Uri.parse(it).path!!).delete() }
        val access = SafDocumentAccess(context, copies)
        val uris = listOf(kept, deleted, NEVER_GRANTED)

        val batch = access.availability(uris)

        assertEquals(
            mapOf(
                kept to BookAvailability.AVAILABLE,
                deleted to BookAvailability.MISSING,
                NEVER_GRANTED to BookAvailability.ACCESS_LOST,
            ),
            batch,
        )
        assertEquals(uris.associateWith { access.availability(it) }, batch)
    }

    private fun copy(name: String): String {
        val file = copies.newCopy(name).also { made += it }
        file.writeText("%PDF-1.7\n")
        return Uri.fromFile(file).toString()
    }

    private companion object {
        const val NEVER_GRANTED = "content://com.android.providers.downloads.documents/document/4243"
    }
}
