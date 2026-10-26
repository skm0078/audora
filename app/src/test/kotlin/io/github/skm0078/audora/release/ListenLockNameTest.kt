// File: app/src/test/kotlin/io/github/skm0078/audora/release/ListenLockNameTest.kt
// Purpose: Nothing a person reads says "Audora" (ADR-060): every string resource the app and the playback service
//          show, in English, Hindi and Bengali. Code, packages, log tags and file names keep the old name on purpose.
//          Nor does any shown text or What's new entry name a phone maker or model: testers read that as personal.

package io.github.skm0078.audora.release

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ListenLockNameTest {
    /** Unit tests run in the module's folder; the playback service's notification strings live next door. */
    private val resourceRoots = listOf(File("src/main/res"), File("../playback/src/main/res"))

    @Test
    fun noShownStringSaysAudora() {
        val offenders = shownStrings().filter { (_, text) -> OLD_NAME.containsMatchIn(text) }
        assertTrue(
            "Strings still saying Audora:\n" + offenders.joinToString("\n") { (where, text) -> "$where: $text" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun noShownTextNamesAPhone() {
        val whatsNew = File("src/main/assets/whatsnew").listFiles().orEmpty()
            .map { "whatsnew/${it.name}" to it.readText() }
        val offenders = (shownStrings() + whatsNew).filter { (_, text) -> PHONE_NAMES.containsMatchIn(text) }
        assertTrue(
            "Shown text naming a phone:\n" + offenders.joinToString("\n") { (where, text) -> "$where: $text" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun theStringsWereFound() {
        // Guards the test itself: a wrong working folder would find nothing and pass.
        assertTrue("Found only ${shownStrings().size} strings", shownStrings().size > MIN_EXPECTED_STRINGS)
    }

    @Test
    fun theAppIsCalledListenLock() {
        val names = shownStrings().filter { (where, _) -> where.endsWith("#app_name") }
        assertTrue("No app_name found", names.isNotEmpty())
        names.forEach { (where, text) -> assertTrue("$where is \"$text\"", text.startsWith("ListenLock")) }
    }

    /** (folder/file#name, text) for every <string>, <plurals> item and <string-array> item, comments left out. */
    private fun shownStrings(): List<Pair<String, String>> = resourceRoots
        .flatMap { root -> root.listFiles { dir -> dir.name.startsWith("values") }.orEmpty().toList() }
        .flatMap { dir -> dir.listFiles { file -> file.extension == "xml" }.orEmpty().toList() }
        .flatMap(::stringsIn)

    private fun stringsIn(file: File): List<Pair<String, String>> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val where = "${file.parentFile.name}/${file.name}"
        return STRING_TAGS.flatMap { tag ->
            val nodes = root.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it) as Element }
                .filter { tag != "item" || (it.parentNode as? Element)?.tagName in LIST_TAGS }
                .map { element ->
                    val name = element.getAttribute("name").ifEmpty {
                        (element.parentNode as Element).getAttribute("name")
                    }
                    "$where#$name" to element.textContent
                }
        }
    }

    private companion object {
        val OLD_NAME = Regex("audora", RegexOption.IGNORE_CASE)

        /** Makers and models of phones; the battery steps keep the maker in code (BatteryGuide), never on screen. */
        val PHONE_NAMES = Regex(
            "\\b(moto|motorola|oneplus|samsung|galaxy|xiaomi|redmi|poco|oppo|realme|vivo|iqoo|pixel|edge 50)\\b",
            RegexOption.IGNORE_CASE,
        )
        val STRING_TAGS = listOf("string", "item")

        /** A theme's <item> is a style value, not text; only these parents hold shown items. */
        val LIST_TAGS = setOf("plurals", "string-array")
        const val MIN_EXPECTED_STRINGS = 500
    }
}
