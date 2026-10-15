// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/ui/VoicesActivity.kt
// Purpose: The tester's screen: download voices, run the benchmark, listen and rate blind, share the report.

package io.github.skm0078.audora.spike.voices.ui

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.spike.voices.VoicesApplication
import io.github.skm0078.audora.spike.voices.VoicesController
import java.io.File
import java.io.IOException

class VoicesActivity : ComponentActivity() {
    private val controller: VoicesController get() = (application as VoicesApplication).controller
    private var player: MediaPlayer? = null
    private val actions = ScreenActions()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            // A benchmark or download stops being trustworthy if the phone sleeps mid-run, so keep the screen on.
            LaunchedEffect(state.benchBusy || state.downloadBusy) {
                if (state.benchBusy || state.downloadBusy) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            VoicesTheme {
                VoicesScreen(state = state, actions = actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.refresh() // e.g. after installing a system voice pack in Settings
    }

    override fun onStop() {
        stopPlayback()
        super.onStop()
    }

    private fun stopPlayback() {
        player?.release()
        player = null
        controller.setPlaying(null)
    }

    private fun launch(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure { AudoraLog.e(TAG, "No app could handle ${intent.action}", it) }
    }

    private fun share(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        launch(Intent.createChooser(send, subject))
    }

    private inner class ScreenActions : VoicesActions {
        override fun downloadAll() = controller.downloadAll()

        override fun deleteModels() = controller.deleteModels()

        override fun startBenchmark() {
            stopPlayback()
            controller.startBenchmark()
        }

        override fun cancelBenchmark() = controller.cancelBenchmark()

        override fun play(voiceId: String) {
            val wasPlaying = controller.state.value.playingVoiceId
            stopPlayback()
            if (wasPlaying == voiceId) return // second tap on the same voice stops it
            val file = controller.sampleFile(voiceId)
            if (!file.isFile) return
            try {
                player = MediaPlayer().apply {
                    setDataSource(file.path)
                    setOnCompletionListener { stopPlayback() }
                    prepare()
                    start()
                }
                controller.setPlaying(voiceId)
            } catch (e: IOException) {
                AudoraLog.e(TAG, "Could not play the sample for $voiceId", e)
                stopPlayback()
            }
        }

        override fun rate(voiceId: String, rating: Int) = controller.setRating(voiceId, rating)

        override fun setRevealNames(reveal: Boolean) = controller.setRevealNames(reveal)

        override fun openTtsSettings() {
            // Opens the system TTS engine's "install voice data" screen (Google: download Hindi/Bengali packs).
            launch(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        }

        override fun shareReport() =
            share("Audora S1 report", controller.state.value.report.ifBlank { "No S1 benchmark recorded yet." })

        override fun shareLog() {
            val logFile = File(VoicesApplication.logDirectory(application), LOG_FILE_NAME)
            if (!logFile.exists()) {
                AudoraLog.w(TAG, "No log file to share at ${logFile.path}")
                return
            }
            val uri = FileProvider.getUriForFile(this@VoicesActivity, "$packageName$FILE_PROVIDER_SUFFIX", logFile)
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Audora S1 log")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            launch(Intent.createChooser(send, "Share S1 log"))
        }
    }

    private companion object {
        const val TAG = "S1.Activity"
        const val LOG_FILE_NAME = "audora.log"
        const val FILE_PROVIDER_SUFFIX = ".files"
    }
}
