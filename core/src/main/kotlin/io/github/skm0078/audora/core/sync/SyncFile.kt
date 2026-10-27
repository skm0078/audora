// File: core/src/main/kotlin/io/github/skm0078/audora/core/sync/SyncFile.kt
// Purpose: The sync folder's per-phone file and this phone's sync memory as plain text, a record per line.

package io.github.skm0078.audora.core.sync

/**
 * Plain text, so a person (or a cloud app's conflict copy) can be read and a damaged line costs only that line: a
 * record per line, fields split by tabs, with tabs, line breaks and backslashes in text escaped. The first line names
 * the format and its version; a file of a newer version is not read (that phone's ListenLock is newer), so nothing is
 * lost by reading half of it. Notes and quotes are the listener's own text and stay in this file only.
 */
object SyncFile {
    const val VERSION = 1

    fun encode(snapshot: SyncSnapshot): String = buildString {
        line(listOf(HEADER, VERSION.toString()))
        line(listOf(DEVICE, snapshot.deviceId))
        line(listOf(WRITTEN, snapshot.writtenAtMillis.toString()))
        snapshot.books.forEach { book ->
            line(listOf(BOOK, book.fingerprint, book.title, book.fileName.orEmpty()))
            book.progress?.let { line(listOf(PROGRESS) + anchorFields(it)) }
            book.bookmarks.forEach { line(listOf(BOOKMARK) + anchorFields(it.anchor) + it.note) }
            book.highlights.forEach { highlight ->
                val ends = listOf(highlight.endPage.toString(), highlight.endChar.toString())
                val texts = listOf(highlight.quote, highlight.note, highlight.color)
                line(listOf(HIGHLIGHT) + anchorFields(highlight.anchor) + ends + texts)
            }
        }
        snapshot.removals.forEach { line(listOf(REMOVED, it.markKey, it.atMillis.toString())) }
    }

    fun encode(memory: SyncMemory): String = buildString {
        line(listOf(MEMORY_HEADER, VERSION.toString()))
        memory.knownMarks.sorted().forEach { line(listOf(MARK, it)) }
        memory.removals.forEach { line(listOf(REMOVED, it.markKey, it.atMillis.toString())) }
        memory.knownBooks.sorted().forEach { line(listOf(BOOK, it)) }
        memory.dismissedBooks.sorted().forEach { line(listOf(DISMISSED, it)) }
    }

    /** This phone's memory in [text]; empty (a first sync) when it is missing or unreadable. */
    fun decodeMemory(text: String?): SyncMemory {
        val lines = text.orEmpty().lineSequence().map { it.split(TAB).map(SyncText::unescape) }.toList()
        val header = lines.firstOrNull()
        if (header?.getOrNull(0) != MEMORY_HEADER || header.getOrNull(1)?.toIntOrNull() != VERSION) return SyncMemory()
        val values = lines.drop(1).filter { it.size >= 2 && it[1].isNotEmpty() }.groupBy({ it[0] }, { it })
        return SyncMemory(
            knownMarks = values[MARK].orEmpty().mapTo(HashSet()) { it[1] },
            removals = values[REMOVED].orEmpty().mapNotNull { fields ->
                fields.getOrNull(2)?.toLongOrNull()?.let { SyncRemoval(fields[1], it) }
            },
            knownBooks = values[BOOK].orEmpty().mapTo(HashSet()) { it[1] },
            dismissedBooks = values[DISMISSED].orEmpty().mapTo(HashSet()) { it[1] },
        )
    }

    /** The snapshot in [text], or null when it is not a sync file this build can read. */
    fun decode(text: String): SyncSnapshot? {
        val lines = text.lineSequence().map { it.split(TAB).map(SyncText::unescape) }.toList()
        val header = lines.firstOrNull()
        if (header?.getOrNull(0) != HEADER || header.getOrNull(1)?.toIntOrNull() != VERSION) return null
        var device: String? = null
        var written = 0L
        val books = mutableListOf<SyncBook>()
        val removals = mutableListOf<SyncRemoval>()
        lines.drop(1).forEach { fields ->
            when (fields[0]) {
                DEVICE -> device = fields.getOrNull(1)

                WRITTEN -> written = fields.getOrNull(1)?.toLongOrNull() ?: 0L

                BOOK -> fields.getOrNull(1)?.takeIf(String::isNotEmpty)?.let { fingerprint ->
                    val fileName = fields.getOrNull(FILE_NAME_AT)?.ifEmpty { null }
                    books += SyncBook(fingerprint, fields.getOrNull(TITLE_AT).orEmpty(), fileName)
                }

                else -> addTo(books, removals, fields)
            }
        }
        return device?.takeIf(String::isNotEmpty)?.let { SyncSnapshot(it, written, books, removals) }
    }

    /** A progress, bookmark, highlight or removal line; one that does not parse is skipped. */
    private fun addTo(books: MutableList<SyncBook>, removals: MutableList<SyncRemoval>, fields: List<String>) {
        val last = books.lastOrNull()
        val anchor = anchorOf(fields)
        when {
            fields[0] == REMOVED -> fields.getOrNull(2)?.toLongOrNull()?.let { removals += SyncRemoval(fields[1], it) }

            last == null || anchor == null -> Unit

            fields[0] == PROGRESS -> books[books.lastIndex] = last.copy(progress = anchor)

            fields[0] == BOOKMARK && fields.size > NOTE_AT ->
                books[books.lastIndex] = last.copy(bookmarks = last.bookmarks + SyncBookmark(anchor, fields[NOTE_AT]))

            fields[0] == HIGHLIGHT -> highlightOf(anchor, fields)?.let {
                books[books.lastIndex] = last.copy(highlights = last.highlights + it)
            }
        }
    }

    private fun highlightOf(anchor: SyncAnchor, fields: List<String>): SyncHighlight? {
        val endPage = fields.getOrNull(END_PAGE_AT)?.toIntOrNull()
        val endChar = fields.getOrNull(END_CHAR_AT)?.toIntOrNull()
        val color = fields.getOrNull(COLOR_AT)
        if (endPage == null || endChar == null || color == null) return null
        return SyncHighlight(anchor, endPage, endChar, fields[QUOTE_AT], fields[HIGHLIGHT_NOTE_AT], color)
    }

    private fun anchorFields(anchor: SyncAnchor) = with(anchor) {
        listOf(
            page.toString(),
            charStart.toString(),
            fingerprint.toString(),
            tailFingerprint.toString(),
            pipelineVersion.toString(),
            savedAtMillis.toString(),
        )
    }

    /** The anchor in fields 1 to 6, or null when any is missing or not a number. */
    private fun anchorOf(fields: List<String>): SyncAnchor? {
        val numbers = (1..ANCHOR_FIELDS).map { fields.getOrNull(it)?.toLongOrNull() }
        if (numbers.any { it == null }) return null
        val values = numbers.requireNoNulls()
        val (page, charStart, fingerprint) = values
        val pipeline = values[PIPELINE_AT].toInt()
        return SyncAnchor(page.toInt(), charStart.toInt(), fingerprint, values[TAIL_AT], pipeline, values.last())
    }

    private fun StringBuilder.line(fields: List<String>) {
        fields.joinTo(this, TAB) { SyncText.escape(it) }
        append('\n')
    }

    private const val TAB = "\t"
    private const val TITLE_AT = 2
    private const val FILE_NAME_AT = 3
    private const val TAIL_AT = 3
    private const val PIPELINE_AT = 4
    private const val HEADER = "listenlock-sync"
    private const val DEVICE = "device"
    private const val WRITTEN = "written"
    private const val BOOK = "book"
    private const val PROGRESS = "progress"
    private const val BOOKMARK = "bookmark"
    private const val HIGHLIGHT = "highlight"
    private const val REMOVED = "removed"
    private const val MEMORY_HEADER = "listenlock-sync-memory"
    private const val MARK = "mark"
    private const val DISMISSED = "dismissed"
    private const val ANCHOR_FIELDS = 6
    private const val NOTE_AT = 7
    private const val END_PAGE_AT = 7
    private const val END_CHAR_AT = 8
    private const val QUOTE_AT = 9
    private const val HIGHLIGHT_NOTE_AT = 10
    private const val COLOR_AT = 11
}
