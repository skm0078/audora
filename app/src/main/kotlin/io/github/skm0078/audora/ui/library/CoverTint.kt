// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/CoverTint.kt
// Purpose: A soft tint taken from a book's cover for its Continue card (personal touch 3, ADR
//          2026-10-28-make-it-yours), kept readable in both themes.

package io.github.skm0078.audora.ui.library

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.skm0078.audora.core.library.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The card's [container] and the [accent] its label and bar use, both from one cover colour. */
@Immutable
data class CoverTint(val container: Color, val accent: Color)

/**
 * The tint for [book]'s cover over [surface] (the card's usual colour) with [onSurface] text, or null while the cover
 * loads, when it has none, or when the listener turned tints off ([enabled]). One 1×1 scale per cover, off the main
 * thread, again only when the cover changes.
 */
@Composable
fun rememberCoverTint(book: Book, covers: CoverSource, surface: Color, onSurface: Color, enabled: Boolean): CoverTint? {
    val colour by produceState<Color?>(null, book.id, covers.revision(book.id), enabled) {
        value = if (enabled) withContext(Dispatchers.Default) { covers.cover(book)?.let(::averageColour) } else null
    }
    return colour?.let { coverTint(it, surface, onSurface) }
}

/**
 * [cover] mixed lightly into [surface]; the accent starts from the cover and moves toward [onSurface] until it reads at
 * [MIN_CONTRAST] on the container, so a pale or a dark cover never leaves the label unreadable.
 */
internal fun coverTint(cover: Color, surface: Color, onSurface: Color): CoverTint {
    val container = lerp(surface, cover.copy(alpha = 1f), CONTAINER_MIX)
    var mix = 0f
    var accent = cover.copy(alpha = 1f)
    while (contrast(accent, container) < MIN_CONTRAST && mix < 1f) {
        mix = (mix + ACCENT_STEP).coerceAtMost(1f)
        accent = lerp(cover.copy(alpha = 1f), onSurface, mix)
    }
    return CoverTint(container, accent)
}

/** WCAG contrast ratio of two opaque colours. */
internal fun contrast(a: Color, b: Color): Float {
    val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (light + LUMINANCE_OFFSET) / (dark + LUMINANCE_OFFSET)
}

/** The cover's average colour: a 1×1 filtered scale. Null for a bitmap that cannot be read on the CPU. */
internal fun averageColour(image: ImageBitmap): Color? {
    val bitmap = image.asAndroidBitmap()
    val hardware = bitmap.config == Bitmap.Config.HARDWARE
    val readable: Bitmap = (if (hardware) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap) ?: return null
    return try {
        val one = Bitmap.createScaledBitmap(readable, 1, 1, true)
        Color(one.getPixel(0, 0)).also { if (one !== readable) one.recycle() }
    } catch (_: IllegalStateException) {
        null // A recycled bitmap: no tint, the card keeps its usual colour.
    } finally {
        if (readable !== bitmap) readable.recycle()
    }
}

/** Enough of the cover to warm the card without it looking like a different card in each book. */
private const val CONTAINER_MIX = 0.16f
private const val ACCENT_STEP = 0.1f

/** Text at 12 sp needs WCAG AA's 4.5:1. */
private const val MIN_CONTRAST = 4.5f
private const val LUMINANCE_OFFSET = 0.05f
