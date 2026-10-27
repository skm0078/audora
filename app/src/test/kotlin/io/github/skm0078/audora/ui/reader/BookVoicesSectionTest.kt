// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/BookVoicesSectionTest.kt
// Purpose: The speech sheet lists the open book's language first, even with one voice (phone test: a Bengali book
//          showed only English's voices).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BookVoicesSectionTest {
    private val rows = listOf(
        row("bruno", Lang.EN, VoiceKind.BUILT_IN),
        row("loona", Lang.EN, VoiceKind.BUILT_IN),
        row("syspin-male", Lang.BN),
        row("supertonic", Lang.HI),
        row("chatterbox", Lang.HI),
    )

    @Test
    fun `a Bengali book with one Bengali voice lists Bengali first, then the languages with a choice`() {
        val shown = shownVoiceChoices(bookVoiceChoices(rows), Lang.BN)
        assertEquals(listOf(Lang.BN, Lang.EN, Lang.HI), shown.keys.toList())
        assertEquals(listOf("syspin-male"), shown.getValue(Lang.BN).map { it.label })
    }

    @Test
    fun `a Hindi book lists Hindi before English`() {
        assertEquals(listOf(Lang.HI, Lang.EN), shownVoiceChoices(bookVoiceChoices(rows), Lang.HI).keys.toList())
    }

    @Test
    fun `with no book language only languages with a choice are listed, in order`() {
        assertEquals(listOf(Lang.EN, Lang.HI), shownVoiceChoices(bookVoiceChoices(rows), null).keys.toList())
    }

    @Test
    fun `a book language with no voice downloaded adds no row`() {
        val noBengali = rows.filter { it.lang != Lang.BN }
        assertEquals(listOf(Lang.EN, Lang.HI), shownVoiceChoices(bookVoiceChoices(noBengali), Lang.BN).keys.toList())
    }

    private fun row(name: String, lang: Lang, kind: VoiceKind = VoiceKind.CATALOG) = VoiceRow(
        VoiceId(EngineId.SHERPA, name),
        name,
        lang,
        "MIT",
        0,
        kind,
        if (kind == VoiceKind.BUILT_IN) VoiceStatus.BuiltIn else VoiceStatus.Installed,
        readsLanguage = false,
    )
}
