// File: app/src/main/kotlin/io/github/skm0078/audora/ui/PlaceholderScreen.kt
// Purpose: Temporary home screen: build info plus the PDF extraction check; replaced by the library in P1-01.

package io.github.skm0078.audora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.AudoraTheme

@Composable
fun PlaceholderScreen(versionName: String, modifier: Modifier = Modifier, pdfCheck: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.placeholder_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.placeholder_body), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.placeholder_version, versionName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        pdfCheck()
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    AudoraTheme { PlaceholderScreen(versionName = "0.0.1-debug") }
}
