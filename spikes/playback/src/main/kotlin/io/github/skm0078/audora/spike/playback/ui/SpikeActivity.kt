// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/ui/SpikeActivity.kt
// Purpose: The tester's screen: pick a run, start/stop it, watch live numbers, fix battery settings, share results.

package io.github.skm0078.audora.spike.playback.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.spike.playback.SpikeApplication
import io.github.skm0078.audora.spike.playback.config.SpikePreset
import io.github.skm0078.audora.spike.playback.device.DeviceStatus
import io.github.skm0078.audora.spike.playback.service.ActiveRun
import io.github.skm0078.audora.spike.playback.service.RunStore
import io.github.skm0078.audora.spike.playback.service.SpikeCommands
import io.github.skm0078.audora.spike.playback.service.SpikePlaybackService
import java.io.File

class SpikeActivity : ComponentActivity() {
    private lateinit var store: RunStore
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var uiState by mutableStateOf(SpikeUiState())
    private val actions = ScreenActions()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AudoraLog.i(TAG, "Notification permission granted=$granted")
            sendStart() // Media notifications still work without it; the run must not depend on the answer.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = RunStore(this)
        uiState = uiState.copy(selectedPreset = store.selectedPreset, ownWakeLock = store.ownWakeLock)
        setContent {
            SpikeTheme {
                SpikeScreen(state = uiState, actions = actions)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, SpikePlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                controller = runCatching { future.get() }
                    .onFailure { AudoraLog.e(TAG, "Could not connect to the playback service", it) }
                    .getOrNull()
                actions.refresh()
            },
            ContextCompat.getMainExecutor(this),
        )
        uiState = uiState.copy(visible = true)
        actions.refresh()
    }

    override fun onStop() {
        // Controllers must be released, or the service can never stop (MediaSessionService docs).
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        controller = null
        uiState = uiState.copy(visible = false, connected = false)
        super.onStop()
    }

    /** A button tap in a visible activity: the user action Android 17 requires before audio may play (C7). */
    private fun sendStart() {
        val connected = controller
        if (connected == null) {
            AudoraLog.w(TAG, "Start tapped before the service connected")
            return
        }
        val state = uiState
        AudoraLog.i(TAG, "Start tapped: preset=${state.selectedPreset.name} ownWakeLock=${state.ownWakeLock}")
        connected.sendCustomCommand(
            SpikeCommands.START,
            SpikeCommands.startArgs(state.selectedPreset, state.ownWakeLock),
        )
            .addListener(actions::refresh, ContextCompat.getMainExecutor(this))
    }

    private fun launch(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure { AudoraLog.e(TAG, "No app could handle ${intent.action}", it) }
    }

    private inner class ScreenActions : SpikeActions {
        override fun selectPreset(preset: SpikePreset) {
            store.selectedPreset = preset
            uiState = uiState.copy(selectedPreset = preset)
        }

        override fun setOwnWakeLock(enabled: Boolean) {
            store.ownWakeLock = enabled
            uiState = uiState.copy(ownWakeLock = enabled)
        }

        override fun startRun() {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@SpikeActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                sendStart()
            }
        }

        override fun stopRun() {
            val connected = controller ?: return
            AudoraLog.i(TAG, "Stop tapped")
            connected.sendCustomCommand(SpikeCommands.STOP, Bundle.EMPTY)
                .addListener(::refresh, ContextCompat.getMainExecutor(this@SpikeActivity))
        }

        override fun refresh() {
            val context = this@SpikeActivity
            val live = ActiveRun.service?.liveReport()
            uiState = uiState.copy(
                connected = controller != null,
                runActive = live != null,
                report = live ?: store.lastReport,
                interruption = if (live == null) store.interruption else null,
                batteryLines = DeviceStatus.describe(context),
                batteryExempt = DeviceStatus.isIgnoringBatteryOptimizations(context),
            )
        }

        override fun requestBatteryExemption() =
            launch(DeviceStatus.requestIgnoreBatteryOptimizations(this@SpikeActivity))

        override fun openAppSettings() = launch(DeviceStatus.appDetailsSettings(this@SpikeActivity))

        override fun shareReport() {
            val text = listOfNotNull(uiState.interruption, uiState.report).joinToString("\n\n")
                .ifBlank { "No S3 run recorded yet." }
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Audora S3 report")
                .putExtra(Intent.EXTRA_TEXT, text)
            launch(Intent.createChooser(send, "Share S3 report"))
        }

        override fun shareLog() {
            val logFile = File(SpikeApplication.logDirectory(application), LOG_FILE_NAME)
            if (!logFile.exists()) {
                AudoraLog.w(TAG, "No log file to share at ${logFile.path}")
                return
            }
            val uri = FileProvider.getUriForFile(this@SpikeActivity, "$packageName$FILE_PROVIDER_SUFFIX", logFile)
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Audora S3 log")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            launch(Intent.createChooser(send, "Share S3 log"))
        }
    }

    private companion object {
        const val TAG = "S3.Activity"
        const val LOG_FILE_NAME = "audora.log"
        const val FILE_PROVIDER_SUFFIX = ".files"
    }
}
