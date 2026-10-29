// File: app/src/main/kotlin/io/github/skm0078/audora/ui/stats/ListeningStatsRoute.kt
// Purpose: The library's "Listening stats" screen wired to the listening log, the library, the books' places, the
//          audiobooks made and the weekly goal; and its share card's Save picture and Share.

package io.github.skm0078.audora.ui.stats

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookOutputStore
import io.github.skm0078.audora.core.library.OutputKind
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.stats.ListeningLog
import io.github.skm0078.audora.core.stats.ListeningPeriod
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.export.ShareWords
import io.github.skm0078.audora.ui.library.CoverSource
import io.github.skm0078.audora.ui.loggingFailures
import io.github.skm0078.audora.ui.share.CardShape
import io.github.skm0078.audora.ui.share.ShareCardFiles
import io.github.skm0078.audora.ui.share.captureCard
import io.github.skm0078.audora.ui.share.rememberCardRecorder
import io.github.skm0078.audora.ui.share.sendCard
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * While [open], full screen: the stats are worked out off the main thread once the log, the library ([books], null
 * while it loads) and the goal are read, and again whenever one changes. Back on the card goes back to the stats.
 */
@Composable
fun ListeningStatsRoute(
    log: ListeningLog,
    books: List<Book>?,
    anchors: ResumeAnchorStore,
    open: Boolean,
    onClose: () -> Unit,
) {
    if (!open) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember(context) { LibraryEntryPoint.of(context) }
    val goalStore = remember(context) { ListeningGoal(context) }
    var goal by rememberSaveable { mutableIntStateOf(GOAL_UNREAD) }
    LaunchedEffect(goalStore) {
        if (goal == GOAL_UNREAD) {
            goal = withContext(Dispatchers.IO) { goalStore.weeklyHours }
        }
    }
    val all = rememberStats(log, books, anchors, library.outputs(), goal)
    var period by rememberSaveable { mutableStateOf(ListeningPeriod.WEEK) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val back: () -> Unit = {
        if (sharing) {
            sharing = false
        } else {
            onClose()
        }
    }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (sharing && all != null) {
                StatsShareRoute(all, period, library.covers()) { sharing = false }
            } else {
                val actions = object : StatsScreenActions {
                    override fun onClose() = back()

                    override fun onPeriod(chosen: ListeningPeriod) {
                        period = chosen
                    }

                    override fun onGoal(weeklyHours: Int) {
                        goal = weeklyHours
                        scope.launch(Dispatchers.IO) { goalStore.weeklyHours = weeklyHours }
                    }

                    override fun onShare() {
                        sharing = true
                    }

                    override fun onClear() {
                        scope.launch(Dispatchers.IO) { log.clear() }
                    }
                }
                ListeningStatsScreen(all, period, goal.coerceAtLeast(0), library.covers(), actions)
            }
        }
    }
}

/** Every period's stats, null until the log, the library, the places and the goal have all been read once. */
@Composable
private fun rememberStats(
    log: ListeningLog,
    books: List<Book>?,
    anchors: ResumeAnchorStore,
    outputs: BookOutputStore,
    goal: Int,
): Map<ListeningPeriod, StatsData>? {
    LaunchedEffect(log) { withContext(Dispatchers.IO) { log.load() } }
    val slots by log.slots.collectAsStateWithLifecycle()
    val places by remember(anchors) { anchors.observeAll().loggingFailures(TAG, "reading positions") }
        .collectAsStateWithLifecycle(initialValue = null)
    val madeAudiobook by remember(outputs) {
        outputs.observeAll().map { made -> made.any { it.kind == OutputKind.AUDIOBOOK } }
            .loggingFailures(TAG, "audiobooks made")
    }.collectAsStateWithLifecycle(initialValue = false)
    val locale = LocalConfiguration.current.locales[0]
    val inputs = places?.let { anchorList ->
        books?.takeIf { goal != GOAL_UNREAD }?.let { library ->
            StatsInputs(
                slots = slots,
                books = library,
                anchors = anchorList,
                madeAudiobook = madeAudiobook,
                weeklyGoalHours = goal,
                today = LocalDate.now(),
                zone = ZoneId.systemDefault(),
                firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek,
            )
        }
    }
    val stats by produceState<Map<ListeningPeriod, StatsData>?>(null, inputs) {
        inputs?.let { value = withContext(Dispatchers.Default) { StatsAllPeriods.of(it) } }
    }
    return stats
}

/** The share card: its choices saved across rotation, the picture captured as drawn for Save picture and Share. */
@Composable
private fun StatsShareRoute(
    all: Map<ListeningPeriod, StatsData>,
    period: ListeningPeriod,
    covers: CoverSource,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = rememberCardRecorder()
    val files = remember(context) { ShareCardFiles(context) }
    var choices by rememberSaveable(stateSaver = ChoicesSaver) {
        mutableStateOf(StatsCardChoices(kind = kindFor(period)))
    }
    var busy by remember { mutableStateOf(false) }
    val actions = object : StatsShareActions {
        override fun onBack() = onDone()

        override fun onChoices(next: StatsCardChoices) {
            choices = next
        }

        override fun onSave() {
            if (busy) return
            busy = true
            scope.captureCard(recorder, write = { card -> StatsPictures.save(context, card).takeIf { it } }) { saved ->
                busy = false
                toast(context, if (saved == true) R.string.stats_saved else R.string.stats_not_saved)
            }
        }

        override fun onShare() {
            if (busy) return
            busy = true
            scope.captureCard(recorder, write = { files.write(it, photo = false) }) { card ->
                busy = false
                when {
                    card == null -> toast(context, R.string.share_card_failed)

                    !context.sendCard(files.uri(card), card.mimeType, ShareWords.caption(context, source = null)) ->
                        toast(context, R.string.share_card_no_app)
                }
            }
        }
    }
    StatsShareScreen(all, choices, busy, recorder, covers, actions)
}

/** The card opens on the period the screen was showing; all time has no card of its own, so it opens on the year. */
private fun kindFor(period: ListeningPeriod) = when (period) {
    ListeningPeriod.WEEK -> StatsCardKind.WEEK
    ListeningPeriod.MONTH -> StatsCardKind.MONTH
    ListeningPeriod.YEAR, ListeningPeriod.ALL -> StatsCardKind.YEAR
}

private fun toast(context: Context, message: Int) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

private val ChoicesSaver = listSaver<StatsCardChoices, String>(
    save = { listOf(it.kind.name, it.look.name, it.shape.name) },
    restore = { saved ->
        StatsCardChoices(
            kind = StatsCardKind.entries.firstOrNull { it.name == saved[0] } ?: StatsCardKind.MONTH,
            look = StatsCardLook.entries.firstOrNull { it.name == saved[1] } ?: StatsCardLook.FOREST,
            shape = CardShape.entries.firstOrNull { it.name == saved[2] } ?: CardShape.STORY,
        )
    },
)

/** The goal before it is read from the phone: the stats wait for it, so the ring never jumps. */
private const val GOAL_UNREAD = -1
private const val TAG = "Audora.ListeningStats"
