// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/djvu/DjvuFixtures.kt
// Purpose: The test DjVu's text layer, outline and metadata as djvused printed them (src/test/resources/djvu, written
//          by scripts/fixtures/make_djvu_fixtures.py), served as the native file would.

package io.github.skm0078.audora.playback.djvu

object DjvuFixtures {
    fun text(name: String): String =
        requireNotNull(DjvuFixtures::class.java.getResourceAsStream("/djvu/$name")) { "no DjVu fixture $name" }
            .use { String(it.readBytes(), Charsets.UTF_8) }

    /** text.djvu's three pages: two English with an outline entry each, one Hindi under the second. */
    fun textBook(): DjvuPages = FakePages(
        pages = (1..TEXT_PAGES).map { text("text-page$it.sexp") },
        outline = text("text-outline.sexp"),
        annotations = text("text-annotations.sexp"),
    )

    class FakePages(
        private val pages: List<String>,
        private val outline: String = "",
        private val annotations: String = "",
    ) : DjvuPages {
        override val pageCount: Int get() = pages.size

        override fun pageText(page: Int): String = pages[page]

        override fun outline(): String = outline

        override fun annotations(): String = annotations
    }

    private const val TEXT_PAGES = 3
}
