// File: app/src/main/kotlin/io/github/skm0078/audora/privacy/LockSaver.kt
// Purpose: The app lock's screen saver (ADR 2026-10-28-lock-screen-saver): the time, the logo breathing, Pause while
//          it reads, and Unlock. Never a book title: the lock is there to hide the library.

package io.github.skm0078.audora.privacy

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.rememberDecorativeMotion
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlinx.coroutines.delay

/**
 * Three groups spread over the height (time at the top, logo in the middle, buttons at the bottom), so the screen is
 * filled on any phone, and it scrolls instead of clipping when the text is very large or the phone lies sideways.
 *
 * @param reading the book is playing: the logo says so and Pause shows. What is read never shows here.
 */
@Composable
internal fun LockSaver(reading: Boolean, onPause: () -> Unit, onUnlock: () -> Unit) {
    DarkSystemBars()
    val moving = rememberDecorativeMotion()
    BoxWithConstraints(Modifier.fillMaxSize().drawBehind { drawRect(nightGlow(size.width, size.height)) }) {
        val logo = min(min(maxWidth * LOGO_OF_WIDTH, maxHeight * LOGO_OF_HEIGHT), LOGO_MAX)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXl),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LockClock()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BreathingLogo(logo, moving)
                Spacer(Modifier.size(Dimens.SpaceS))
                ReadingState(reading, moving)
            }
            LockActions(reading, onPause, onUnlock)
        }
    }
}

private fun nightGlow(width: Float, height: Float): Brush = Brush.radialGradient(
    colors = listOf(LockSaverColors.Glow, LockSaverColors.Night),
    center = Offset(width / 2, height * GLOW_CENTRE),
    radius = maxOf(width, height) * GLOW_RADIUS,
)

/** The time as the phone writes it (12 or 24 hours), and the day, in the app's language. */
@Composable
private fun LockClock() {
    val now = rememberMinute()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(now) { DateFormat.getTimeFormat(context).format(Date.from(now.toInstant())) }
    val day = remember(now, locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_SKELETON), locale).format(now)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, style = MaterialTheme.typography.displayLarge, color = LockSaverColors.Ink)
        Text(day, style = MaterialTheme.typography.titleMedium, color = LockSaverColors.Ink.copy(alpha = SOFT))
    }
}

/** Now, to the minute: wakes at each minute's turn while the screen is in sight, and sleeps while it is not. */
@Composable
private fun rememberMinute(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = ZonedDateTime.now()
                delay(untilNextMinuteMs(now))
            }
        }
    }
    return now
}

/** Milliseconds from [now] to the next whole minute; at least 1, so the loop never spins. */
internal fun untilNextMinuteMs(now: ZonedDateTime): Long =
    Duration.between(now, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).toMillis().coerceAtLeast(1)

/** "Still reading to you" over moving bars while it plays; otherwise the locked title, which TalkBack reads first. */
@Composable
private fun ReadingState(reading: Boolean, moving: Boolean) {
    if (reading) {
        ReadingBars(moving)
        Spacer(Modifier.size(Dimens.SpaceS))
    }
    Text(
        stringResource(if (reading) R.string.lock_saver_reading else R.string.app_lock_locked_title),
        style = MaterialTheme.typography.titleMedium,
        color = LockSaverColors.Ink.copy(alpha = SOFT),
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun LockActions(reading: Boolean, onPause: () -> Unit, onUnlock: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (reading) {
                OutlinedIconButton(
                    onClick = onPause,
                    modifier = Modifier.size(ACTION_HEIGHT),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, LockSaverColors.Ink.copy(alpha = FAINT)),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = LockSaverColors.Ink),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_pause),
                        contentDescription = stringResource(R.string.lock_saver_pause),
                    )
                }
            }
            Button(
                onClick = onUnlock,
                modifier = Modifier.heightIn(min = ACTION_HEIGHT),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LockSaverColors.Ink,
                    contentColor = LockSaverColors.Night,
                ),
            ) {
                Icon(painterResource(R.drawable.ic_unlock_fingerprint), contentDescription = null)
                Spacer(Modifier.width(Dimens.SpaceS))
                Text(stringResource(R.string.app_lock_unlock), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.size(Dimens.SpaceL))
        Text(
            stringResource(R.string.lock_saver_hidden),
            style = MaterialTheme.typography.bodySmall,
            color = LockSaverColors.Ink.copy(alpha = SOFT),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Light status and navigation bar icons over the night colours, whatever the app's theme. */
@Composable
private fun DarkSystemBars() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}

/** "Tuesday, 27 October" in each language's own order. */
private const val DAY_SKELETON = "EEEEdMMMM"
private const val SOFT = 0.75f
private const val FAINT = 0.3f
private const val LOGO_OF_WIDTH = 0.55f
private const val LOGO_OF_HEIGHT = 0.28f
private const val GLOW_CENTRE = 0.42f
private const val GLOW_RADIUS = 0.6f
private val LOGO_MAX = 240.dp
private val ACTION_HEIGHT = 56.dp
