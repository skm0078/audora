// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/SafImportTree.kt
// Purpose: ImportTree over a folder the user picked with the system folder picker (Storage Access Framework).

package io.github.skm0078.audora.speech.voices

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Lists with DocumentsContract queries (no androidx.documentfile: no new dependency). The picker's grant lasts while
 * the app runs, which covers the import; Audora keeps no lasting access to the folder because it copies the files.
 */
class SafImportTree(private val resolver: ContentResolver, private val treeUri: Uri) : ImportTree {
    private val documentIds = mutableMapOf<String, String>()

    override val name: String
        get() = queryName(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId())) ?: "Voice"

    override fun files(maxDepth: Int, limit: Int): List<ImportFile> {
        documentIds.clear()
        return Listing(maxDepth, limit).apply { walk(rootId(), prefix = "", depth = 0) }.found
    }

    override fun open(path: String): InputStream {
        val id = documentIds[path] ?: throw FileNotFoundException(path)
        return resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
            ?: throw IOException("provider returned no stream for $path")
    }

    private fun rootId(): String = DocumentsContract.getTreeDocumentId(treeUri)

    private inner class Listing(private val maxDepth: Int, private val limit: Int) {
        val found = mutableListOf<ImportFile>()

        fun walk(parentId: String, prefix: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor = resolver.query(children, COLUMNS, null, null, null)
                ?: throw IOException("cannot list the picked folder")
            val folders = mutableListOf<Pair<String, String>>()
            cursor.use {
                while (it.moveToNext() && found.size < limit) {
                    val path = prefix + it.getString(NAME_COLUMN)
                    if (it.getString(MIME_COLUMN) == Document.MIME_TYPE_DIR) {
                        folders += it.getString(ID_COLUMN) to "$path/"
                    } else {
                        documentIds[path] = it.getString(ID_COLUMN)
                        found += ImportFile(path, if (it.isNull(SIZE_COLUMN)) 0 else it.getLong(SIZE_COLUMN))
                    }
                }
            }
            if (depth < maxDepth) folders.forEach { (id, path) -> walk(id, path, depth + 1) }
        }
    }

    private fun queryName(uri: Uri): String? =
        resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    private companion object {
        val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        const val ID_COLUMN = 0
        const val NAME_COLUMN = 1
        const val MIME_COLUMN = 2
        const val SIZE_COLUMN = 3
    }
}
