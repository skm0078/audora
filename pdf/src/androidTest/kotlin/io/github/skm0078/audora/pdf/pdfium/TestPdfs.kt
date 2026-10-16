// File: pdf/src/androidTest/kotlin/io/github/skm0078/audora/pdf/pdfium/TestPdfs.kt
// Purpose: Opens the test PDFs (assets/pdfs, see SOURCES.md) and their ground truth on the device.

package io.github.skm0078.audora.pdf.pdfium

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfOpenError
import java.io.File
import org.json.JSONObject

object TestPdfs {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /** Copies an asset to a file: assets may be compressed, and PDFium needs a seekable descriptor. */
    fun file(name: String): File {
        val target = File(instrumentation.targetContext.cacheDir, "test-pdfs/$name")
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            instrumentation.context.assets.open("pdfs/$name").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        return target
    }

    fun engine(file: File, config: ExtractionConfig = ExtractionConfig()) = PdfiumEngine(
        openFile = { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) },
        config = config,
    )

    fun open(name: String, password: String? = null): Outcome<PdfBook, PdfOpenError> =
        PdfBook.open(engine(file(name)), password, logContext = "test=$name")

    fun groundTruthPages(name: String): List<String> {
        val json = instrumentation.context.assets.open("pdfs/${name.removeSuffix(".pdf")}.gt.json")
            .bufferedReader()
            .readText()
        val pages = JSONObject(json).getJSONArray("pages")
        return List(pages.length()) { pages.getString(it) }
    }
}

fun characterErrorRate(expected: String, actual: String): Double {
    if (expected.isEmpty()) return if (actual.isEmpty()) 0.0 else 1.0
    var previous = IntArray(actual.length + 1) { it }
    var current = IntArray(actual.length + 1)
    for (i in 1..expected.length) {
        current[0] = i
        for (j in 1..actual.length) {
            val substitution = previous[j - 1] + if (expected[i - 1] == actual[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[actual.length].toDouble() / expected.length
}

fun collapseWhitespace(s: String): String = s.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

/** Whitespace collapsed, ZWNJ/ZWJ dropped (the golden score ignores them too), S2's page footer removed. */
fun comparable(s: String): String = collapseWhitespace(
    s.replace(Regex("Audora S2 test corpus - \\S+\\s+\\d+"), " ").replace("\u200C", "").replace("\u200D", ""),
)
