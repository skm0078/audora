// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/service/OtherAppsCommandsTest.kt
// Purpose: An app with no media permission can play, pause and skip, but never load a book or read what is playing.

package io.github.skm0078.audora.playback.service

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherAppsCommandsTest {
    @Test
    fun `other apps keep the transport controls`() {
        listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
        ).forEach { assertTrue("missing $it", OTHER_APPS.contains(it)) }
    }

    @Test
    fun `other apps can neither load a book nor read what is playing`() {
        listOf(
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
        ).forEach { assertFalse("allowed $it", OTHER_APPS.contains(it)) }
    }
}
