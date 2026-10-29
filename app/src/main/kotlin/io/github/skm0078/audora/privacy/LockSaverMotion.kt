// File: app/src/main/kotlin/io/github/skm0078/audora/privacy/LockSaverMotion.kt
// Purpose: The lock screen saver's light motion: the logo breathing inside two fading rings, and the reading bars.

package io.github.skm0078.audora.privacy

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R

/**
 * Every animated value is read inside `graphicsLayer`, so a frame only moves already-drawn layers: nothing is
 * composed, measured or drawn again. With [moving] false nothing animates at all and the logo sits still in one ring.
 *
 * The logo is the store master cut to 600 px tall (drawable-nodpi): sharp at the largest size the saver draws it on
 * a 3x screen, where the launcher's 108 dp copy would blur.
 */
@Composable
internal fun BreathingLogo(size: Dp, moving: Boolean) {
    Box(Modifier.size(size * HALO_ROOM).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (moving) {
            val transition = rememberInfiniteTransition(label = "lock saver logo")
            val halo = transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(HALO_MS, easing = LinearEasing)),
                label = "halo",
            )
            Halo(size) { halo.value }
            Halo(size) { (halo.value + HALF) % 1f }
            Logo(size, breath(transition))
        } else {
            Halo(size) { STILL_HALO }
            Logo(size, null)
        }
    }
}

/** A ring that grows from just inside the logo's edge and fades out as [progress] goes from 0 to 1. */
@Composable
private fun Halo(size: Dp, progress: () -> Float) {
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                val p = progress()
                val scale = HALO_FROM + (HALO_TO - HALO_FROM) * p
                scaleX = scale
                scaleY = scale
                alpha = HALO_ALPHA * (1f - p)
            }
            .border(HALO_STROKE, LockSaverColors.Ring, CircleShape),
    )
}

@Composable
private fun Logo(size: Dp, breath: State<Float>?) {
    Image(
        painterResource(R.drawable.lock_saver_logo),
        contentDescription = null,
        modifier = Modifier.size(size * LOGO_FILL).graphicsLayer {
            val scale = breath?.value ?: 1f
            scaleX = scale
            scaleY = scale
        },
    )
}

@Composable
private fun breath(transition: InfiniteTransition): State<Float> = transition.animateFloat(
    initialValue = 1f,
    targetValue = BREATH_SCALE,
    animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "breath",
)

/** Seven soft bars rising and falling one after another, like a voice; still at half height when [moving] is off. */
@Composable
internal fun ReadingBars(moving: Boolean) {
    Row(
        Modifier.height(BAR_HEIGHT).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val transition = if (moving) rememberInfiniteTransition(label = "lock saver bars") else null
        repeat(BAR_COUNT) { index ->
            val level = transition?.animateFloat(
                initialValue = BAR_LOW,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(BAR_MS, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                    StartOffset(index * BAR_STAGGER_MS),
                ),
                label = "bar $index",
            )
            Box(
                Modifier
                    .width(BAR_WIDTH)
                    .fillMaxHeight()
                    .graphicsLayer { scaleY = level?.value ?: STILL_BAR }
                    .background(LockSaverColors.Bars, RoundedCornerShape(BAR_WIDTH / 2)),
            )
        }
    }
}

/** Room around the logo for the rings to grow into. */
private const val HALO_ROOM = 1.15f

/** The logo's height inside the ring, so the rings start just outside the shield. */
private const val LOGO_FILL = 0.82f
private const val HALO_MS = 4_000
private const val HALO_FROM = 0.85f
private const val HALO_TO = 1.15f
private const val HALO_ALPHA = 0.55f
private const val HALF = 0.5f
private const val STILL_HALO = 0.5f
private val HALO_STROKE = 2.dp

/** A breath in and out every 4 s, at most 3.5 % bigger: seen, never jumpy. */
private const val BREATH_SCALE = 1.035f
private const val BREATH_MS = 2_000

private const val BAR_COUNT = 7
private const val BAR_MS = 800
private const val BAR_STAGGER_MS = 120
private const val BAR_LOW = 0.35f
private const val STILL_BAR = 0.6f
private val BAR_HEIGHT = 28.dp
private val BAR_WIDTH = 6.dp
private val BAR_GAP = 5.dp
