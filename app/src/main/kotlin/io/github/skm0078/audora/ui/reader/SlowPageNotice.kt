// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SlowPageNotice.kt
// Purpose: The spinner for a page being read, with a line once it is slow that names OCR only while OCR runs.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.delay

/** [page] loading: a spinner, and [SlowPageLine] under it once the page has taken [noticeAfterMs]. */
@Composable
internal fun PageLoading(page: Int, recognizing: Boolean, noticeAfterMs: Long, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center).padding(Dimens.SpaceL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            CircularProgressIndicator()
            SlowPageLine(page, recognizing, noticeAfterMs)
        }
    }
}

/**
 * Nothing for [noticeAfterMs], then that the page is slow and turning pages still works, announced politely by
 * TalkBack. It says OCR (up to a minute) only while [recognizing]: a text page that waits for something else must
 * never claim to be a scan. Starts over for every page, so a quick page never shows it.
 */
@Composable
internal fun SlowPageLine(page: Int, recognizing: Boolean, noticeAfterMs: Long = DEFAULT_NOTICE_AFTER_MS) {
    var slow by remember(page) { mutableStateOf(false) }
    LaunchedEffect(page, noticeAfterMs) {
        delay(noticeAfterMs)
        slow = true
    }
    if (slow) {
        Text(
            stringResource(if (recognizing) R.string.reader_page_slow_ocr else R.string.reader_page_slow),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val DEFAULT_NOTICE_AFTER_MS = ReaderConfig().slowPageNoticeMs
