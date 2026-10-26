// File: core/src/test/kotlin/io/github/skm0078/audora/core/sync/SyncSettingsTest.kt
// Purpose: The sync settings survive a write and a read; a missing file means sync is off with book copies on.

package io.github.skm0078.audora.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSettingsTest {
    @Test
    fun `settings read back as written`() {
        val settings = SyncSettings(
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3AListenLock",
            folderName = "My\tfolder",
            deviceId = "4f1c",
            copyBooks = false,
            lastSyncMillis = 1_234L,
            lastFailed = true,
        )
        assertEquals(settings, SyncSettings.decode(settings.encode()))
    }

    @Test
    fun `no file is sync off with book copies on`() {
        val settings = SyncSettings.decode(null)
        assertNull(settings.treeUri)
        assertEquals(true, settings.copyBooks)
        assertNull(settings.lastSyncMillis)
    }

    @Test
    fun `stopped sync writes no folder`() {
        val stopped = SyncSettings(treeUri = null, deviceId = "4f1c")
        assertEquals(stopped, SyncSettings.decode(stopped.encode()))
    }
}
