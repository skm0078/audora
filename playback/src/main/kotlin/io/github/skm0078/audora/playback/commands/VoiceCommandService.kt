// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/commands/VoiceCommandService.kt
// Purpose: Keeps the microphone usable with the screen off while voice commands listen (plan item 38, opt-in).

package io.github.skm0078.audora.playback.commands

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.contract.PendingIntentCodes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Android lets a backgrounded app read the microphone only from a `microphone` foreground service, and (Android 14)
 * only one started while the app is visible. This service is that, and nothing else: the reader keeps listening in
 * its own process (the commands act on its player), and starts this while it is on screen ([start]) and stops it
 * when listening ends ([stop]). Its notification says Audora is listening and has Stop.
 *
 * [endings] tells the reader when listening must end from here: Stop was tapped, or Android refused the service.
 */
class VoiceCommandService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AudoraLog.i(TAG, "Screen-off listening stopped from the notification")
            endListening()
            return START_NOT_STICKY
        }
        try {
            createChannel()
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            AudoraLog.i(TAG, "Listening for voice commands with the screen off")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            AudoraLog.w(TAG, "Android refused screen-off listening (app not visible)", e)
            endListening()
        } catch (e: SecurityException) {
            AudoraLog.w(TAG, "Android refused screen-off listening (microphone not allowed)", e)
            endListening()
        }
        // Not restarted by Android: listening belongs to the reader, which starts this again when it is shown.
        return START_NOT_STICKY
    }

    private fun endListening() {
        ended.update { it + 1 }
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL,
            getString(R.string.commands_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.commands_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this,
            PendingIntentCodes.STOP_SCREEN_OFF_LISTENING,
            Intent(this, VoiceCommandService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(
                this,
                PendingIntentCodes.OPEN_FROM_VOICE_COMMANDS,
                launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_voice_commands)
            .setContentTitle(getString(R.string.commands_listening_title))
            .setContentText(getString(R.string.commands_listening_text))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .apply { open?.let { setContentIntent(it) } }
            .addAction(Notification.Action.Builder(null, getString(R.string.commands_listening_stop), stop).build())
            .build()
    }

    companion object {
        private const val TAG = "Playback.CommandService"
        private const val CHANNEL = "voice_commands"
        private const val NOTIFICATION_ID = 0x0A38
        private const val ACTION_STOP = "io.github.skm0078.audora.action.STOP_SCREEN_OFF_LISTENING"

        private val ended = MutableStateFlow(0)

        /**
         * Counts the times listening had to end from here (Stop tapped, Android refused the service): a session
         * notes the count before [start] and ends when it grows.
         */
        val endings: StateFlow<Int> = ended.asStateFlow()

        /** Call only while Audora is visible; Android refuses a microphone service started from the background. */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, VoiceCommandService::class.java))
            } catch (e: IllegalStateException) {
                AudoraLog.w(TAG, "Could not start screen-off listening", e)
                ended.update { it + 1 }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceCommandService::class.java))
        }
    }
}
