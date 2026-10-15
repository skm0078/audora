// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/ui/VoicesTheme.kt
// Purpose: Material 3 dynamic colors, as in the main app (every supported phone is Android 12+).

package io.github.skm0078.audora.spike.voices.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun VoicesTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}
