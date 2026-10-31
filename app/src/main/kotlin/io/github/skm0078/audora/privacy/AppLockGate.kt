// File: app/src/main/kotlin/io/github/skm0078/audora/privacy/AppLockGate.kt
// Purpose: Covers every screen with the screen saver while the app lock (ADR-061) is closed, and keeps app content
//          out of screenshots and the recent-apps preview when the person asks for it.

package io.github.skm0078.audora.privacy

import android.app.Activity
import android.os.Build
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.ui.reader.rememberConnectedPlaybackPort

/**
 * [content] stays composed under the lock, so the open book, the scroll position and any shared file wait behind
 * it; it is only hidden from sight, touch and TalkBack. Playback in the notification and on the lock screen goes on:
 * the lock guards the library and its notes, not the play button.
 *
 * The lock is drawn in a window of its own, opened after the content's: a sheet, dialog or menu that was open when
 * the app locked has its own window above the activity's, and would otherwise stay readable and tappable on top of
 * a lock drawn in the activity (the notes sheet, a book's details).
 */
@Composable
fun AppLockGate(
    activity: Activity,
    settings: PrivacySettings,
    session: AppLockSession,
    content: @Composable () -> Unit,
) {
    WindowPrivacy(activity, settings)
    val locked = settings.appLock && !session.unlocked
    Box(Modifier.fillMaxSize()) {
        Box(if (locked) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) { content() }
        if (locked) LockWindow(activity) { LockedScreen(activity, session) }
    }
}

/**
 * A full-screen window above every window the content opened. Back leaves the app instead of reaching the screen
 * behind the lock; its security flag follows the activity's ("Hide app content").
 */
@Composable
private fun LockWindow(activity: Activity, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = { activity.moveTaskToBack(true) },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
        content = content,
    )
}

@Composable
private fun LockedScreen(activity: Activity, session: AppLockSession) {
    val unlock = DeviceUnlock(activity)
    var prompt by remember { mutableStateOf<CancellationSignal?>(null) }
    val ask = {
        if (unlock.hasScreenLock()) {
            prompt = unlock.ask { proved -> if (proved) session.unlock() }
        } else {
            // The phone's screen lock was removed: there is nothing left to prove, and asking would lock the person
            // out of their own library. Anyone able to remove it already knew it.
            AudoraLog.w(TAG, "App lock is on but the phone has no screen lock; opening")
            session.unlock()
        }
    }
    // Asks by itself once each time it locks (only while resumed, when Android shows the prompt), then waits.
    LifecycleResumeEffect(Unit) {
        if (session.takeAutoPrompt()) ask()
        onPauseOrDispose {}
    }
    // A prompt left open by a lock that went away some other way (the lock turned off) would cover the open app.
    DisposableEffect(Unit) { onDispose { prompt?.cancel() } }
    // Its own controller, so the lock never reaches into the screens it hides. Like the mini player's, it joins the
    // service only while it is up: nothing playing, nothing to show. Whether it reads, and Pause: never what it reads.
    val playback = rememberConnectedPlaybackPort()
    val snapshot by playback.state.collectAsStateWithLifecycle()
    LockSaver(reading = snapshot.wantsToPlay, onPause = playback::pause, onUnlock = ask)
}

/**
 * "Hide app content" sets FLAG_SECURE: no screenshots, no screen recording or casting of the app, a blank preview
 * in recent apps. The app lock alone only blanks the recents preview (Android 13+), so the person's own
 * screenshots still work.
 */
@Composable
private fun WindowPrivacy(activity: Activity, settings: PrivacySettings) {
    DisposableEffect(settings) {
        if (settings.hideContent) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!settings.appLock && !settings.hideContent)
        }
        onDispose {}
    }
}

private const val TAG = "Audora.AppLock"
