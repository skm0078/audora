// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/PendingIntentCodes.kt
// Purpose: One request code per PendingIntent purpose, so two purposes on the same intent never share one.

package io.github.skm0078.audora.playback.contract

/**
 * Android matches PendingIntents by intent and request code, ignoring extras: two purposes built on the launch
 * intent with the same code share one PendingIntent, and FLAG_UPDATE_CURRENT on either rewrites the other's extras.
 * Every PendingIntent in Audora takes its code from here.
 *
 * Blocks, so branches landing apart never pick the same number: 1-9 playback and app basics, 10-19 the UI pass
 * (widgets, tile), 20-39 studio (talking books, copies, clips). PendingIntentCodesTest fails when two share a value.
 */
object PendingIntentCodes {
    /** The playback notification and lock screen: open Audora. */
    const val OPEN_FROM_PLAYBACK = 1

    /** The audiobook export notifications: open Audora. */
    const val OPEN_FROM_EXPORT = 2

    /** The audiobook export notification's Cancel (a service intent). */
    const val CANCEL_EXPORT = 3

    /** The screen-off voice commands notification (plan item 38): open Audora. */
    const val OPEN_FROM_VOICE_COMMANDS = 4

    /** The same notification's Stop listening (a service intent). */
    const val STOP_SCREEN_OFF_LISTENING = 5

    /** The resume widget's body: open Audora. */
    const val OPEN_FROM_WIDGET = 10

    /** The resume widget's Resume: open the last book and read on. */
    const val RESUME_FROM_WIDGET = 11

    /** The Quick Settings tile (plan item 36), on Android 14 and later: open Audora on the last book. */
    const val OPEN_FROM_TILE = 12
}
