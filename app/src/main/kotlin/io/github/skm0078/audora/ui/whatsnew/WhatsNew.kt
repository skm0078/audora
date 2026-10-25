// File: app/src/main/kotlin/io/github/skm0078/audora/ui/whatsnew/WhatsNew.kt
// Purpose: "What's new", once after an update that brings something a listener would notice, so people testing a
//          new build know what changed and what to try.

package io.github.skm0078.audora.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The list in `whats_new_items` (strings_whats_new.xml, with its Hindi and Bengali copies). Bump this whenever that
 * list is replaced, so everyone who has not seen the new one sees it once.
 */
const val WHATS_NEW_ID = 9

/** What to do at start: show the list, or mark it seen unshown (a new listener, who has nothing to compare). */
enum class WhatsNewStart { SHOW, MARK_SEEN, NOTHING }

/**
 * From the list seen last ([seen]) and the library's first settled state: only someone who already has books and
 * has not seen [current] is shown it; someone with an empty library is new, so it is marked seen for them.
 */
fun whatsNewStart(seen: Int, current: Int, library: LibraryListState): WhatsNewStart = when {
    seen >= current || library !is LibraryListState.Loaded -> WhatsNewStart.NOTHING
    library.all.isEmpty() -> WhatsNewStart.MARK_SEEN
    else -> WhatsNewStart.SHOW
}

/**
 * Decided once per start from [library] (read as state, so its first settled value is used), then shown until "Got
 * it". Never in kids mode. Placed after the first-run screens, so a new listener's first launch never shows it.
 */
@Composable
fun WhatsNewDialog(library: () -> LibraryListState) {
    if (LocalKidsMode.current) return
    val context = LocalContext.current
    val preferences = remember(context) { OnboardingPreferences(context.applicationContext) }
    var show by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val seen = withContext(Dispatchers.IO) { preferences.whatsNewSeen }
        val settled = snapshotFlow(library).first { it !is LibraryListState.Loading }
        when (whatsNewStart(seen, WHATS_NEW_ID, settled)) {
            WhatsNewStart.SHOW -> show = true
            WhatsNewStart.MARK_SEEN -> preferences.whatsNewSeen = WHATS_NEW_ID
            WhatsNewStart.NOTHING -> Unit
        }
    }
    if (!show) return
    val done = {
        show = false
        preferences.whatsNewSeen = WHATS_NEW_ID // an apply(): written in the background
    }
    AlertDialog(
        onDismissRequest = done,
        title = { Text(stringResource(R.string.whats_new_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                stringArrayResource(R.array.whats_new_items).forEach { item ->
                    Text(stringResource(R.string.whats_new_item, item))
                }
            }
        },
        confirmButton = { TextButton(onClick = done) { Text(stringResource(R.string.whats_new_ok)) } },
    )
}
