// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/BookSource.kt
// Purpose: Where a book's file lives (this phone, Google Drive, OneDrive, Dropbox or another app), read from the
//          authority of its document URI, so the library can say so and offer to keep a copy on the phone.

package io.github.skm0078.audora.core.library

/**
 * Where a book's file is read from. Only the URI's authority is looked at: never the path, which names the file.
 * [isOnPhone] books are read from the phone's own storage and work with no internet; any other one is read through
 * another app, which may need the internet, and can be kept on the phone as ListenLock's own copy.
 */
enum class BookSource(val isOnPhone: Boolean) {
    /** Phone storage, an SD card, Downloads, or ListenLock's own copy. */
    PHONE(isOnPhone = true),

    GOOGLE_DRIVE(isOnPhone = false),

    ONEDRIVE(isOnPhone = false),

    DROPBOX(isOnPhone = false),

    /** An app ListenLock does not know (a file manager, another cloud): its files may or may not be on the phone. */
    OTHER_APP(isOnPhone = false),
}

/**
 * The [BookSource] of a document URI. Pure string work (no Android), cheap enough for every book on every change of
 * the library. Authorities are the ones each app's document provider declares; a new one is a line in [KNOWN].
 */
object BookSources {
    fun of(documentUri: String): BookSource {
        if (documentUri.startsWith(FILE_SCHEME, ignoreCase = true)) return BookSource.PHONE
        return KNOWN[authorityOf(documentUri)] ?: BookSource.OTHER_APP
    }

    /**
     * "content://<authority>/..." without a user prefix: a work profile's URI reads "content://10@<authority>/...".
     * Empty for anything else.
     */
    internal fun authorityOf(documentUri: String): String {
        if (!documentUri.startsWith(CONTENT_SCHEME, ignoreCase = true)) return ""
        val rest = documentUri.substring(CONTENT_SCHEME.length)
        return rest.substringBefore('/').substringAfter('@').lowercase()
    }

    private const val FILE_SCHEME = "file:"
    private const val CONTENT_SCHEME = "content://"

    private val KNOWN: Map<String, BookSource> = mapOf(
        // Android's own storage, SD cards, Downloads and the media store.
        "com.android.externalstorage.documents" to BookSource.PHONE,
        "com.android.providers.downloads.documents" to BookSource.PHONE,
        "com.android.providers.media.documents" to BookSource.PHONE,
        "media" to BookSource.PHONE,
        "downloads" to BookSource.PHONE,
        "com.google.android.apps.docs.storage" to BookSource.GOOGLE_DRIVE,
        "com.google.android.apps.docs.storage.legacy" to BookSource.GOOGLE_DRIVE,
        "com.microsoft.skydrive.content.storageaccessprovider" to BookSource.ONEDRIVE,
        "com.dropbox.product.android.dbapp.document_provider.documents" to BookSource.DROPBOX,
    )
}
