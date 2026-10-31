// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/FrontMatterNotice.kt
// Purpose: The card that says a new book opened past its front matter, what was skipped, and goes back to page 1 in
//          one tap (ADR 2026-10-31-skip-front-matter).

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.pdf.start.FrontMatterKind
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * "Started at “Chapter 1”, page 15 · Skipped 14 pages: contents, copyright, preface." Two answers: Read from the
 * start (page 1, and this book keeps opening there) and OK. The skipped pages are never hidden: they stay in the
 * book, the page bar and the chapter list. TalkBack reads the card when it appears.
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
internal fun FrontMatterNotice(skipped: SkippedFrontMatter, onReadFromStart: () -> Unit, onOk: () -> Unit) {
    val page = skipped.page + 1
    val title = skipped.title?.let { stringResource(R.string.front_matter_started_at, it, page) }
        ?: stringResource(R.string.front_matter_started_on, page)
    val pages = pluralStringResource(R.plurals.front_matter_skipped, skipped.page, skipped.page)
    val kinds = skipped.skipped.map { stringResource(kindLabel(it)) }
    val joiner = stringResource(R.string.front_matter_list_joiner)
    val what = stringResource(R.string.front_matter_skipped_what, pages, kinds.joinToString(joiner))
    val body = if (kinds.isEmpty()) pages else what
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(Dimens.SpaceL), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                Button(onClick = onReadFromStart) { Text(stringResource(R.string.front_matter_read_from_start)) }
                TextButton(onClick = onOk) { Text(stringResource(R.string.front_matter_ok)) }
            }
        }
    }
}

/** The cards the open book shows above its page, each only when [ReaderNotice] picked it. */
internal class ReaderCards(val voicePack: VoicePackOffer?, state: ReaderState, notice: ReaderNotice?) {
    val frontMatter: SkippedFrontMatter? = state.skippedFrontMatter.takeIf { notice == ReaderNotice.FRONT_MATTER }
}

@StringRes
internal fun kindLabel(kind: FrontMatterKind): Int = when (kind) {
    FrontMatterKind.TITLE_PAGES -> R.string.front_matter_kind_title_pages
    FrontMatterKind.COPYRIGHT -> R.string.front_matter_kind_copyright
    FrontMatterKind.CONTENTS -> R.string.front_matter_kind_contents
    FrontMatterKind.DEDICATION -> R.string.front_matter_kind_dedication
    FrontMatterKind.THANKS -> R.string.front_matter_kind_thanks
    FrontMatterKind.PREFACE -> R.string.front_matter_kind_preface
}
