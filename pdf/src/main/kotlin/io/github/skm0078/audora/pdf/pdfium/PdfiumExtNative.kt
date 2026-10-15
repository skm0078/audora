// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/pdfium/PdfiumExtNative.kt
// Purpose: Kotlin side of libaudora_pdfium_ext.so (src/main/cpp): font info, flags and /ActualText per page.

package io.github.skm0078.audora.pdf.pdfium

import io.github.skm0078.audora.pdf.extract.PdfTextString
import io.github.skm0078.audora.pdf.extract.RawChars
import io.github.skm0078.audora.pdf.extract.RawPage
import io.github.skm0078.audora.pdf.extract.RawTextObject

internal object PdfiumExtNative {
    /** Resolves the FPDF* functions from the loaded libpdfium.so. Null on success, else what is missing. */
    @JvmStatic
    external fun nativeInit(): String?

    /**
     * Reads one page. Returns an Object[] in the slot order below (jni_bridge.cpp `Slot`).
     * Caller holds PdfiumAndroidKt's lock and keeps both pointers open for the call.
     */
    @JvmStatic
    external fun nativeReadPage(pagePtr: Long, textPagePtr: Long, maxFormDepth: Int): Array<Any?>

    private const val CODE_POINTS = 0
    private const val GENERATED = 1
    private const val MAP_ERROR = 2
    private const val RENDER_MODE = 3
    private const val BOXES = 4
    private const val FONT_SIZES = 5
    private const val FONT_IDS = 6
    private const val FONT_NAMES = 7
    private const val OBJECT_TEXTS = 8
    private const val OBJECT_ACTUAL_TEXTS = 9
    private const val OBJECT_SPAN_KEYS = 10
    private const val OBJECT_WIDTHS = 11
    private const val SLOT_COUNT = 12

    fun readPage(pageIndex: Int, pagePtr: Long, textPagePtr: Long, maxFormDepth: Int): RawPage {
        val slots = nativeReadPage(pagePtr, textPagePtr, maxFormDepth)
        check(slots.size == SLOT_COUNT) { "native returned ${slots.size} slots, expected $SLOT_COUNT" }
        return decode(pageIndex, slots)
    }

    @Suppress("UNCHECKED_CAST")
    private fun decode(pageIndex: Int, slots: Array<Any?>): RawPage {
        val generated = slots[GENERATED] as IntArray
        val mapError = slots[MAP_ERROR] as IntArray
        val renderMode = slots[RENDER_MODE] as IntArray
        val flags = IntArray(generated.size) {
            RawChars.flagsOf(generated = generated[it] == 1, unicodeMapError = mapError[it] == 1, renderMode[it])
        }
        val chars = RawChars(
            codePoints = slots[CODE_POINTS] as IntArray,
            flags = flags,
            boxes = slots[BOXES] as FloatArray,
            fontSizes = slots[FONT_SIZES] as FloatArray,
            fontIds = slots[FONT_IDS] as IntArray,
            fontNames = (slots[FONT_NAMES] as Array<ByteArray>).map { String(it, Charsets.UTF_8) },
        )
        val texts = slots[OBJECT_TEXTS] as Array<String>
        val actualTexts = slots[OBJECT_ACTUAL_TEXTS] as Array<ByteArray?>
        val spanKeys = slots[OBJECT_SPAN_KEYS] as LongArray
        val widths = slots[OBJECT_WIDTHS] as FloatArray
        val objects = texts.indices.map { i ->
            RawTextObject(
                text = texts[i],
                actualText = actualTexts[i]?.let(PdfTextString::decode),
                spanKey = spanKeys[i],
                width = widths[i],
            )
        }
        return RawPage(pageIndex, chars, objects)
    }
}
