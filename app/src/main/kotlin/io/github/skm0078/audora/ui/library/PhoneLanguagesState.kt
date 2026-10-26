// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/PhoneLanguagesState.kt
// Purpose: The languages the phone's own voices read offline, for Edit details (ADR 2026-10-26), and the way to the
//          phone's text-to-speech settings to add more.

package io.github.skm0078.audora.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechEngines

private const val TAG = "UI.PhoneLanguages"

/** Android's text-to-speech settings, where voice packs are downloaded (not a public constant). */
private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

/**
 * The phone's offline voice languages ("fr", "ta"), asked again each time the screen comes back (from the phone's
 * text-to-speech settings, say); null while asking, empty when the engine has none or could not start (logged).
 */
@Composable
internal fun rememberPhoneLanguages(): List<String>? {
    val context = LocalContext.current.applicationContext
    var languages by remember { mutableStateOf<List<String>?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(resumes) {
        // The resume effect counts the first resume too, right away: ask once that has happened.
        if (resumes == 0) return@LaunchedEffect
        languages = when (val found = SpeechEngines.phoneLanguages(context)) {
            is Outcome.Success -> found.value

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Phone voice languages unknown: ${found.error.message}")
                emptyList()
            }
        }
    }
    return languages
}

/** Opens the phone's text-to-speech settings; says so when the phone has no such screen. */
internal fun openPhoneVoiceSettings(context: Context) {
    try {
        context.startActivity(Intent(TTS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (missing: ActivityNotFoundException) {
        AudoraLog.w(TAG, "No text-to-speech settings screen", missing)
        Toast.makeText(context, R.string.phone_languages_no_settings, Toast.LENGTH_SHORT).show()
    }
}
