// File: app/src/main/kotlin/io/github/skm0078/audora/ui/firstrun/FirstRunStep.kt
// Purpose: The first-run steps (UI plan, First run) and when they show: once, to someone with an empty library.

package io.github.skm0078.audora.ui.firstrun

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.ui.library.LibraryListState

/** The first-run screens' steps, in order. */
enum class FirstRunStep { LANGUAGES, VOICES, BACKGROUND, BOOK }

/**
 * Whether to show the first-run screens: never once finished, never to someone who already has books (an update
 * from a version without them), never if the library failed to load; null while the library is still loading.
 */
fun shouldShowFirstRun(done: Boolean, library: LibraryListState): Boolean? = when {
    done -> false
    library is LibraryListState.Loaded -> library.all.isEmpty()
    library is LibraryListState.Failed -> false
    else -> null
}

/** The languages a new listener starts with: English, which has a voice inside the app. */
val FIRST_RUN_LANGUAGES: Set<Lang> = setOf(Lang.EN)
