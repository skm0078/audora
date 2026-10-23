// File: app/src/main/kotlin/io/github/skm0078/audora/ui/motion/ScreenMotion.kt
// Purpose: Moving between screens: the predictive back preview Android 16 expects and the cover that grows from the
//          mini player into the full player (UI plan, Look and feel).

package io.github.skm0078.audora.ui.motion

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.AppDestination
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** The shared-element scope of the screen change in progress; null outside [AnimatedScreens] (previews, tests). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalScreenTransition = compositionLocalOf<SharedTransitionScope?> { null }

/** The enter or exit of the screen this composable belongs to; null outside [AnimatedScreens]. */
val LocalScreenVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Draws [content] for [destination] and animates every change of screen. A back swipe scrubs the change to the
 * parent screen as the finger moves, so the listener sees where back leads before letting go (predictive back,
 * on by default at API 36); letting go finishes it through [onBack], cancelling springs back. Screens that share a
 * [AppDestination.screen] (the tabs; one book's reader and player) are one content, so switching between them keeps
 * their state, including the book's one ReaderModel (ADR-036 §6).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AnimatedScreens(
    destination: AppDestination,
    onBack: (AppDestination) -> Unit,
    content: @Composable (AppDestination) -> Unit,
) {
    val state = remember { SeekableTransitionState(destination) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(destination) { state.animateTo(destination) }
    val parent = destination.parent
    PredictiveBackHandler(enabled = parent != null) { events ->
        val target = parent ?: return@PredictiveBackHandler
        // Within one content (a tab to the library, the reader back to its player) there is nothing to preview,
        // and scrubbing would swap the screen under the finger at once.
        val preview = target.screen != destination.screen
        try {
            events.collect { event -> if (preview) state.seekTo(event.progress, target) }
            onBack(target)
        } catch (cancelled: CancellationException) {
            // The swipe was abandoned: the screen springs back to where it was, then the cancellation goes on.
            scope.launch { state.animateTo(state.currentState, spring()) }
            throw cancelled
        }
    }
    val transition = rememberTransition(state, label = "screens")
    SharedTransitionLayout {
        transition.AnimatedContent(
            transitionSpec = { screenChange() },
            contentKey = { it.screen },
        ) { target ->
            CompositionLocalProvider(
                LocalScreenTransition provides this@SharedTransitionLayout,
                LocalScreenVisibility provides this,
            ) { content(target) }
        }
    }
}

/**
 * Deeper screens grow in over the one below; going back, the top screen shrinks away over the one below, which
 * fades in beneath it, as the back preview shows it while the finger is down. Back is linear in time, because the
 * system already eases the swipe's progress that drives it. Kept short so a listener is never waiting on it.
 */
private fun AnimatedContentTransitionScope<AppDestination>.screenChange(): ContentTransform =
    if (targetState == initialState.parent) {
        val spec = tween<Float>(ScreenMotion.DURATION_MS, easing = LinearEasing)
        ContentTransform(
            targetContentEnter = fadeIn(spec),
            initialContentExit = scaleOut(spec, ScreenMotion.BACK_SCALE) + fadeOut(spec),
            // The leaving screen stays on top, so its shrink is what the eye follows, not a cross-fade.
            targetContentZIndex = targetState.depth.toFloat(),
        )
    } else {
        val spec = tween<Float>(ScreenMotion.DURATION_MS)
        ContentTransform(
            targetContentEnter = scaleIn(spec, ScreenMotion.ENTER_SCALE) + fadeIn(spec),
            initialContentExit = fadeOut(spec),
            targetContentZIndex = targetState.depth.toFloat(),
        )
    }

/**
 * Steps from the library to this screen. AnimatedContent keeps the z-index a screen arrived with, so every change sets
 * it from depth: a deeper screen always draws over a shallower one, however it was reached.
 */
private val AppDestination.depth: Int get() = generateSequence(parent) { it.parent }.count()

/**
 * True while the screen this is called from animates out (or a back swipe previews leaving it). Effects that act on
 * the whole window, such as keeping the screen on or taking the volume keys, stop then, so the screen arriving in
 * the same moment owns them.
 */
@Composable
fun isLeavingScreen(): Boolean = LocalScreenVisibility.current?.transition?.targetState == EnterExitState.PostExit

/**
 * The book's cover as one shared element across screens, so it grows from the mini player into the full player
 * and shrinks back. Each screen tags at most one cover per book: the library's grid, where a book can appear twice,
 * takes no part.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(id: BookId): Modifier {
    val shared = LocalScreenTransition.current
    val visibility = LocalScreenVisibility.current
    if (shared == null || visibility == null) return this
    return with(shared) { this@sharedCover.sharedElement(rememberSharedContentState(COVER_KEY + id.value), visibility) }
}

private const val COVER_KEY = "cover/"

/** Screen-change tunables. */
object ScreenMotion {
    const val DURATION_MS = 300

    /** How small the top screen gets as it leaves on back (Material's predictive back shrinks to about 90%). */
    const val BACK_SCALE = 0.9f

    /** Where a deeper screen starts as it grows in. */
    const val ENTER_SCALE = 0.94f
}
