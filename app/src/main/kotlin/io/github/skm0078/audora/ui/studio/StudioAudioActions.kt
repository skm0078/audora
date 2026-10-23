// File: app/src/main/kotlin/io/github/skm0078/audora/ui/studio/StudioAudioActions.kt
// Purpose: The actions of a book's studio dialog (ADR-062), carried out by the library.

package io.github.skm0078.audora.ui.studio

import io.github.skm0078.audora.core.library.Book

/** What the listener can do from the studio dialog; the library carries each out. */
interface StudioAudioActions {
    fun start(book: Book)

    fun listen(book: Book)

    fun saveAsFile(book: Book)

    fun close()
}
