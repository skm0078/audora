// File: app/src/main/kotlin/io/github/skm0078/audora/PendingShares.kt
// Purpose: A file or text shared from another app, held for the whole process until it is in the library, so a
//          rotation or theme switch in the middle of adding it does not lose it.

package io.github.skm0078.audora

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lives as long as the process, not the screen; a share is still lost if Android kills the process mid-add, as
 * before. Main thread only, like the Compose state it is.
 */
internal object PendingShares {
    /** A PDF opened or shared from another app, until it is added. */
    var pdf: String? by mutableStateOf(null)

    /** Shared text, until it is saved as a book. */
    var text: SharedText? by mutableStateOf(null)
}
