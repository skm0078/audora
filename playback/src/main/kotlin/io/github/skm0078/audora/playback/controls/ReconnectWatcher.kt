// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/controls/ReconnectWatcher.kt
// Purpose: Android's side of resuming when headphones reconnect (CF-4): the player's pauses and the phone's audio
//          outputs feed ReconnectResume, and a yes plays the book again.

package io.github.skm0078.audora.playback.controls

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import io.github.skm0078.audora.core.logging.AudoraLog

/**
 * Media3 pauses the player when the headphones or speaker leave (handleAudioBecomingNoisy): that pause, and no other,
 * is what a reconnect may undo. Main thread; [start] once, [release] when the service ends.
 */
class ReconnectWatcher(
    context: Context,
    private val player: Player,
    private val decide: ReconnectResume,
    private val bookLabel: () -> String?,
) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                decide.onPlaying()
            } else {
                decide.onPaused(byDisconnect = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
            }
        }
    }

    private val devices = object : AudioDeviceCallback() {
        // Also called once on registration with the outputs already there: nothing was paused by a disconnect then.
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any(::isListeningOutput)) onOutputConnected()
        }
    }

    fun start() {
        player.addListener(playerListener)
        audio?.registerAudioDeviceCallback(devices, main)
    }

    fun release() {
        player.removeListener(playerListener)
        audio?.unregisterAudioDeviceCallback(devices)
    }

    private fun onOutputConnected() {
        val decision = decide.onOutputConnected()
        if (decision == ReconnectResume.Decision.NOT_PAUSED_BY_DISCONNECT) return
        val canPlay = player.mediaItemCount > 0 && !player.playWhenReady
        AudoraLog.i(TAG, "Headphones or speaker back: $decision, book=${bookLabel()} canPlay=$canPlay")
        if (decision == ReconnectResume.Decision.RESUME && canPlay) player.play()
    }

    private companion object {
        const val TAG = "Playback.Reconnect"

        /** Outputs a listener puts on to listen; not the phone's own speaker, a call headset (SCO) or a TV. */
        val LISTENING_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )

        fun isListeningOutput(device: AudioDeviceInfo): Boolean = device.isSink && device.type in LISTENING_OUTPUTS
    }
}
