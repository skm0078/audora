// File: app/src/main/kotlin/io/github/skm0078/audora/battery/OnboardingPreferences.kt
// Purpose: Remembers that the user said "Not now" to the battery setup card, so it does not nag.

package io.github.skm0078.audora.battery

import android.content.Context
import androidx.core.content.edit

/** One flag: SharedPreferences is enough and needs no extra library. Read on a background thread. */
class OnboardingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var batteryCardDismissed: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_DISMISSED, false)
        set(value) = prefs.edit { putBoolean(KEY_BATTERY_DISMISSED, value) }

    private companion object {
        const val FILE = "onboarding"
        const val KEY_BATTERY_DISMISSED = "battery_card_dismissed"
    }
}
