// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/kws/CommandListener.kt
// Purpose: Listens for voice commands with the phone's own on-device speech recogniser while the reader is open
//          (plan item 38, ADR 2026-11-02-voice-commands-system-recogniser).

package io.github.skm0078.audora.speech.kws

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.github.skm0078.audora.core.config.VoiceCommandConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Uses Android's on-device recogniser ([SpeechRecognizer.createOnDeviceSpeechRecognizer], Android 12+): the phone's
 * own speech engine (Google's on most phones), so nothing is downloaded by the app, nothing leaves the phone, and it
 * understands accents a small bundled model does not. A phone without one (no Google speech services) reports
 * [ListenFailure.NOT_AVAILABLE] and Settings hides the switch.
 *
 * The recogniser listens in short sessions; [RecognitionLoop] starts the next one as each ends. With the screen off
 * (an opt-in) the reader keeps a `microphone` foreground service running meanwhile, started while it is visible, as
 * Android 14 requires (ADR-051). Every call to the recogniser is on the main thread, as Android requires.
 */
class CommandListener(
    context: Context,
    private val config: VoiceCommandConfig = VoiceCommandConfig(),
    private val echo: NarratorEcho = NarratorEcho.shared,
    private val pause: VoiceCommandPause = VoiceCommandPause.shared,
) {
    private val appContext = context.applicationContext

    /** Blocks until the calling coroutine is cancelled, or returns why listening cannot go on. */
    suspend fun listen(events: ListenEvents): Outcome<Unit, ListenFailure> = withContext(Dispatchers.Main) {
        when {
            !microphoneGranted(appContext) -> Outcome.Failure(ListenFailure.NO_PERMISSION)

            !isAvailable(appContext) -> Outcome.Failure(ListenFailure.NOT_AVAILABLE)

            else -> {
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                try {
                    val sessions = SystemRecognizer(appContext, recognizer, config.languages)
                    RecognitionLoop(sessions, config, echo, pause, SystemClock::elapsedRealtime).run(events)
                } finally {
                    recognizer.destroy()
                    AudoraLog.i(TAG, "Stopped listening for voice commands")
                }
            }
        }
    }

    companion object {
        private const val TAG = "Speech.Commands"

        /** Where the voice-command model downloaded by builds before 2026-11-02 was kept, and its download's name. */
        private const val OLD_MODEL_DIR = "voice-commands"

        /** True when this phone has an on-device recogniser voice commands can use. */
        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        private fun microphoneGranted(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /**
         * Frees the space of the 70 MB model older builds downloaded for voice commands; nothing uses it now. Safe to
         * call at every start: it is a no-op once the folder is gone. Off the main thread.
         */
        fun forgetOldModel(context: Context) {
            val dir = File(context.noBackupFilesDir, OLD_MODEL_DIR)
            if (dir.exists()) {
                val deleted = dir.deleteRecursively()
                AudoraLog.i(TAG, "Old voice-command model removed: $deleted")
            }
        }
    }
}

/**
 * One [SpeechRecognizer] session per call. Asks for partial results, so "ListenLock, pause" acts while the listener
 * is still speaking, and for on-device recognition only. The first language in [languages] the recogniser accepts is
 * kept for the rest of the listening.
 */
private class SystemRecognizer(
    private val context: Context,
    private val recognizer: SpeechRecognizer,
    private val languages: List<String>,
) : CommandRecognizer {
    private var languageIndex = 0
    private var askedForPack = false

    override suspend fun session(onReady: () -> Unit, onText: (String) -> Unit): SessionEnd {
        val end = once(languages[languageIndex], onReady, onText)
        return if (end == SessionEnd.LANGUAGE_MISSING && languageIndex < languages.lastIndex) {
            AudoraLog.i(TAG, "No ${languages[languageIndex]} speech pack; trying ${languages[languageIndex + 1]}")
            languageIndex++
            SessionEnd.NOTHING
        } else {
            if (end == SessionEnd.LANGUAGE_MISSING) askForPack()
            end
        }
    }

    private suspend fun once(language: String, onReady: () -> Unit, onText: (String) -> Unit): SessionEnd =
        suspendCancellableCoroutine { continuation ->
            val listener = SessionListener(onReady, onText) { end ->
                if (continuation.isActive) continuation.resumeWith(Result.success(end))
            }
            recognizer.setRecognitionListener(listener)
            recognizer.startListening(intentFor(language))
            continuation.invokeOnCancellation { recognizer.cancel() }
        }

    /** Android 13+ can fetch a missing on-device speech pack; asked once per listening, the listener told why. */
    private fun askForPack() {
        if (askedForPack || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        askedForPack = true
        recognizer.triggerModelDownload(intentFor(languages.first()))
        AudoraLog.i(TAG, "Asked Android to download the ${languages.first()} speech pack")
    }

    private fun intentFor(language: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    private companion object {
        const val TAG = "Speech.Commands"
    }
}

/** One session's callbacks, turned into text and how it ended. Called on the main thread. */
private class SessionListener(
    private val onReady: () -> Unit,
    private val onText: (String) -> Unit,
    private val onEnd: (SessionEnd) -> Unit,
) : RecognitionListener {
    private var heard = false

    override fun onReadyForSpeech(params: Bundle?) = onReady()

    override fun onBeginningOfSpeech() {
        heard = true
    }

    override fun onPartialResults(partialResults: Bundle?) = texts(partialResults).forEach(onText)

    override fun onResults(results: Bundle?) {
        texts(results).forEach(onText)
        onEnd(if (heard) SessionEnd.HEARD else SessionEnd.NOTHING)
    }

    override fun onError(error: Int) = onEnd(endFor(error))

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

/** The best guess only: alternatives would act on words the listener did not say. */
private fun texts(bundle: Bundle?): List<String> =
    bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.take(1).orEmpty()

private fun endFor(error: Int): SessionEnd = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SessionEnd.NOTHING

    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_AUDIO,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
    -> SessionEnd.BUSY

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SessionEnd.NO_PERMISSION

    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        SessionEnd.LANGUAGE_MISSING

    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> SessionEnd.NOT_AVAILABLE

    else -> SessionEnd.FAILED.also { AudoraLog.w(SESSION_TAG, "Recogniser error $error") }
}

private const val SESSION_TAG = "Speech.Commands"
