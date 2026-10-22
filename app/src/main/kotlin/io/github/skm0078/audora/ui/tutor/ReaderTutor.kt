// File: app/src/main/kotlin/io/github/skm0078/audora/ui/tutor/ReaderTutor.kt
// Purpose: Opens "Read aloud to me" from the reader on the shown page; "Next page" turns the reader's page (item 67).

package io.github.skm0078.audora.ui.tutor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.skm0078.audora.ui.reader.PageContent
import io.github.skm0078.audora.ui.reader.ReaderState

/** [pause] stops reading aloud; [goToPage] turns the reader's page, which the tutor then follows. */
@Composable
fun ReaderTutorRoute(state: ReaderState, pause: () -> Unit, goToPage: (Int) -> Unit, onClose: () -> Unit) {
    val content = state.content as? PageContent.Ready
    val page = remember(state.page, content) { content?.let { TutorPage.of(state.page, it.units) } }
    val next = state.page + 1
    val onNextPage = { goToPage(next) }.takeIf { state.pageCount?.let { next < it } == true }
    TutorRoute(page, loading = content == null, pause = pause, onNextPage = onNextPage, onClose = onClose)
}
