// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/tour/BrokenWords.kt
// Purpose: Finds words a text layout broke across two lines, the sign of a squeezed row at large font sizes.

package io.github.skm0078.audora.ui.tour

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult

/**
 * Each line break of [node]'s text that falls inside a word, as the text around it ("comman|ds"). A word only breaks
 * when it is wider than its line, so in a row with room it means something beside the text took too much space.
 */
internal fun brokenWords(node: SemanticsNode): List<String> {
    val layouts = mutableListOf<TextLayoutResult>()
    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
    return layouts.flatMap(::brokenWords)
}

internal fun brokenWords(layout: TextLayoutResult): List<String> {
    val text = layout.layoutInput.text.text
    return (0 until layout.lineCount - 1).mapNotNull { line ->
        val end = layout.getLineEnd(line)
        val broken = end in 1 until text.length && text[end - 1].inWord() && text[end].inWord()
        if (broken) text.substring(layout.getLineStart(line), end) + "|" + text.substring(end).take(CONTEXT) else null
    }
}

/** Letters, digits and the vowel signs and marks of Hindi and Bengali, which are not letters to Java. */
private fun Char.inWord(): Boolean = isLetterOrDigit() || Character.getType(this).let {
    it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
}

private const val CONTEXT = 12
