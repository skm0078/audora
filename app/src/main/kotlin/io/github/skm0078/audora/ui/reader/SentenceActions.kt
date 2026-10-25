// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SentenceActions.kt
// Purpose: TalkBack actions that reach each sentence of a paragraph or a drawn page, which TalkBack reads as one
//          block and cannot aim inside.

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.CustomAccessibilityAction
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit

/**
 * "Read from: …" (when [canRead]) and "Options for: …" for each of [sentences], named by their first words, for
 * TalkBack's actions menu. Empty for a single sentence: the block's own double tap and long press already reach it.
 */
@Composable
internal fun rememberSentenceActions(
    sentences: List<ReadingUnit>,
    canRead: Boolean,
    actions: ReaderActions,
): List<CustomAccessibilityAction> {
    val resources = LocalResources.current
    return remember(sentences, canRead, actions, resources) {
        if (sentences.size < 2) return@remember emptyList()
        sentences.flatMap { unit ->
            val preview = sentencePreview(unit.text)
            val options = CustomAccessibilityAction(resources.getString(R.string.a11y_sentence_options, preview)) {
                actions.onSentenceLongPressed(unit)
                true
            }
            val read = CustomAccessibilityAction(resources.getString(R.string.a11y_read_from_sentence, preview)) {
                actions.onSentenceTapped(unit)
                true
            }
            if (canRead) listOf(read, options) else listOf(options)
        }
    }
}

/** The first [words] words of [text], with an ellipsis when there are more: enough to tell sentences apart. */
internal fun sentencePreview(text: String, words: Int = PREVIEW_WORDS): String {
    val all = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    val shown = all.take(words).joinToString(" ")
    return if (all.size > words) "$shown…" else shown
}

private const val PREVIEW_WORDS = 6
private val WHITESPACE = Regex("\\s+")
