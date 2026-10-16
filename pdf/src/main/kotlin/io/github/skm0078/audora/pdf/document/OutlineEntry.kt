// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/document/OutlineEntry.kt
// Purpose: One entry of a PDF's outline (bookmarks): where it points and what is nested under it.

package io.github.skm0078.audora.pdf.document

/**
 * @property title the bookmark's text, from the PDF: show it, never log it.
 * @property page zero-based page it points to; null when the bookmark has no page destination.
 */
data class OutlineEntry(val title: String, val page: Int?, val children: List<OutlineEntry> = emptyList()) {
    /** Leaves the title out, so logging an entry never writes the PDF's text. */
    override fun toString() = "OutlineEntry(page=$page, children=${children.size})"
}
