// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryAction.kt
// Purpose: The library's ⋮ menu entries, added without touching the top bar's layout (plan items 62, 26, 28).

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import io.github.skm0078.audora.R

/** Each entry shows in the library's ⋮ menu, in this order; the activity does the work. */
enum class LibraryAction(@StringRes val label: Int) {
    /** Takes a photo of a page with the camera app and reads it as a one-page book (plan item 62, ADR-043). */
    POINT_AND_READ(R.string.library_point_and_read),

    /** Lists the watched folders, whose new books join the library when it opens (plan item 26, ADR-044). */
    WATCHED_FOLDERS(R.string.library_watched_folders),
}
