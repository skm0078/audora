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
 * only one started while the app is visible (ADR-051). This service is that, and nothing else: the reader keeps
 * listening in its own process (the commands act on its player), and starts this while it is on screen ([start]) and
 * ends it when listening ends ([end]). Its notification says Audora is listening and has Stop; swiping it away stops
 * too.
 *
 * Every command after the first reaches it as an intent through startService, never stopService: Android 14+ ends
 * the app when a service started with startForegroundService is stopped before its onStartCommand ran, and intents
 * are delivered in order, so the start is always handled first.
 *
 * [endings] tells the reader when listening must end from here: Stop was tapped, or Android refused the service.
 */
class VoiceCommandService : Service() {
    /** Main thread only (onStartCommand): whether the foreground notification is up. */
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AudoraLog.i(TAG, "Screen-off listening stopped from the notification")
                ended.update { it + 1 }
                stopSelf(startId)
            }

            ACTION_END -> end(intent, startId)

            // Not listening: this start is the service's only reason to run, so it ends with it.
            ACTION_SILENCED -> if (listening) post(intent.getBooleanExtra(EXTRA_SILENCED, false)) else stopSelf(startId)

            else -> startListening(startId)
        }
        // Not restarted by Android: listening belongs to the reader, which starts this again when it is shown.
        return START_NOT_STICKY
    }

    private fun startListening(startId: Int) {
        try {
            createChannel()
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIFICATION_ID, notification(silenced = false), type)
            listening = true
            AudoraLog.i(TAG, "Listening for voice commands with the screen off")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            refused("app not visible", e, startId)
        } catch (e: SecurityException) {
            refused("microphone not allowed", e, startId)
        }
    }

    private fun refused(why: String, e: Exception, startId: Int) {
        AudoraLog.w(TAG, "Android refused screen-off listening ($why)", e)
        ended.update { it + 1 }
        stopSelf(startId)
    }

    /** The reader ended the session; a newer start already queued keeps the service (stopSelf(startId)). */
    private fun end(intent: Intent, startId: Int) {
        val notice = intent.getStringExtra(EXTRA_NOTICE)
            ?.let { name -> ListeningNotice.entries.find { it.name == name } }
        if (notice != null && notice != ListeningNotice.NONE) {
            notify(notice, intent.getIntExtra(EXTRA_IDLE_MINUTES, 0))
        }
        listening = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL,
            getString(R.string.commands_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.commands_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun post(silenced: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(silenced))
    }

    private fun notification(silenced: Boolean): Notification {
        val stop = PendingIntent.getService(
            this,
            PendingIntentCodes.STOP_SCREEN_OFF_LISTENING,
            Intent(this, VoiceCommandService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (silenced) R.string.commands_listening_silenced else R.string.commands_listening_text
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_voice_commands)
            .setContentTitle(getString(R.string.commands_listening_title))
            .setContentText(getString(text))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setDeleteIntent(stop)
            .apply { openApp()?.let { setContentIntent(it) } }
            .addAction(Notification.Action.Builder(null, getString(R.string.commands_listening_stop), stop).build())
            .build()
    }

    /** A plain notification (not the service's) saying listening ended and why. */
    private fun notify(notice: ListeningNotice, idleMinutes: Int) {
        val text = when (notice) {
            ListeningNotice.IDLE ->
                resources.getQuantityString(R.plurals.commands_stopped_idle, idleMinutes, idleMinutes)

            else -> getString(R.string.commands_stopped_microphone)
        }
        val ended = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_voice_commands)
            .setContentTitle(getString(R.string.commands_stopped_title))
            .setContentText(text)
            .setAutoCancel(true)
            .apply { openApp()?.let { setContentIntent(it) } }
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTICE_ID, ended)
        AudoraLog.i(TAG, "Told the user screen-off listening ended: $notice")
    }

    private fun openApp(): PendingIntent? = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
        PendingIntent.getActivity(
            this,
            PendingIntentCodes.OPEN_FROM_VOICE_COMMANDS,
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val TAG = "Playback.CommandService"
        private const val CHANNEL = "voice_commands"
        private const val NOTIFICATION_ID = 0x0A38
        private const val NOTICE_ID = 0x0A39
        private const val ACTION_STOP = "io.github.skm0078.audora.action.STOP_SCREEN_OFF_LISTENING"
        private const val ACTION_END = "io.github.skm0078.audora.action.END_SCREEN_OFF_LISTENING"
        private const val ACTION_SILENCED = "io.github.skm0078.audora.action.SCREEN_OFF_LISTENING_SILENCED"
        private const val EXTRA_NOTICE = "notice"
        private const val EXTRA_IDLE_MINUTES = "idleMinutes"
        private const val EXTRA_SILENCED = "silenced"

        private val ended = MutableStateFlow(0)

        /**
         * Counts the times listening had to end from here (Stop tapped or swiped away, Android refused the service):
         * a session notes the count before [start] and ends when it grows.
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

        /** Ends the service after its start was handled, posting [notice] when it is not NONE. */
        fun end(context: Context, notice: ListeningNotice = ListeningNotice.NONE, idleMinutes: Int = 0) {
            val intent = Intent(context, VoiceCommandService::class.java)
                .setAction(ACTION_END)
                .putExtra(EXTRA_NOTICE, notice.name)
                .putExtra(EXTRA_IDLE_MINUTES, idleMinutes)
            send(context, intent) {
                // Only when Android refuses even this (the app long in the background, the service gone): nothing
                // is waiting on a start then, so stopping directly cannot meet one.
                context.stopService(Intent(context, VoiceCommandService::class.java))
            }
        }

        /** Shows in the notification that Android silenced the microphone ([silenced]), or that it is back. */
        fun silenced(context: Context, silenced: Boolean) {
            val intent = Intent(context, VoiceCommandService::class.java)
                .setAction(ACTION_SILENCED)
                .putExtra(EXTRA_SILENCED, silenced)
            send(context, intent) {}
        }

        private fun send(context: Context, intent: Intent, refused: () -> Unit) {
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                AudoraLog.w(TAG, "Android refused ${intent.action}", e)
                refused()
            }
        }
    }
}
