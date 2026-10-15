// File: app/src/main/kotlin/io/github/skm0078/audora/ui/AppDestination.kt
// Purpose: The app's screens as a small saveable back stack, without a navigation library (no new dependency).

package io.github.skm0078.audora.ui

import io.github.skm0078.audora.core.library.BookId

/**
 * Three screens, at most two deep: the library is the root, the reader or Settings sits on top of it. Encoded
 * as a string so it survives process death through saved state. The reader (P1-05) kept the graph this flat,
 * so Navigation Compose is still not needed.
 */
sealed interface AppDestination {
    data object Library : AppDestination

    data object Settings : AppDestination

    data class Reader(val id: BookId) : AppDestination

    fun toRoute(): String = when (this) {
        Library -> LIBRARY
        Settings -> SETTINGS
        is Reader -> BOOK_PREFIX + id.value
    }

    companion object {
        private const val LIBRARY = "library"
        private const val SETTINGS = "settings"
        private const val BOOK_PREFIX = "book/"

        /** Unknown or malformed routes (an older app version's saved state) fall back to the library. */
        fun fromRoute(route: String?): AppDestination = when {
            route == SETTINGS -> Settings

            route != null && route.startsWith(BOOK_PREFIX) && route.length > BOOK_PREFIX.length ->
                Reader(BookId(route.removePrefix(BOOK_PREFIX)))

            else -> Library
        }
    }
}
