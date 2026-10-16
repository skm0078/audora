// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PageImage.kt
// Purpose: A rendered PDF page as the reader holds it: pixels for the screen, geometry for highlight and taps.

package io.github.skm0078.audora.ui.reader

import androidx.compose.ui.graphics.ImageBitmap
import io.github.skm0078.audora.pdf.render.PageTransform

/** The reader model only reads [transform] and [aspectRatio]; only the screen asks for the pixels. */
interface PageImage {
    /** Maps the page's line boxes onto the image, in fractions of its size. */
    val transform: PageTransform

    /** Width over height of the page as displayed. */
    val aspectRatio: Float

    fun imageBitmap(): ImageBitmap
}

/** How the reader shows a page: the rendered PDF with the spoken sentence highlighted, or its sentences as text. */
enum class ReaderView { PAGE, TEXT }

sealed interface PageImageState {
    /** Not wanted (text view) or not yet possible (the screen has not reported its width). */
    data object None : PageImageState

    data object Loading : PageImageState

    /** [page] is the page the image shows: a late render of a page left behind is never drawn on another. */
    data class Ready(val page: Int, val image: PageImage) : PageImageState

    data object Failed : PageImageState
}
