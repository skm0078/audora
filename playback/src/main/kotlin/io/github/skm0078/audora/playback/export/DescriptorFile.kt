// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/DescriptorFile.kt
// Purpose: An open "rw" descriptor as a [SeekableFile], for tagging a chapter after MediaMuxer has finished it.

package io.github.skm0078.audora.playback.export

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import java.io.IOException

/**
 * Positional reads and writes (pread/pwrite) on [descriptor], so it works for a MediaStore entry as well as a
 * plain file, and never moves the descriptor's own position. The descriptor stays open; its owner closes it.
 */
class DescriptorFile(private val descriptor: ParcelFileDescriptor) : SeekableFile {
    private val fd get() = descriptor.fileDescriptor

    override fun size(): Long = errno { Os.fstat(fd).st_size }

    override fun read(position: Long, length: Int): ByteArray = errno {
        val bytes = ByteArray(length)
        var done = 0
        while (done < length) {
            val read = Os.pread(fd, bytes, done, length - done, position + done)
            if (read <= 0) throw IOException("File ended at ${position + done}, wanted ${position + length}")
            done += read
        }
        bytes
    }

    override fun write(position: Long, bytes: ByteArray) = errno {
        var done = 0
        while (done < bytes.size) {
            done += Os.pwrite(fd, bytes, done, bytes.size - done, position + done)
        }
    }

    override fun truncate(size: Long) = errno { Os.ftruncate(fd, size) }

    private inline fun <T> errno(block: () -> T): T = try {
        block()
    } catch (e: ErrnoException) {
        throw IOException("File access failed: ${e.message}", e)
    }
}
