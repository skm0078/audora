// File: core/src/main/kotlin/io/github/skm0078/audora/core/sync/SyncSettings.kt
// Purpose: The listener's sync choices (CF-16): the picked folder, whether book files are copied there, this
//          phone's random id and when it last synced; stored as key=value lines.

package io.github.skm0078.audora.core.sync

/**
 * @property treeUri the picked folder (a Storage Access Framework tree URI), or null when sync is off.
 * @property folderName the name the folder's app gave it, for the screen.
 * @property deviceId random, made once per install; names this phone's file in the folder, never the phone.
 * @property copyBooks whether book files are copied into the folder, so another phone can add them.
 * @property lastSyncMillis when the last sync finished, or null before the first.
 * @property lastFailed whether the last sync failed (the folder could not be reached).
 */
data class SyncSettings(
    val treeUri: String? = null,
    val folderName: String = "",
    val deviceId: String = "",
    val copyBooks: Boolean = true,
    val lastSyncMillis: Long? = null,
    val lastFailed: Boolean = false,
) {
    fun encode(): String = buildString {
        treeUri?.let { append(TREE).append('=').append(SyncText.escape(it)).append('\n') }
        append(NAME).append('=').append(SyncText.escape(folderName)).append('\n')
        append(DEVICE).append('=').append(SyncText.escape(deviceId)).append('\n')
        append(COPY).append('=').append(copyBooks).append('\n')
        lastSyncMillis?.let { append(LAST).append('=').append(it).append('\n') }
        append(FAILED).append('=').append(lastFailed).append('\n')
    }

    companion object {
        private const val TREE = "tree"
        private const val NAME = "name"
        private const val DEVICE = "device"
        private const val COPY = "copyBooks"
        private const val LAST = "lastSync"
        private const val FAILED = "lastFailed"

        /** The settings in [text]; the defaults (sync off) for a missing file, an unknown key is ignored. */
        fun decode(text: String?): SyncSettings {
            val values = text.orEmpty().lineSequence()
                .filter { '=' in it }
                .associate { it.substringBefore('=') to SyncText.unescape(it.substringAfter('=')) }
            return SyncSettings(
                treeUri = values[TREE]?.ifEmpty { null },
                folderName = values[NAME].orEmpty(),
                deviceId = values[DEVICE].orEmpty(),
                copyBooks = values[COPY]?.toBooleanStrictOrNull() ?: true,
                lastSyncMillis = values[LAST]?.toLongOrNull(),
                lastFailed = values[FAILED]?.toBooleanStrictOrNull() ?: false,
            )
        }
    }
}
