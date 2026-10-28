// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/web/CuratedCatalogue.kt
// Purpose: The free book catalogues ListenLock lists by itself, checked every week on CI (ADR 2026-10-28-curated-
//          catalogues): free to read, no account, public-domain or openly licensed books only.

package io.github.skm0078.audora.playback.web

/**
 * Each one is an OPDS 1 (Atom) catalogue that CI opens every week (`.github/workflows/catalogue-check.yml`): its
 * first page, a book it lists that ListenLock can read, that book's download, and its search when it has one. A
 * catalogue that stops passing is fixed or taken out here. [title] is the source's own name (never translated); the
 * app shows a description of its own for each [name]. [languages] are the main ones its books are in (BCP 47
 * primary tags), most books first.
 */
enum class CuratedCatalogue(val title: String, val address: String, val languages: List<String>) {
    GUTENBERG("Project Gutenberg", "https://www.gutenberg.org/ebooks.opds/", listOf("en", "fr", "de", "es", "pt")),
    WIKISOURCE_EN("English Wikisource", "https://ws-export.wmcloud.org/opds/en/Ready_for_export.xml", listOf("en")),
    FEEDBOOKS("Feedbooks public domain", "https://catalog.feedbooks.com/publicdomain/browse/top.atom", listOf("en")),
    UNGLUE_IT("Unglue.it", "https://unglue.it/api/opds/", listOf("en")),
    ;

    val catalogue: OpdsCatalogue get() = OpdsCatalogue(title, address, curated = this)
}
