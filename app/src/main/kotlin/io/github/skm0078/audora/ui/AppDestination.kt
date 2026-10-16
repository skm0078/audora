// File: app/src/main/kotlin/io/github/skm0078/audora/ui/AppDestination.kt
// Purpose: The app's screens as a small saveable back stack, without a navigation library (no new dependency).

package io.github.skm0078.audora.ui

import io.github.skm0078.audora.core.library.BookId

/**
 * Four screens, at most three deep: the library is the root, the reader or Settings sits on top of it, and Voices
 * (P4-01) sits on Settings. Encoded as a string so it survives process death through saved state. Still flat enough
 * that Navigation Compose is not needed.
 */
sealed interface AppDestination {
    data object Library : AppDestination

    data object Settings : AppDestination

    data object Voices : AppDestination

    data class Reader(val id: BookId) : AppDestination

    /** Where back goes: Voices returns to Settings, everything else to the library. */
    val parent: AppDestination?
        get() = when (this) {
            Library -> null
            Voices -> Settings
            Settings, is Reader -> Library
        }

    fun toRoute(): String = when (this) {
        Library -> LIBRARY
        Settings -> SETTINGS
        Voices -> VOICES
        is Reader -> BOOK_PREFIX + id.value
    }

    companion object {
        private const val LIBRARY = "library"
        private const val SETTINGS = "settings"
        private const val VOICES = "settings/voices"
        private const val BOOK_PREFIX = "book/"

        /** Unknown or malformed routes (an older app version's saved state) fall back to the library. */
        fun fromRoute(route: String?): AppDestination = when {
            route == SETTINGS -> Settings

            route == VOICES -> Voices

            route != null && route.startsWith(BOOK_PREFIX) && route.length > BOOK_PREFIX.length ->
                Reader(BookId(route.removePrefix(BOOK_PREFIX)))

            else -> Library
        }
    }
}
