// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/service/PlaybackRun.kt
// Purpose: One S3 test run: builds the player over the render queue, feeds the playlist and records metrics.

package io.github.skm0078.audora.spike.playback.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.wav.WavExtractor
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.spike.playback.config.SpikeConfig
import io.github.skm0078.audora.spike.playback.config.SpikePreset
import io.github.skm0078.audora.spike.playback.device.DeviceStatus
import io.github.skm0078.audora.spike.playback.engine.FakeBook
import io.github.skm0078.audora.spike.playback.engine.FakeSpeechEngine
import io.github.skm0078.audora.spike.playback.engine.RenderClock
import io.github.skm0078.audora.spike.playback.metrics.Heartbeat
import io.github.skm0078.audora.spike.playback.metrics.PlayerPhase
import io.github.skm0078.audora.spike.playback.metrics.RunMetrics
import io.github.skm0078.audora.spike.playback.metrics.RunReport
import io.github.skm0078.audora.spike.playback.metrics.RunReportInput
import io.github.skm0078.audora.spike.playback.queue.PlaylistWindow
import io.github.skm0078.audora.spike.playback.queue.RenderAheadQueue
import io.github.skm0078.audora.spike.playback.queue.RenderListener
import java.text.DateFormat
import java.util.Date

/** Must be created, used and released on the main thread (the player's application thread). */
@OptIn(UnstableApi::class)
class PlaybackRun(
    private val context: Context,
    private val preset: SpikePreset,
    val config: SpikeConfig,
    private val store: RunStore,
) {
    private val book = FakeBook(config)
    private val startedRealtimeMs = SystemClock.elapsedRealtime()
    private val startedAt = DateFormat.getDateTimeInstance().format(Date())
    private val metrics = RunMetrics(startedRealtimeMs)
    private val handler = Handler(Looper.getMainLooper())
    private val playlistWindow = PlaylistWindow(book, minItemsAhead = config.unitsPerPage)
    private val queue = RenderAheadQueue(
        book = book,
        renderer = FakeSpeechEngine(config.sampleRateHz, config.realTimeFactor, RenderClock.forWork(config.engineWork)),
        prefetchUnits = config.prefetchUnits,
        listener = QueueListener(),
    )
    private val wakeLock: PowerManager.WakeLock? = if (config.ownWakeLock) {
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
    } else {
        null
    }
    private var heartbeats = 0
    private var released = false

    val player: ExoPlayer = buildPlayer().apply { addListener(PlayerListener()) }

    /** Starts playback. Only ever called from a user action (C7: Android 17 mutes playback started otherwise). */
    fun start() {
        AudoraLog.i(TAG, "Run start: preset=${preset.name} ${config.summary()}")
        store.beginRun(System.currentTimeMillis(), DeviceStatus.batteryPercent(context))
        queue.start()
        appendIfNeeded()
        player.prepare()
        player.playWhenReady = true
        scheduleHeartbeat()
    }

    /** Ends the run on the user's request and stores the final report. Safe to call twice. */
    fun finish() {
        if (released) return
        metrics.finish(SystemClock.elapsedRealtime())
        val report = report()
        store.finishRun(report)
        AudoraLog.i(TAG, "Run finished by user.\n$report")
        release()
    }

    fun report(): String = RunReport.format(
        RunReportInput(
            presetLabel = preset.label,
            isGateRun = preset == SpikePreset.GATE || preset == SpikePreset.GATE_BUSY_CPU,
            configSummary = config.summary(),
            startedAt = startedAt,
            snapshot = metrics.snapshot(SystemClock.elapsedRealtime()),
            device = DeviceStatus.describe(context) +
                ("Battery at start" to (store.startBatteryPercent?.let { "$it%" } ?: "unknown")) +
                ("Render-ahead ready" to "${queue.readyAheadCount()} units"),
            notes = emptyList(),
            processDied = false,
        ),
    )

    private fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        wakeLock?.takeIf { it.isHeld }?.release()
        queue.close()
        player.release()
    }

    @OptIn(ExperimentalApi::class)
    private fun buildPlayer(): ExoPlayer {
        val dataSourceFactory = DataSource.Factory {
            TtsDataSource(queue, config.sampleRateHz) { unit, waitedMs ->
                if (waitedMs >= BLOCK_LOG_THRESHOLD_MS) AudoraLog.d(TAG, "Loader waited $waitedMs ms for unit $unit")
            }
        }
        val wavOnly = ExtractorsFactory { arrayOf<Extractor>(WavExtractor()) }
        val speech = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSourceFactory, wavOnly))
            .setAudioAttributes(speech, HANDLE_AUDIO_FOCUS)
            .setHandleAudioBecomingNoisy(true)
            // The Media3 default, set explicitly because the whole spike depends on it.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .enablePerStreamMediaProgression(config.perStreamProgression)
            .build()
    }

    private fun appendIfNeeded() {
        val lastQueued = player.mediaItemCount - 1
        val current = player.currentMediaItemIndex.coerceAtLeast(0)
        val range = playlistWindow.nextAppend(lastQueued, current) ?: return
        player.addMediaItems(range.map { SpikeMediaItems.forUnit(book.unit(it), preset.label) })
        AudoraLog.d(TAG, "Appended units ${range.first}..${range.last} (page ${book.unit(range.first).page + 1})")
    }

    private fun unitsAfterCurrent(): Int {
        val current = player.currentMediaItem?.mediaId?.toIntOrNull() ?: 0
        return book.unitCount - 1 - current
    }

    private fun scheduleHeartbeat() {
        handler.postDelayed(::heartbeat, HEARTBEAT_MS)
    }

    private fun heartbeat() {
        if (released) return
        metrics.onHeartbeat(
            Heartbeat(
                realtimeMs = SystemClock.elapsedRealtime(),
                uptimeMs = SystemClock.uptimeMillis(),
                screenOn = DeviceStatus.isScreenOn(context),
            ),
        )
        val report = report()
        store.saveProgress(report, System.currentTimeMillis())
        heartbeats++
        if (heartbeats % HEARTBEATS_PER_LOG == 0) AudoraLog.i(TAG, "Progress:\n$report")
        scheduleHeartbeat()
    }

    private fun updateOwnWakeLock() {
        val lock = wakeLock ?: return
        if (player.playWhenReady && !lock.isHeld) {
            lock.acquire(OWN_WAKE_LOCK_TIMEOUT_MS)
        } else if (!player.playWhenReady && lock.isHeld) {
            lock.release()
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                val phase = SpikeMediaItems.phaseOf(player.playbackState)
                metrics.onPlayerState(phase, player.playWhenReady, SystemClock.elapsedRealtime(), unitsAfterCurrent())
                AudoraLog.d(
                    TAG,
                    "State $phase playWhenReady=${player.playWhenReady} unit=${player.currentMediaItemIndex}",
                )
                if (phase == PlayerPhase.ENDED && unitsAfterCurrent() > 0) {
                    AudoraLog.e(TAG, "INVARIANT BROKEN: player ENDED with ${unitsAfterCurrent()} units left")
                }
                updateOwnWakeLock()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val unit = mediaItem?.mediaId?.toIntOrNull() ?: return
            metrics.onTransition(
                newUnit = unit,
                newUnitDurationMs = book.unit(unit).durationMs,
                automatic = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                nowMs = SystemClock.elapsedRealtime(),
            )
            queue.onPlaying(unit)
            appendIfNeeded()
        }

        override fun onPlayerError(error: PlaybackException) {
            metrics.onError("${error.errorCodeName}: ${error.message}")
            AudoraLog.e(TAG, "Player error at unit ${player.currentMediaItemIndex}", error)
        }
    }

    private inner class QueueListener : RenderListener {
        override fun onRendered(unitIndex: Int, renderNanos: Long, audioMs: Long) {
            metrics.onRendered(renderNanos, audioMs)
        }

        override fun onRenderFailed(unitIndex: Int, error: Throwable) {
            metrics.onRenderFailed()
            AudoraLog.e(TAG, "Render failed for unit $unitIndex, engine=fake rtf=${config.realTimeFactor}", error)
        }
    }

    private companion object {
        const val TAG = "S3.Run"
        const val WAKE_LOCK_TAG = "Audora:S3Playback"
        const val HEARTBEAT_MS = 30_000L
        const val HEARTBEATS_PER_LOG = 10
        const val BLOCK_LOG_THRESHOLD_MS = 50L

        /** Pause for calls and other apps' audio, as a reader app must. */
        const val HANDLE_AUDIO_FOCUS = true

        /** Upper bound so a bug can never hold the CPU awake forever; re-acquired on every playWhenReady change. */
        const val OWN_WAKE_LOCK_TIMEOUT_MS = 3L * 60 * 60 * 1_000
    }
}
