// File: app/src/main/kotlin/io/github/skm0078/audora/ui/whatsnew/WhatsNew.kt
// Purpose: "What's new", once after an update that brings something a listener would notice, so people testing a
//          new build know what changed and what to try. Its items are files in assets/whatsnew/ (WhatsNewEntries).

package io.github.skm0078.audora.ui.whatsnew

import android.content.res.AssetManager
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
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.battery.OnboardingPreferences
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The names of every shipped entry; lists the APK's asset table only, so it is cheap enough for the main thread. */
fun whatsNewEntryNames(assets: AssetManager): Set<String> = assets.list(WhatsNewEntries.DIRECTORY).orEmpty()
    .filter(WhatsNewEntries::isEntryFile)
    .mapTo(HashSet()) { it.removeSuffix(WhatsNewEntries.EXTENSION) }

/** Every shipped entry; one that does not parse is logged and left out (the unit test keeps that from shipping). */
fun loadWhatsNew(assets: AssetManager): List<WhatsNewEntry> =
    whatsNewEntryNames(assets).mapNotNull { name -> readEntry(assets, name) }

private fun readEntry(assets: AssetManager, name: String): WhatsNewEntry? {
    val path = "${WhatsNewEntries.DIRECTORY}/$name${WhatsNewEntries.EXTENSION}"
    return runCatching { WhatsNewEntries.parse(name, assets.open(path).bufferedReader().use { it.readText() }) }
        .onFailure { AudoraLog.w(TAG, "What's new entry $name skipped", it) }
        .getOrNull()
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
    val language = stringResource(R.string.whats_new_language)
    // The items shown and every entry's name (marked seen on "Got it"); saved, so a rotation keeps the dialog.
    var items by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    var names by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    LaunchedEffect(Unit) {
        if (items != null) return@LaunchedEffect
        val (entries, seen) = withContext(Dispatchers.IO) {
            loadWhatsNew(context.assets) to
                WhatsNewEntries.seenEntries(preferences.whatsNewSeenEntries, preferences.whatsNewSeenList)
        }
        val settled = snapshotFlow(library).first { it !is LibraryListState.Loading }
        val fresh = WhatsNewEntries.unseen(entries, seen)
        val all = entries.mapTo(HashSet()) { it.name }
        when (whatsNewStart(fresh.isNotEmpty(), settled)) {
            WhatsNewStart.SHOW -> {
                names = ArrayList(all)
                items = fresh.mapTo(ArrayList()) { it.textIn(language) }
            }

            WhatsNewStart.MARK_SEEN -> preferences.markWhatsNewSeen(all)

            WhatsNewStart.NOTHING -> Unit
        }
    }
    val shown = items
    if (shown.isNullOrEmpty()) return
    val done = {
        items = ArrayList()
        preferences.markWhatsNewSeen(names.toSet()) // an apply(): written in the background
    }
    AlertDialog(
        onDismissRequest = done,
        title = { Text(stringResource(R.string.whats_new_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                shown.forEach { item -> Text(stringResource(R.string.whats_new_item, item)) }
            }
        },
        confirmButton = { TextButton(onClick = done) { Text(stringResource(R.string.whats_new_ok)) } },
    )
}

private const val TAG = "Audora.WhatsNew"
