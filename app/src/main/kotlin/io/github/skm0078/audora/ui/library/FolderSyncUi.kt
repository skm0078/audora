// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/FolderSyncUi.kt
// Purpose: Whether the folder sync sheet (CF-16) is open; the library's ⋮ entry sets it.

package io.github.skm0078.audora.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-wide, so the library's menu and the sheet need no state passed between them. */
object FolderSyncUi {
    var open by mutableStateOf(false)
}
