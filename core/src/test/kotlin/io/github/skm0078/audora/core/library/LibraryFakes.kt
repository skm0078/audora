// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/LibraryFakes.kt
// Purpose: In-memory BookStore and DocumentAccess that behave like Room and the Storage Access Framework.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.result.Outcome
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Enforces the same unique keys as the Room table (id, documentUri, fingerprint). */
class FakeBookStore : BookStore {
    private val books = MutableStateFlow<Map<BookId, Book>>(emptyMap())
    var failWrites = false
    var failReads = false

    val all: List<Book> get() = books.value.values.toList()

    override fun observeAll(): Flow<List<Book>> = books.map { map ->
        if (failReads) error("database unreadable")
        map.values.sortedByDescending(Book::recentAtMillis)
    }

    override suspend fun byId(id: BookId): Book? = books.value[id]

    override suspend fun byFingerprint(fingerprint: String): Book? = all.find { it.fingerprint == fingerprint }

    override suspend fun byDocumentUri(documentUri: String): Book? = all.find { it.documentUri == documentUri }

    override suspend fun insert(book: Book): Outcome<Unit, StorageError> {
        if (book.id in books.value) return Outcome.Failure(StorageError("duplicate id"))
        return write(book)
    }

    override suspend fun update(book: Book): Outcome<Unit, StorageError> {
        if (book.id !in books.value) return Outcome.Failure(StorageError("no such book"))
        return write(book)
    }

    override suspend fun delete(id: BookId): Outcome<Unit, StorageError> {
        if (failWrites) return Outcome.Failure(StorageError("disk full"))
        books.value -= id
        return Outcome.Success(Unit)
    }

    private fun write(book: Book): Outcome<Unit, StorageError> {
        val others = books.value.values.filter { it.id != book.id }
        val clash = others.any { it.documentUri == book.documentUri || it.fingerprint == book.fingerprint }
        return when {
            failWrites -> Outcome.Failure(StorageError("disk full"))

            clash -> Outcome.Failure(StorageError("unique constraint"))

            else -> {
                books.value += book.id to book
                Outcome.Success(Unit)
            }
        }
    }
}

/** Documents by URI; a grant is kept only while [granted] holds the URI, like persisted URI permissions. */
class FakeDocuments : DocumentAccess {
    data class Doc(val name: String?, val bytes: ByteArray, val reportsSize: Boolean = true)

    val docs = mutableMapOf<String, Doc>()
    val granted = mutableSetOf<String>()
    val oneTimeOnly = mutableSetOf<String>()
    val released = mutableListOf<String>()
    val pruned = mutableListOf<Set<String>>()

    /** Each batch check's URIs, in call order: a batch is where the real access layer reads its grants once. */
    val batches = mutableListOf<Collection<String>>()

    fun put(uri: String, name: String?, bytes: ByteArray) {
        docs[uri] = Doc(name, bytes)
    }

    override fun keepAccess(documentUri: String): Outcome<Unit, DocumentError> = when (documentUri) {
        in oneTimeOnly -> Outcome.Failure(DocumentError.CannotKeepAccess("no persistable grant"))

        !in docs -> Outcome.Failure(DocumentError.NotFound("gone"))

        else -> {
            granted += documentUri
            Outcome.Success(Unit)
        }
    }

    override fun releaseAccess(documentUri: String) {
        granted -= documentUri
        released += documentUri
    }

    override fun describe(documentUri: String): Outcome<DocumentInfo, DocumentError> {
        val doc = docs[documentUri] ?: return Outcome.Failure(DocumentError.NotFound("gone"))
        return Outcome.Success(DocumentInfo(doc.name, doc.bytes.size.toLong().takeIf { doc.reportsSize }))
    }

    override fun openStream(documentUri: String): Outcome<InputStream, DocumentError> {
        val doc = docs[documentUri] ?: return Outcome.Failure(DocumentError.NotFound("gone"))
        return Outcome.Success(ByteArrayInputStream(doc.bytes))
    }

    override fun availability(documentUri: String): BookAvailability = when {
        documentUri !in granted -> BookAvailability.ACCESS_LOST
        documentUri !in docs -> BookAvailability.MISSING
        else -> BookAvailability.AVAILABLE
    }

    override fun availability(documentUris: Collection<String>): Map<String, BookAvailability> {
        batches += documentUris.toList()
        return super.availability(documentUris)
    }

    override fun pruneUnused(inUse: Set<String>) {
        pruned += inUse
    }
}

/** A minimal byte sequence that passes the PDF header check; [tag] makes contents differ. */
fun pdfBytes(tag: String): ByteArray = "%PDF-1.7\n% $tag\n".toByteArray(Charsets.US_ASCII)
