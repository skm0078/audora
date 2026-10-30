// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/documents/GrantLimitDeviceTest.kt
// Purpose: At the lasting-grant limit a new file is refused a grant (so the library keeps a copy) before Android
//          would silently drop the oldest book's grant, while Audora's own copies still need none.

package io.github.skm0078.audora.playback.documents

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.config.LibraryConfig
import io.github.skm0078.audora.core.library.DocumentError
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GrantLimitDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val copies = SharedPdfCopies(context)
    private var made: File? = null

    @After
    fun cleanUp() {
        made?.parentFile?.deleteRecursively()
    }

    @Test
    fun aNewFileAtTheLimitIsRefusedAGrantAndNoGrantIsTaken() {
        val before = context.contentResolver.persistedUriPermissions.size
        // No room at all: whatever this test app already holds is at the limit.
        val access = SafDocumentAccess(context, copies, maxGrants = 0)

        val kept = access.keepAccess(PICKED)

        assertTrue("$kept", kept is Outcome.Failure && kept.error is DocumentError.GrantsFull)
        assertEquals(before, context.contentResolver.persistedUriPermissions.size)
    }

    @Test
    fun audorasOwnCopyNeedsNoGrantEvenAtTheLimit() {
        val file = copies.newCopy("book.pdf").also { made = it }
        file.writeText("%PDF-1.7\n")
        val access = SafDocumentAccess(context, copies, maxGrants = 0)

        assertEquals(Outcome.Success(Unit), access.keepAccess(Uri.fromFile(file).toString()))
    }

    @Test
    fun theLimitStopsShortOfAndroids() {
        assertTrue(LibraryConfig().maxKeptFileGrants < ANDROID_GRANT_LIMIT)
    }

    private companion object {
        const val PICKED = "content://com.android.providers.downloads.documents/document/4242"

        // UriGrantsManagerService.MAX_PERSISTED_URI_GRANTS on Android 11 and later.
        const val ANDROID_GRANT_LIMIT = 512
    }
}
