// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/device/ProcStatus.kt
// Purpose: Reads the process's resident memory from /proc/self/status. Native model memory (ONNX Runtime) is
//          invisible to the Java heap, so RSS is the honest number for "how much RAM does this voice need".

package io.github.skm0078.audora.spike.voices.device

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

object ProcStatus {
    private const val KB_PER_MB = 1_024
    private const val TAG = "S1.Proc"

    /** Value of a "Key:   1234 kB" line in MB, or null if absent. */
    fun parseKbFieldMb(status: String, key: String): Long? = status.lineSequence()
        .firstOrNull { it.startsWith("$key:") }
        ?.substringAfter(':')
        ?.trim()
        ?.substringBefore(' ')
        ?.toLongOrNull()
        ?.let { it / KB_PER_MB }

    fun currentRssMb(file: File = File("/proc/self/status")): Long? = try {
        parseKbFieldMb(file.readText(), "VmRSS")
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Could not read ${file.path}", e)
        null
    }
}
