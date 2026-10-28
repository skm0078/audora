// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/flip/PageNeighbours.kt
// Purpose: The pages beside the shown one, drawn and read ahead so a page turn by hand lands on a finished page
//          (ADR 2026-10-28-page-flip). Holds at most the shown page and one on each side.

package io.github.skm0078.audora.ui.reader.flip

import io.github.skm0078.audora.core.config.PageFlipConfig
import io.github.skm0078.audora.ui.reader.PageContent
import io.github.skm0078.audora.ui.reader.PageImage

/**
 * Images are kept for one width only: a new width (a rotation, a zoom) drops them, as they would be drawn at the
 * wrong size. Sentences do not depend on the width and survive it. Not thread-safe: the reader model uses it from
 * its own scope only.
 */
internal class PageNeighbours(private val config: PageFlipConfig) {
    private val images = HashMap<Int, PageImage>()
    private val contents = HashMap<Int, PageContent.Ready>()
    private var widthPx: Int? = null

    /** The images held, by page, for the page view to slide in while a finger drags. */
    fun images(): Map<Int, PageImage> = images.toMap()

    fun image(page: Int, widthPx: Int): PageImage? = images[page]?.takeIf { widthPx == this.widthPx }

    fun content(page: Int): PageContent.Ready? = contents[page]

    fun keepImage(page: Int, widthPx: Int, image: PageImage) {
        useWidth(widthPx)
        images[page] = image
    }

    fun keepContent(page: Int, content: PageContent.Ready) {
        contents[page] = content
    }

    /** Drops images drawn at another width; the shown page's image is drawn again at the new one anyway. */
    fun useWidth(widthPx: Int) {
        if (widthPx != this.widthPx) images.clear()
        this.widthPx = widthPx
    }

    /** Pages to draw and read ahead around [page], the next first (most turns go forward), within the book. */
    fun wanted(page: Int, pageCount: Int): List<Int> = buildList {
        if (config.drawNext) add(page + 1)
        if (config.drawPrevious) add(page - 1)
    }.filter { it in 0 until pageCount }

    /** Keeps only [page] and the neighbours [wanted] for it, so memory never grows past three pages. */
    fun keepAround(page: Int, pageCount: Int) {
        val keep = wanted(page, pageCount).toSet() + page
        images.keys.retainAll(keep)
        contents.keys.retainAll(keep)
    }

    /** Drops the images (the text view, a zoom); the sentences are kept. */
    fun dropImages() = images.clear()

    /** Drops everything: the book was opened again, so pages may read differently. */
    fun clear() {
        images.clear()
        contents.clear()
        widthPx = null
    }
}
