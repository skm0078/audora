// File: core/src/main/kotlin/io/github/skm0078/audora/core/home/HomeLayout.kt
// Purpose: What the library's home shows and in what order, the name it greets the listener by and whether the
//          Continue card takes its tint from the cover: the "Make it yours" sheet's choices (ADR
//          2026-10-28-make-it-yours).

package io.github.skm0078.audora.core.home

/**
 * The parts of the library above the books. [order] holds the ones that scroll with the books; [SHELF_CHIPS] stays
 * pinned above them, so it can be turned off but not moved.
 */
enum class HomeSection {
    THIS_WEEK,
    CONTINUE,
    LINE_OF_THE_DAY,
    RECENTLY_ADDED,
    SHELF_CHIPS,
    ;

    val movable: Boolean get() = this != SHELF_CHIPS
}

/**
 * [order] lists every movable section once, top first; [hidden] the ones turned off. [name] is what the greeting adds
 * ("Good evening, Ria"), empty for none; it stays on the phone and is never logged.
 */
data class HomeLayout(
    val order: List<HomeSection> = DEFAULT_ORDER,
    val hidden: Set<HomeSection> = DEFAULT_HIDDEN,
    val name: String = "",
    val coverTint: Boolean = true,
) {
    init {
        require(order.toSet() == MOVABLE && order.size == MOVABLE.size) { "order must list each movable section once" }
        require(name.length <= NAME_MAX) { "name must be at most $NAME_MAX characters" }
    }

    fun shows(section: HomeSection): Boolean = section !in hidden

    /** The movable sections that show, top first. */
    val shownOrder: List<HomeSection> get() = order.filter(::shows)

    fun toggled(section: HomeSection): HomeLayout = copy(
        hidden = if (shows(section)) {
            hidden + section
        } else {
            hidden -
                section
        },
    )

    /** [section] one place up ([up]) or down; at either end, unchanged. */
    fun moved(section: HomeSection, up: Boolean): HomeLayout {
        val from = order.indexOf(section)
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in order.indices) return this
        return copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun named(typed: String): HomeLayout = copy(name = cleanName(typed))

    /** One line for the preferences: `order|hidden|tint|name`; the name goes last, so it may hold the separator. */
    fun encode(): String = listOf(
        order.joinToString(LIST_SEPARATOR) { it.name },
        hidden.joinToString(LIST_SEPARATOR) { it.name },
        if (coverTint) TINT_ON else TINT_OFF,
        name,
    ).joinToString(FIELD_SEPARATOR)

    companion object {
        /** Long enough for a full first name in any of the three scripts, short enough to fit beside the greeting. */
        const val NAME_MAX = 24

        private val MOVABLE: Set<HomeSection> = HomeSection.entries.filter { it.movable }.toSet()
        val DEFAULT_ORDER: List<HomeSection> =
            listOf(HomeSection.THIS_WEEK, HomeSection.CONTINUE, HomeSection.LINE_OF_THE_DAY, HomeSection.RECENTLY_ADDED)

        /** A light home by default (ADR 2026-10-28-home-and-tour): Recently added is the listener's to turn on. */
        val DEFAULT_HIDDEN: Set<HomeSection> = setOf(HomeSection.RECENTLY_ADDED)

        private const val FIELD_SEPARATOR = "|"
        private const val LIST_SEPARATOR = ","
        private const val TINT_ON = "tint"
        private const val TINT_OFF = "plain"
        private const val FIELDS = 4
        private const val ORDER_FIELD = 0
        private const val HIDDEN_FIELD = 1
        private const val TINT_FIELD = 2
        private const val NAME_FIELD = 3

        /** Trimmed, line breaks and controls dropped, cut to [NAME_MAX]. */
        fun cleanName(typed: String): String = typed.filterNot { it.isISOControl() }.trim().take(NAME_MAX).trimEnd()

        /**
         * Reads [encode]'s line. Null or a line from another build is the default; sections it does not know are
         * dropped and new ones are added at the end, so an update never loses the listener's order.
         */
        fun decode(line: String?): HomeLayout {
            val parts =
                line?.split(FIELD_SEPARATOR, limit = FIELDS)?.takeIf { it.size == FIELDS } ?: return HomeLayout()
            val known = sectionsIn(parts[ORDER_FIELD]).filter { it.movable }.distinct()
            val order = known + DEFAULT_ORDER.filterNot { it in known }
            val hidden = sectionsIn(parts[HIDDEN_FIELD]).toSet()
            return HomeLayout(order, hidden, cleanName(parts[NAME_FIELD]), coverTint = parts[TINT_FIELD] != TINT_OFF)
        }

        private fun sectionsIn(field: String): List<HomeSection> =
            field.split(LIST_SEPARATOR).mapNotNull { name -> HomeSection.entries.firstOrNull { it.name == name } }
    }
}
