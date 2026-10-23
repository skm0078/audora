// File: app/src/main/kotlin/io/github/skm0078/audora/ui/AppDestination.kt
// Purpose: The app's screens as a small saveable back stack, without a navigation library (no new dependency).

package io.github.skm0078.audora.ui

import io.github.skm0078.audora.core.library.BookId

/**
 * A few screens, at most three deep: three tabs (Library, Notes, Settings; ADR-036) with the library as the root,
 * the reader or the full player on top of the library, Voices (P4-01) and Voices & licences (P3-06) on Settings, and
 * background reading setup (P1-06) on the library or Settings. Encoded as a string so it survives process death
 * through saved state. Still flat enough that Navigation Compose is not needed.
 */
sealed interface AppDestination {
    data object Library : AppDestination

    data object Notes : AppDestination

    data object Settings : AppDestination

    /** Settings → Voices (P4-01); [fromStorage] when Storage's Manage opened it, so back returns there. */
    data class Voices(val fromStorage: Boolean = false) : AppDestination

    data object Licences : AppDestination

    /** Settings → Storage (UI plan): what Audora's data takes and how to free it. */
    data object Storage : AppDestination

    /**
     * @property from the tab it was opened over (a bookmark from Notes, a book from the library), where back returns.
     * @property viaPlayer opened from the full player's "Show the page": back returns to that player.
     */
    data class Reader(val id: BookId, val from: AppTab = AppTab.LIBRARY, val viaPlayer: Boolean = false) :
        AppDestination

    /**
     * The full player (ADR-036), opened from the mini player; the same book's reader is one tap away.
     *
     * @property from the tab whose mini player opened it, where back returns.
     */
    data class Player(val id: BookId, val from: AppTab = AppTab.LIBRARY) : AppDestination

    /** Background reading setup (P1-06), opened from the library's card or from Settings; back returns there. */
    data class BatterySetup(val fromSettings: Boolean) : AppDestination

    /**
     * Where back goes: Voices and licences to Settings; setup, the reader and the player to where they were opened
     * from; the other tabs to the library.
     */
    val parent: AppDestination?
        get() = when (this) {
            Library -> null
            is Voices -> if (fromStorage) Storage else Settings
            Licences, Storage -> Settings
            is BatterySetup -> if (fromSettings) Settings else Library
            is Reader -> if (viaPlayer) Player(id, from) else from.destination
            is Player -> from.destination
            Notes, Settings -> Library
        }

    /** The tab this screen shows under; null for screens drawn over the tabs (reader, player, sub-pages). */
    val tab: AppTab?
        get() = when (this) {
            Library -> AppTab.LIBRARY
            Notes -> AppTab.NOTES
            Settings -> AppTab.SETTINGS
            is Voices, Licences, Storage, is BatterySetup, is Reader, is Player -> null
        }

    /**
     * Which drawn screen this is, for screen-change motion: the tabs share one frame, and one book's reader and player
     * are one screen (one ReaderModel, ADR-036 §6), so moving between them never rebuilds or animates it.
     */
    val screen: String
        get() = when (this) {
            Library, Notes, Settings -> TABS_SCREEN
            is Reader -> BOOK_PREFIX + id.value
            is Player -> BOOK_PREFIX + id.value
            is Voices, Licences, Storage, is BatterySetup -> toRoute()
        }

    fun toRoute(): String = when (this) {
        Library -> LIBRARY
        Notes -> NOTES
        Settings -> SETTINGS
        is Voices -> if (fromStorage) VOICES_FROM_STORAGE else VOICES
        Licences -> LICENCES
        Storage -> STORAGE
        is Reader -> bookRoute(BOOK_PREFIX, id, from, viaPlayer)
        is Player -> bookRoute(PLAYER_PREFIX, id, from, viaPlayer = false)
        is BatterySetup -> if (fromSettings) BATTERY_SETUP_FROM_SETTINGS else BATTERY_SETUP
    }

    companion object {
        private const val TABS_SCREEN = "tabs"
        private const val LIBRARY = "library"
        private const val NOTES = "notes"
        private const val SETTINGS = "settings"
        private const val BATTERY_SETUP = "battery-setup"
        private const val BATTERY_SETUP_FROM_SETTINGS = "settings/battery-setup"
        private const val VOICES = "settings/voices"
        private const val VOICES_FROM_STORAGE = "settings/storage/voices"
        private const val LICENCES = "settings/licences"
        private const val STORAGE = "settings/storage"
        private const val BOOK_PREFIX = "book/"
        private const val PLAYER_PREFIX = "player/"
        private const val QUERY = '?'
        private const val PARAMS = '&'
        private const val FROM = "from="
        private const val VIA_PLAYER = "via=player"

        /** "book/<id>", with "?from=notes&via=player" only when they differ from the defaults (older routes parse). */
        private fun bookRoute(prefix: String, id: BookId, from: AppTab, viaPlayer: Boolean): String {
            val params = listOfNotNull(
                (FROM + from.name.lowercase()).takeIf { from != AppTab.LIBRARY },
                VIA_PLAYER.takeIf { viaPlayer },
            )
            return prefix + id.value + if (params.isEmpty()) "" else QUERY + params.joinToString(PARAMS.toString())
        }

        /** The book route after [prefix]: its id, the tab it came from and whether via the player; null if no id. */
        private fun parseBook(rest: String, prefix: String): Triple<BookId, AppTab, Boolean>? {
            val id = rest.removePrefix(prefix).substringBefore(QUERY)
            if (id.isEmpty()) return null
            val params = rest.substringAfter(QUERY, "").split(PARAMS)
            val fromName = params.firstOrNull { it.startsWith(FROM) }?.removePrefix(FROM)
            val from = AppTab.entries.firstOrNull { it.name.lowercase() == fromName } ?: AppTab.LIBRARY
            return Triple(BookId(id), from, VIA_PLAYER in params)
        }

        /** Unknown or malformed routes (an older app version's saved state) fall back to the library. */
        fun fromRoute(route: String?): AppDestination = when {
            route == null -> Library

            route.startsWith(BOOK_PREFIX) ->
                parseBook(route, BOOK_PREFIX)?.let { (id, from, via) -> Reader(id, from, via) } ?: Library

            route.startsWith(PLAYER_PREFIX) ->
                parseBook(route, PLAYER_PREFIX)?.let { (id, from, _) -> Player(id, from) } ?: Library

            else -> fixedRoutes[route] ?: Library
        }

        private val fixedRoutes: Map<String, AppDestination> by lazy {
            mapOf(
                LIBRARY to Library,
                NOTES to Notes,
                SETTINGS to Settings,
                BATTERY_SETUP to BatterySetup(fromSettings = false),
                BATTERY_SETUP_FROM_SETTINGS to BatterySetup(fromSettings = true),
                VOICES to Voices(),
                VOICES_FROM_STORAGE to Voices(fromStorage = true),
                LICENCES to Licences,
                STORAGE to Storage,
            )
        }
    }
}

/** The bottom bar's tabs, in order (ADR-036). */
enum class AppTab(val destination: AppDestination) {
    LIBRARY(AppDestination.Library),
    NOTES(AppDestination.Notes),
    SETTINGS(AppDestination.Settings),
}
