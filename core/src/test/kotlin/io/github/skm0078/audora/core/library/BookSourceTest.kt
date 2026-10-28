// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/BookSourceTest.kt
// Purpose: Table-driven checks of where a book's document URI says its file lives.

package io.github.skm0078.audora.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceTest {
    @Test
    fun `document URIs name where the file lives`() {
        val cases = listOf(
            "content://com.android.externalstorage.documents/document/primary%3ABooks%2Fgita.pdf" to BookSource.PHONE,
            "content://com.android.externalstorage.documents/document/1A2B-3C4D%3Agora.pdf" to BookSource.PHONE,
            "content://com.android.providers.downloads.documents/document/msf%3A42" to BookSource.PHONE,
            "content://com.android.providers.media.documents/document/document%3A17" to BookSource.PHONE,
            "content://media/external/file/17" to BookSource.PHONE,
            "file:///data/user/0/io.github.skm0078.audora/files/shared-pdfs/abc/gita.pdf" to BookSource.PHONE,
            "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3Dencoded" to
                BookSource.GOOGLE_DRIVE,
            "content://com.google.android.apps.docs.storage.legacy/enc%3Dabc" to BookSource.GOOGLE_DRIVE,
            "content://com.microsoft.skydrive.content.StorageAccessProvider/document/1" to BookSource.ONEDRIVE,
            "content://com.dropbox.product.android.dbapp.document_provider.documents/document/x" to
                BookSource.DROPBOX,
            "content://10@com.google.android.apps.docs.storage/document/x" to BookSource.GOOGLE_DRIVE,
            "CONTENT://COM.ANDROID.EXTERNALSTORAGE.DOCUMENTS/document/x" to BookSource.PHONE,
            "content://org.nextcloud.documents/document/x" to BookSource.OTHER_APP,
            "content://com.whatsapp.provider.media/item/1" to BookSource.OTHER_APP,
            "https://example.org/book.pdf" to BookSource.OTHER_APP,
            "" to BookSource.OTHER_APP,
        )
        for ((uri, expected) in cases) {
            assertEquals("uri=$uri", expected, BookSources.of(uri))
        }
    }

    @Test
    fun `only the phone's own storage counts as on the phone`() {
        assertTrue(BookSource.PHONE.isOnPhone)
        BookSource.entries.filter { it != BookSource.PHONE }.forEach { assertFalse(it.name, it.isOnPhone) }
    }

    @Test
    fun `the authority never carries the path`() {
        val authority = BookSources.authorityOf("content://com.android.externalstorage.documents/document/secret.pdf")
        assertEquals("com.android.externalstorage.documents", authority)
    }
}
