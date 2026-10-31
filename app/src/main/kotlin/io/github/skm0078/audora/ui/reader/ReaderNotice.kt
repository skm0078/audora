// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderNotice.kt
// Purpose: Picks the one notice the reader shows, so stacked cards never push the book off a small screen.

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.battery.OnboardingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The reader's cards, most urgent first. Only the first that applies shows: at large text sizes two cards left no
 * book text on screen (UI audit, 2026-10-25). The others wait their turn and appear once it is answered.
 */
internal enum class ReaderNotice {
    /** The page cannot be read yet: its text recognition files are missing, or recognition failed. */
    PAGE_PROBLEM,

    /** The page is in Hindi or Bengali and that language's natural voice is not on the phone. */
    VOICE_PACK,

    /** The one-time card naming the tap and long-press gestures. */
    GESTURE_TIPS,

    /** A first-use hint (ADR 2026-10-30-first-use-hints): last, since it is only ever a suggestion. */
    HINT,
}

/** The notice to show for a page, or null for none. Pure, so its order is unit-tested. */
internal fun readerNotice(
    origin: PageOrigin?,
    voicePackOffered: Boolean,
    tipsPending: Boolean,
    hintPending: Boolean = false,
): ReaderNotice? = when {
    origin == PageOrigin.NEEDS_OCR_MODELS || origin == PageOrigin.OCR_FAILED -> ReaderNotice.PAGE_PROBLEM
    voicePackOffered -> ReaderNotice.VOICE_PACK
    tipsPending -> ReaderNotice.GESTURE_TIPS
    hintPending -> ReaderNotice.HINT
    else -> null
}

/** [readerNotice] for the reader's current page. */
internal fun readerNotice(
    state: ReaderState,
    offer: VoicePackOffer,
    tipsPending: Boolean,
    hintPending: Boolean = false,
): ReaderNotice? {
    val page = state.content as? PageContent.Ready
    return readerNotice(page?.origin, page?.let { offer.packFor(it.units) } != null, tipsPending, hintPending)
}

/**
 * Whether the tips still wait to be seen, once for the whole app. Read off the main thread and not [pending] until
 * read, so the card never flashes for someone who has already tapped "Got it".
 */
@Stable
internal class GestureTipsState(private val prefs: OnboardingPreferences, private val scope: CoroutineScope) {
    var pending by mutableStateOf(false)
        private set

    suspend fun load() {
        pending = withContext(Dispatchers.IO) { !prefs.gestureTipsSeen }
    }

    /** The reader's ⋮ "Gestures and tips": the card shows again, until Got it. */
    fun show() {
        pending = true
    }

    fun dismiss() {
        pending = false
        scope.launch(Dispatchers.IO) { prefs.gestureTipsSeen = true }
    }
}

@Composable
internal fun rememberGestureTips(): GestureTipsState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val tips = remember { GestureTipsState(OnboardingPreferences(context), scope) }
    LaunchedEffect(tips) { tips.load() }
    return tips
}
