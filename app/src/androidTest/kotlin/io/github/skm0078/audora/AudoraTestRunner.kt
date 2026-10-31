// File: app/src/androidTest/kotlin/io/github/skm0078/audora/AudoraTestRunner.kt
// Purpose: The app's instrumentation runner: AndroidJUnitRunner with the first-use hints muted before the app starts
//          (ADR 2026-10-30-first-use-hints).

package io.github.skm0078.audora

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.runner.AndroidJUnitRunner
import io.github.skm0078.audora.ui.hints.HintsState

/**
 * A hint shows when its moment comes, and the journey and screen tests make many such moments (a highlight, a fast
 * speed, a story): a card appearing there would hide the rows a later check looks for. They start muted; the tests
 * of the hints themselves turn them on and put the switch back.
 */
class AudoraTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        app.getSharedPreferences(HintsState.FILE, Context.MODE_PRIVATE).edit(commit = true) {
            putBoolean(HintsState.KEY_MUTED, true)
        }
        super.callApplicationOnCreate(app)
    }
}
