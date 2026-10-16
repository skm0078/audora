// File: app/src/main/kotlin/io/github/skm0078/audora/ui/AppDestination.kt
// Purpose: The app's screens as a small saveable back stack, without a navigation library (no new dependency).

package io.github.skm0078.audora.ui

import io.github.skm0078.audora.core.library.BookId

/**
 * A few screens, at most three deep: the library is the root, the reader or Settings sits on top of it, Voices
 * (P4-01) and Voices & licences (P3-06) sit on Settings, and background reading setup (P1-06) sits on the library
 * or Settings. Encoded as a string so it survives process death through saved state. Still flat enough that
 * Navigation Compose is not needed.
 */
sealed interface AppDestination {
    data object Library : AppDestination

    data object Settings : AppDestination

    data object Voices : AppDestination

    data object Licences : AppDestination

    data class Reader(val id: BookId) : AppDestination

    /** Background reading setup (P1-06), opened from the library's card or from Settings; back returns there. */
    data class BatterySetup(val fromSettings: Boolean) : AppDestination

    /** Where back goes: Voices and licences to Settings, setup to where it was opened from, the rest to the library. */
    val parent: AppDestination?
        get() = when (this) {
            Library -> null
            Voices, Licences -> Settings
            is BatterySetup -> if (fromSettings) Settings else Library
            Settings, is Reader -> Library
        }

    fun toRoute(): String = when (this) {
        Library -> LIBRARY
        Settings -> SETTINGS
        Voices -> VOICES
        Licences -> LICENCES
        is Reader -> BOOK_PREFIX + id.value
        is BatterySetup -> if (fromSettings) BATTERY_SETUP_FROM_SETTINGS else BATTERY_SETUP
    }

    companion object {
        private const val LIBRARY = "library"
        private const val SETTINGS = "settings"
        private const val BATTERY_SETUP = "battery-setup"
        private const val BATTERY_SETUP_FROM_SETTINGS = "settings/battery-setup"
        private const val VOICES = "settings/voices"
        private const val LICENCES = "settings/licences"
        private const val BOOK_PREFIX = "book/"

        /** Unknown or malformed routes (an older app version's saved state) fall back to the library. */
        fun fromRoute(route: String?): AppDestination = when {
            route == SETTINGS -> Settings

            route == BATTERY_SETUP -> BatterySetup(fromSettings = false)

            route == BATTERY_SETUP_FROM_SETTINGS -> BatterySetup(fromSettings = true)

            route == VOICES -> Voices

            route == LICENCES -> Licences

            route != null && route.startsWith(BOOK_PREFIX) && route.length > BOOK_PREFIX.length ->
                Reader(BookId(route.removePrefix(BOOK_PREFIX)))

            else -> Library
        }
    }
}
