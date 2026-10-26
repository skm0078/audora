// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/WebBooksOnPhone.kt
// Purpose: The one WebBooks of the process, saving books beside the library's own copies (CF-12, CF-15).

package io.github.skm0078.audora.playback.web

import android.content.Context
import io.github.skm0078.audora.core.config.WebBooksConfig
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import java.io.File

/**
 * One per process, so a download keeps going when the screen rotates or closes and every screen sees the same job.
 * Books land in the shared copies folder ([SharedPdfCopies.newCopy]): the library keeps them like a shared file, and
 * removing the book deletes its file.
 */
object WebBooksOnPhone {
    private const val CATALOGUES_FILE = "opds-catalogues.txt"

    @Volatile
    private var instance: WebBooks? = null

    fun get(context: Context): WebBooks = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(app: Context): WebBooks {
        val config = WebBooksConfig()
        val copies = SharedPdfCopies(app)
        return WebBooks(
            catalogues = OpdsCatalogues(File(app.filesDir, CATALOGUES_FILE), config.maxCatalogues),
            newBookFile = copies::newCopy,
            config = config,
        )
    }
}
