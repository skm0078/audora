// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/service/LockScreenButtonsTest.kt
// Purpose: The notification's and lock screen's buttons (CF-1) take the back, forward and overflow places with
//          Audora's own commands, and the bookmark fills when asked. On a device: CommandButton needs Android.

package io.github.skm0078.audora.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.playback.contract.LockScreenCommands
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class LockScreenButtonsTest {
    private val buttons = LockScreenButtons(LockScreenButtons.Labels("Previous", "Next", "Bookmark", "Bookmarked"))

    @Test
    fun previousNextAndBookmarkTakeTheirPlaces() {
        val shown = buttons.buttons(bookmarked = false)
        assertEquals(
            listOf(
                LockScreenCommands.ACTION_PREVIOUS_SENTENCE,
                LockScreenCommands.ACTION_NEXT_SENTENCE,
                LockScreenCommands.ACTION_BOOKMARK,
            ),
            shown.map { it.sessionCommand?.customAction },
        )
        assertEquals(
            listOf(CommandButton.SLOT_BACK, CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW),
            shown.map { it.slots.get(0) },
        )
        assertEquals(listOf("Previous", "Next", "Bookmark"), shown.map { it.displayName.toString() })
        assertEquals(CommandButton.ICON_BOOKMARK_UNFILLED, shown[2].icon)
    }

    @Test
    fun theBookmarkFillsWhileItsSentencePlays() {
        val bookmark = buttons.buttons(bookmarked = true)[2]
        assertEquals(CommandButton.ICON_BOOKMARK_FILLED, bookmark.icon)
        assertEquals("Bookmarked", bookmark.displayName.toString())
    }

    @Test
    fun everyButtonIsOneOfTheCommandsOwnControllersAreGiven() {
        val commands = buttons.buttons(bookmarked = false).mapNotNull { it.sessionCommand }
        assertEquals(LockScreenCommands.ALL, commands)
    }
}
