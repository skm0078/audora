// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/ocr/OcrModelFixtures.kt
// Purpose: Test doubles for the OCR model manager: small fake models, their catalog and a server per URL.

package io.github.skm0078.audora.speech.ocr

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.voices.FakeHttpSource
import io.github.skm0078.audora.speech.voices.HttpResponse
import io.github.skm0078.audora.speech.voices.HttpSource
import io.github.skm0078.audora.speech.voices.sha256Hex

/** Bytes standing in for `<code>.traineddata`, different per language. */
fun fakeModelBytes(lang: Lang, size: Int = 3_000): ByteArray = ByteArray(size) { (it + lang.ordinal * 7).toByte() }

fun ocrModel(lang: Lang, bytes: ByteArray = fakeModelBytes(lang)): OcrModel {
    val code = mapOf(Lang.EN to "eng", Lang.HI to "hin", Lang.BN to "ben").getValue(lang)
    return OcrModel(
        lang,
        "$code.traineddata",
        "https://example.test/$code.traineddata",
        sha256Hex(bytes),
        bytes.size.toLong(),
    )
}

/** One [FakeHttpSource] per URL, so each model can be served, broken or dropped on its own. */
class FakeModelServer(contents: Map<String, ByteArray>) : HttpSource {
    val sources = contents.mapValues { (_, bytes) -> FakeHttpSource(bytes) }

    override fun open(url: String, offset: Long): HttpResponse = sources.getValue(url).open(url, offset)
}
