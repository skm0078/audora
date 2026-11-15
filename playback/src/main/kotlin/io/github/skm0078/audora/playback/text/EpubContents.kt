// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/EpubContents.kt
// Purpose: An EPUB's table of contents, nesting and `#part` links kept, turned into rows that point into its chapters.

package io.github.skm0078.audora.playback.text

/**
 * One link of an EPUB's table of contents: [path] is the ZIP path of the document it opens, [fragment] the `#id`
 * inside it (null for the document's start), [depth] 0 for a top-level row.
 */
internal data class TocEntry(val title: String, val path: String, val fragment: String?, val depth: Int) {
    /** Leaves the title out, so logging an entry never writes the book's text. */
    override fun toString() = "TocEntry(path=$path, fragment=$fragment, depth=$depth)"
}

/**
 * Reads both kinds of contents an EPUB can carry (the EPUB 3 navigation document, the EPUB 2 NCX) as a flat list in
 * contents order, nesting kept as depth, and places each row in the book ([place]).
 */
internal object EpubContents {
    private val WHITESPACE = Regex("\\s+")

    /** The `toc` nav's links; landmarks and page lists are ignored. */
    fun fromNav(markup: List<Markup>, base: String): List<TocEntry> = NavReader(base).also { r ->
        markup.forEach(r::accept)
    }.entries

    /** The NCX's navPoints; pageList and navList targets are ignored. */
    fun fromNcx(markup: List<Markup>, base: String): List<TocEntry> = NcxReader(base).also { r ->
        markup.forEach(r::accept)
    }.entries

    /**
     * The fuller of the two: some EPUB 3 books carry a stub navigation document (one "Start" link) next to a full NCX,
     * and some the other way round. The navigation document wins a tie, as EPUB 3 says it should.
     */
    fun fuller(nav: List<TocEntry>, ncx: List<TocEntry>): List<TocEntry> = if (ncx.size > nav.size) ncx else nav

    /**
     * Each [toc] row placed in the book. [spine] is the reading order of every content document (ZIP paths, kept or
     * not); [chapters] maps the spine position of each document that became a chapter to that chapter's index and
     * its [XhtmlDocument]. A row pointing at a document with no text (a title page that is one picture) moves on to
     * the next chapter; a row pointing past the end, or at a document not in the spine, is left out. An `#id` the
     * document does not have points at its start; one after its last text, at that text.
     */
    fun place(
        toc: List<TocEntry>,
        spine: List<String>,
        chapters: Map<Int, Pair<Int, XhtmlDocument>>,
    ): List<ContentsEntry> {
        val spineIndex = HashMap<String, Int>()
        spine.forEachIndexed { index, path -> spineIndex.putIfAbsent(path, index) }
        val kept = chapters.keys.sorted()
        return toc.mapNotNull { entry ->
            val position = spineIndex[entry.path] ?: return@mapNotNull null
            val spineAt = kept.firstOrNull { it >= position } ?: return@mapNotNull null
            val (chapter, document) = chapters.getValue(spineAt)
            val anchor = entry.fragment.takeIf { spineAt == position }?.let(document.anchors::get)
            val block = anchor?.block?.coerceAtMost(document.bodyBlocks - 1)?.coerceAtLeast(0) ?: 0
            val char = if (anchor != null && anchor.block == block) anchor.char else 0
            ContentsEntry(entry.title, chapter, block, char, entry.depth)
        }
    }

    private fun clean(text: CharSequence): String = text.toString().replace(WHITESPACE, " ").trim()

    /** "chapter.xhtml#part%202" → path and the decoded id; an empty fragment is no fragment. */
    private fun entry(base: String, href: String, title: CharSequence, depth: Int): TocEntry? {
        val text = clean(title)
        if (text.isEmpty() || href.isBlank()) return null
        val fragment = href.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let(EpubParser::percentDecoded)
        return TocEntry(text, EpubParser.resolve(base, href), fragment, depth)
    }

    /**
     * Links inside the `nav` whose epub:type is `toc`; each `ol` inside it is one level deeper. A row may be a plain
     * label (`<li><span>Part One</span><ol>…`): it points where its first link below points.
     */
    private class NavReader(private val base: String) {
        val entries = ArrayList<TocEntry>()
        private var navDepth = 0
        private var tocDepth = -1
        private var listDepth = 0
        private var href: String? = null
        private var inSpan = false
        private val label = StringBuilder()

        /** Plain labels waiting for a link below them, outermost first. */
        private val pending = ArrayList<Pair<String, Int>>()

        fun accept(token: Markup) {
            when (token) {
                is Markup.Start -> start(token)
                is Markup.Text -> if (href != null || inSpan) label.append(token.text)
                is Markup.End -> end(token)
            }
        }

        private fun inToc() = tocDepth > 0

        /** A `span` row label: in the contents, outside a link and outside another label. */
        private fun startsLabel(token: Markup.Start) = inToc() && href == null && !(inSpan || token.selfClosing)

        private fun depth() = (listDepth - 1).coerceAtLeast(0)

        private fun start(token: Markup.Start) {
            when (token.localName) {
                "nav" -> {
                    navDepth++
                    if (tocDepth < 0 && "toc" in token.attributes["epub:type"].orEmpty()) tocDepth = navDepth
                }

                "ol", "ul" -> if (inToc()) listDepth++

                "a" -> if (inToc()) {
                    href = token.attributes["href"].orEmpty()
                    inSpan = false
                    label.clear()
                }

                "span" -> if (startsLabel(token)) {
                    inSpan = true
                    label.clear()
                }
            }
        }

        private fun end(token: Markup.End) {
            val link = href
            when (token.localName) {
                "a" -> if (link != null) {
                    link(link)
                    href = null
                }

                "span" -> if (inSpan && href == null) {
                    inSpan = false
                    clean(label).takeIf { it.isNotEmpty() }?.let { pending += it to depth() }
                }

                "ol", "ul" -> if (inToc()) {
                    listDepth--
                    pending.removeAll { it.second >= listDepth }
                }

                "nav" -> {
                    if (navDepth == tocDepth) tocDepth = -1
                    navDepth--
                }
            }
        }

        private fun link(href: String) {
            val depth = depth()
            for ((title, at) in pending) {
                if (at < depth) entry(base, href, title, at)?.let(entries::add)
            }
            pending.clear()
            entry(base, href, label, depth)?.let(entries::add)
        }
    }

    /**
     * Each navPoint's label and target; navPoints nested in it are one level deeper. A navPoint is written once its
     * label and target are both read, before its children, whichever order the file puts them in.
     */
    private class NcxReader(private val base: String) {
        val entries = ArrayList<TocEntry>()
        private val open = ArrayList<Point>()
        private var inText = false

        private class Point(val depth: Int) {
            val label = StringBuilder()
            var src: String? = null
            var written = false
        }

        fun accept(token: Markup) {
            when (token) {
                is Markup.Start -> start(token)
                is Markup.Text -> if (inText) open.lastOrNull()?.label?.append(token.text)
                is Markup.End -> end(token)
            }
        }

        private fun start(token: Markup.Start) {
            when (token.localName) {
                "navpoint" -> {
                    open.lastOrNull()?.let(::write)
                    open += Point(open.size)
                }

                "text" -> inText = open.isNotEmpty()

                "content" -> open.lastOrNull()?.takeIf { it.src == null }?.src = token.attributes["src"]
            }
        }

        private fun end(token: Markup.End) {
            when (token.localName) {
                "text" -> inText = false
                "navpoint" -> open.removeLastOrNull()?.let(::write)
            }
        }

        private fun write(point: Point) {
            val src = point.src ?: return
            if (point.written) return
            point.written = true
            entry(base, src, point.label, point.depth)?.let(entries::add)
        }
    }

    /**
     * [placed] rows in reading order, plus a row for each chapter no row points into but which opens with a heading
     * (such a file was listed by its heading before the contents kept nesting); that row takes the depth of the row
     * before it, as it continues that part.
     */
    fun withHeadings(placed: List<ContentsEntry>, chapters: Collection<Pair<Int, XhtmlDocument>>): List<ContentsEntry> {
        val named = placed.mapTo(HashSet()) { it.chapter }
        val headed = chapters.filter { (chapter, _) -> chapter !in named }.mapNotNull { (chapter, document) ->
            XhtmlBlocks.firstHeading(document.blocks)?.let { ContentsEntry(it, chapter) }
        }
        var depth = 0
        return (placed + headed).sortedWith(BookContents.READING_ORDER).map { row ->
            if (row.chapter in named) row.also { depth = it.depth } else row.copy(depth = depth)
        }
    }
}
