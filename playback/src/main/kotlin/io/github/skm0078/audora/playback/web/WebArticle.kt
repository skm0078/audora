// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/WebArticle.kt
// Purpose: Pulls the article out of a web page (Readability4J) as titled paragraphs, without menus, ads or footers.

package io.github.skm0078.audora.playback.web

import io.github.skm0078.audora.core.config.WebBooksConfig
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** An article's title (null when the page has none) and its text, one paragraph per blank-line-separated block. */
data class WebArticle(val title: String?, val text: String)

/**
 * Readability4J is a port of Firefox's Reader View (Apache-2.0): it scores the page's blocks and keeps the main one.
 * Its plain text runs paragraphs together, so the kept block is read again here, one paragraph per block element
 * (headings, paragraphs, list items, quotes), which the text-book reader turns into pauses and headings like any
 * shared text. Pure: no network, no Android.
 */
class WebArticleExtractor(private val config: WebBooksConfig = WebBooksConfig()) {
    /** The article in [html] fetched from [address], or null when it holds too little text to read. */
    fun extract(html: String, address: String): WebArticle? = extract(Jsoup.parse(html, address), address)

    /** As [extract], from a page jsoup has already parsed (with its charset worked out from the bytes). */
    fun extract(document: Document, address: String): WebArticle? {
        val pageTitle = document.title().trim()
        val article = Readability4J(address, document).parse()
        val text = article.articleContent?.let(::paragraphsOf)?.joinToString(PARAGRAPH_BREAK).orEmpty()
        val title = article.title?.trim()?.takeIf { it.isNotEmpty() } ?: pageTitle.takeIf { it.isNotEmpty() }
        return WebArticle(title, text).takeIf { text.length >= config.minArticleChars }
    }

    /**
     * The text of each outermost block element in [content]; a list item holding paragraphs is one block, not three.
     * Content with no block elements at all is one paragraph.
     */
    private fun paragraphsOf(content: Element): List<String> {
        val blocks = content.select(BLOCKS).filter { block -> block.parents().none { it.normalName() in BLOCK_TAGS } }
        val texts = if (blocks.isEmpty()) listOf(content.text()) else blocks.map(Element::text)
        return texts.map(::tidy).filter(String::isNotEmpty)
    }

    private fun tidy(text: String): String = text.replace(SPACES, " ").trim()

    private companion object {
        val BLOCK_TAGS = setOf(
            "h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "blockquote", "pre", "figcaption", "dt", "dd",
        )
        val BLOCKS = BLOCK_TAGS.joinToString(",")
        const val PARAGRAPH_BREAK = "\n\n"
        val SPACES = Regex("""\s+""")
    }
}
