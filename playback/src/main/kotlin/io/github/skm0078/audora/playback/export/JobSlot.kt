// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/JobSlot.kt
// Purpose: One running long job of a kind per process, across service instances (exports, copies, clips, studio).

package io.github.skm0078.audora.playback.export

import java.util.concurrent.atomic.AtomicReference

/**
 * A service's worker thread can outlive the service: after Android's time limit the service stops at once, while the
 * worker may still be inside a native call. A new start then gets a new service instance, so a per-instance field
 * would let a second run go beside the first (deleting its in-progress file, rewriting its record). Each kind of job
 * has one slot in [JobSlots]: a start is refused as busy until the old thread has really ended.
 */
class JobSlot {
    private val holder = AtomicReference<Thread?>(null)

    /** A job of this kind is running, possibly left by a service instance that has already stopped. */
    val busy: Boolean get() = holder.get() != null

    /** Runs [work] on a new thread named [name], unless a job of this kind still runs; false if refused. */
    fun launch(name: String, work: () -> Unit): Boolean {
        val thread = Thread({
            try {
                work()
            } finally {
                holder.compareAndSet(Thread.currentThread(), null)
            }
        }, name)
        if (!holder.compareAndSet(null, thread)) return false
        thread.start()
        return true
    }

    /** Interrupts the running job, if any; it ends at its next check. */
    fun interrupt() {
        holder.get()?.interrupt()
    }
}

/** The app's long jobs, one slot each; a restore waits for all of them (Backups). */
object JobSlots {
    val EXPORT = JobSlot()
    val COPY = JobSlot()
    val CLIP = JobSlot()
    val STUDIO = JobSlot()
    val VIDEO = JobSlot()

    /** Any long job is running, even one a stopped service left behind. */
    val anyBusy: Boolean get() = listOf(EXPORT, COPY, CLIP, STUDIO, VIDEO).any { it.busy }
}
