// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/SectionTitle.kt
// Purpose: The one look for a section's title inside a screen, sheet or group card, and a heading TalkBack can jump to.

package io.github.skm0078.audora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * A section title: small, in the accent colour, so it reads as a label over what follows rather than as content.
 * Callers add only their own spacing through [modifier].
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}
