// File: app/src/main/kotlin/io/github/skm0078/audora/MainActivity.kt
// Purpose: Single activity hosting the Compose UI, edge-to-edge from day one (Build Spec §4).

package io.github.skm0078.audora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.AndroidEntryPoint
import io.github.skm0078.audora.ui.PlaceholderScreen
import io.github.skm0078.audora.ui.pdfcheck.PdfCheckController
import io.github.skm0078.audora.ui.pdfcheck.PdfCheckSection
import io.github.skm0078.audora.ui.theme.AudoraTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AudoraTheme {
                val context = LocalContext.current.applicationContext
                val scope = rememberCoroutineScope()
                val pdfCheck = remember { PdfCheckController(context, scope) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PlaceholderScreen(
                        versionName = BuildConfig.VERSION_NAME,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        PdfCheckSection(
                            state = pdfCheck.state,
                            onPdfPicked = pdfCheck::open,
                            onPasswordSubmitted = pdfCheck::submitPassword,
                            onPasswordCancelled = pdfCheck::cancelPassword,
                        )
                    }
                }
            }
        }
    }
}
