// File: app/src/test/kotlin/io/github/skm0078/audora/ui/theme/RowLinesTest.kt
// Purpose: Every switch says what it changes on its own row (ADR 2026-10-29-say-what-it-does): each SwitchOption in
//          the app has a subtitle, and each row line is short, in English, Hindi and Bengali, and used.

package io.github.skm0078.audora.ui.theme

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class RowLinesTest {
    /** Unit tests run in the module's folder. */
    private val sources = File("src/main/kotlin")
    private val res = File("src/main/res")

    @Test
    fun everySwitchSaysWhatItDoes() {
        val calls = switchCalls()
        // Guards the test itself: a wrong working folder would find nothing and pass.
        assertTrue("Found only ${calls.size} switches", calls.size > MIN_EXPECTED_SWITCHES)
        val silent = calls.filterNot { (_, call) -> "subtitle =" in call }
            .map { (where, call) -> "$where: " + call.lineSequence().take(2).joinToString(" ").trim() }
        assertTrue(
            "Switches with no line under them (the effect hides behind ⓘ):\n" + silent.joinToString("\n"),
            silent.isEmpty(),
        )
    }

    @Test
    fun everyRowLineIsInEveryLanguage() {
        val english = rowLines("values")
        assertTrue("Found only ${english.size} row lines", english.size > MIN_EXPECTED_LINES)
        LOCALES.forEach { locale ->
            val translated = rowLines(locale)
            assertEquals("$locale row lines", english.keys, translated.keys)
            translated.forEach { (name, text) -> assertTrue("$locale/$name is blank", text.isNotBlank()) }
        }
    }

    @Test
    fun rowLinesFitOneLine() {
        (listOf("values") + LOCALES).forEach { locale ->
            val long = rowLines(locale).filterValues { it.length > MAX_LINE_CHARS }
            assertTrue("$locale lines over $MAX_LINE_CHARS characters: $long", long.isEmpty())
        }
    }

    @Test
    fun everyRowLineIsShown() {
        val code = sources.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        val unused = rowLines("values").keys.filterNot { "R.string.$it" in code }
        assertTrue("Row lines no row shows: $unused", unused.isEmpty())
    }

    /** (file, call text) for every SwitchOption( call, up to its closing bracket. */
    private fun switchCalls(): List<Pair<String, String>> = sources.walkTopDown()
        .filter { it.extension == "kt" }
        .flatMap { file ->
            val text = file.readText()
            SWITCH_CALL.findAll(text).map { file.name to callFrom(text, it.range.last + 1) }
        }
        .toList()

    private fun callFrom(text: String, open: Int): String {
        var depth = 1
        var end = open
        while (depth > 0 && end < text.length) {
            when (text[end]) {
                '(' -> depth++
                ')' -> depth--
            }
            end++
        }
        return text.substring(open, end)
    }

    private fun rowLines(folder: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(res, "$folder/strings_row_lines.xml")).documentElement
        val strings = root.getElementsByTagName("string")
        return (0 until strings.length).associate { i ->
            val element = strings.item(i) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private companion object {
        val SWITCH_CALL = Regex("""(?<!fun )(?<![\w.])SwitchOption\(""")
        val LOCALES = listOf("values-hi", "values-bn")
        const val MAX_LINE_CHARS = 52
        const val MIN_EXPECTED_SWITCHES = 20
        const val MIN_EXPECTED_LINES = 15
    }
}
