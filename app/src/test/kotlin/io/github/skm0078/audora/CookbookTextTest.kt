// File: app/src/test/kotlin/io/github/skm0078/audora/CookbookTextTest.kt
// Purpose: The ListenLock Cookbook opens with how to use it, then one chapter per recipe, read without markup
//          (ADR 2026-10-29-in-app-cookbook).

package io.github.skm0078.audora

import io.github.skm0078.audora.playback.text.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookbookTextTest {
    // The shape CookbookAssetsTask writes for each recipe.
    private val recipes = """
        |# Fall asleep to a book
        |
        |Let a novel play at bedtime.
        |
        |- You need: Nothing extra
        |- Set-up: 2 minutes, once
        |
        |1. **Set bedtime once.** In the reader, tap the timer icon.
        |
        |## Why it works
        |
        |The bedtime timer means you never have to remember one.
        |
        |
        |
        |# Revise a chapter
        |
        |Hear only the bones of a chapter.
        |
        |1. **Open the chapter.**
    """.trimMargin()

    @Test
    fun `the introduction is the first chapter and each recipe is its own chapter`() {
        val text = cookbookText("How to use this cookbook", "Each chapter is one recipe.", recipes)
        assertTrue(MarkdownParser.looksLikeMarkdown(text))
        val chapters = MarkdownParser.parse(text).chapters
        assertEquals(
            listOf("How to use this cookbook", "Fall asleep to a book", "Revise a chapter"),
            chapters.map { it.title },
        )
    }

    @Test
    fun `no run of blank lines and no markup symbols are left for the voice`() {
        val text = cookbookText("How to use this cookbook", "Each chapter is one recipe.", recipes)
        assertFalse(text.contains("\n\n\n"))
        val spoken = MarkdownParser.parse(text).chapters.joinToString(" ") { chapter ->
            chapter.blocks.joinToString(" ") { it.text }
        }
        assertFalse(spoken, spoken.contains("**"))
        assertTrue(spoken, spoken.contains("Set bedtime once."))
    }

    @Test
    fun `a missing recipes asset still gives a book with its introduction`() {
        val text = cookbookText("How to use this cookbook", "Each chapter is one recipe.", recipes = "")
        val chapters = MarkdownParser.parse(text).chapters
        assertEquals(listOf("How to use this cookbook"), chapters.map { it.title })
    }
}
