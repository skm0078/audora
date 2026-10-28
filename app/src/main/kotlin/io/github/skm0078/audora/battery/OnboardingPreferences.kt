// File: app/src/main/kotlin/io/github/skm0078/audora/battery/OnboardingPreferences.kt
// Purpose: Remembers that the user said "Not now" to the battery setup card, so it does not nag, and that the
//          first-run screens were finished (UI plan, wave 4), that the reader's long-press tips were read, which
//          "What's new" entries were shown, and that the 1-minute tour was offered (ADR 2026-10-28-home-and-tour).

package io.github.skm0078.audora.battery

import android.content.Context
import androidx.core.content.edit

/** A few flags: SharedPreferences is enough and needs no extra library. Read on a background thread. */
class OnboardingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var batteryCardDismissed: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_DISMISSED, false)
        set(value) = prefs.edit { putBoolean(KEY_BATTERY_DISMISSED, value) }

    /** The first-run screens were finished or skipped; read once at start (one boolean from a small file). */
    var firstRunDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_FIRST_RUN_DONE, value) }

    /** The reader's long-press tips were dismissed with "Got it". */
    var gestureTipsSeen: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_TIPS_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_GESTURE_TIPS_SEEN, value) }

    /** The 1-minute tour was offered once, whatever the answer; You › Show me around replays it. */
    var tourOffered: Boolean
        get() = prefs.getBoolean(KEY_TOUR_OFFERED, false)
        set(value) = prefs.edit { putBoolean(KEY_TOUR_OFFERED, value) }

    /** The numbered "What's new" list seen last, from builds before entries had names; 0 before any. Read only. */
    val whatsNewSeenList: Int
        get() = prefs.getInt(KEY_WHATS_NEW_SEEN, 0)

    /** The "What's new" entries shown (or skipped as a new listener's), by name; null until first written. */
    val whatsNewSeenEntries: Set<String>?
        get() = prefs.getStringSet(KEY_WHATS_NEW_SEEN_ENTRIES, null)?.toSet() // a copy: the stored set is not ours

    fun markWhatsNewSeen(names: Set<String>) = prefs.edit { putStringSet(KEY_WHATS_NEW_SEEN_ENTRIES, names) }

    private companion object {
        const val FILE = "onboarding"
        const val KEY_WHATS_NEW_SEEN = "whats_new_seen"
        const val KEY_WHATS_NEW_SEEN_ENTRIES = "whats_new_seen_entries"
        const val KEY_BATTERY_DISMISSED = "battery_card_dismissed"
        const val KEY_FIRST_RUN_DONE = "first_run_done"
        const val KEY_GESTURE_TIPS_SEEN = "gesture_tips_seen"
        const val KEY_TOUR_OFFERED = "tour_offered"
    }
}
