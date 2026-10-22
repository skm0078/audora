// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/listen/ListenPin.kt
// Purpose: The reading tutor's pinned speech recognition downloads, one per language (plan item 67, ADR-050).

package io.github.skm0078.audora.speech.listen

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.download.PinnedZipFiles
import io.github.skm0078.audora.speech.voices.PinnedFile
import java.io.File

/** How a model listens, and so which files its zip holds; [catalogName] is its `kind` in speech/models.json. */
enum class ListenKind(val catalogName: String, val entries: Set<String>) {
    /** Word by word as the reader speaks (a streaming Zipformer transducer). */
    STREAMING_TRANSDUCER(
        "streaming-transducer",
        setOf("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt", "LICENSE"),
    ),

    /** Phrase by phrase: a voice detector cuts the phrases and an offline CTC model reads each one. */
    PHRASE_CTC("phrase-ctc", setOf("model.onnx", "tokens.txt", "vad.onnx", "LICENSE")),
}

/**
 * A stored zip on the app's own voice mirror (ADR-022), built by scripts/build_asr_mirror.py from speech/models.json's
 * `asr` entries. The pins are repeated here so the app needs no catalog parser; a JVM test checks they match.
 *
 * @property unpackedBytes every entry's size together, for the space check, the size shown and the install check.
 */
data class ListenPin(
    override val id: String,
    val fileName: String,
    override val sha256: String,
    override val sizeBytes: Long,
    val unpackedBytes: Long,
    val kind: ListenKind,
    val baseUrl: String,
) : PinnedFile {
    override val url: String get() = baseUrl + fileName

    companion object {
        private const val MIRROR = "https://github.com/skm0078/audora/releases/download/voices/"

        /** Streaming Zipformer 20M trained on LibriSpeech (Apache-2.0), int8. */
        val ENGLISH = ListenPin(
            id = "asr-en-zipformer-20m",
            fileName = "asr-en-zipformer-20m-896cc6625975.zip",
            sha256 = "896cc6625975bbd1dd3d280f37b6bfb575c5cb03120acba4807e0fcc33e47343",
            sizeBytes = 43_661_165,
            unpackedBytes = 43_660_659,
            kind = ListenKind.STREAMING_TRANSDUCER,
            baseUrl = MIRROR,
        )

        /** Vosk small streaming Bengali, a Zipformer2 transducer (Apache-2.0). */
        val BENGALI = ListenPin(
            id = "asr-bn-zipformer-vosk",
            fileName = "asr-bn-zipformer-vosk-c108226b561f.zip",
            sha256 = "c108226b561fe6d6c854ec9bc3d7fd6079d22a8a352f350217f5f503caa4f8a5",
            sizeBytes = 94_131_803,
            unpackedBytes = 94_131_297,
            kind = ListenKind.STREAMING_TRANSDUCER,
            baseUrl = MIRROR,
        )

        /** Dolphin base CTC, multilingual (Apache-2.0), int8, with Silero VAD (MIT); no free streaming Hindi model. */
        val HINDI = ListenPin(
            id = "asr-hi-dolphin-base",
            fileName = "asr-hi-dolphin-base-6036b50ffc67.zip",
            sha256 = "6036b50ffc677114c768c28368ec51c12aa7fe7a00bff02d9c11f1ee08d09a50",
            sizeBytes = 104_891_149,
            unpackedBytes = 104_890_753,
            kind = ListenKind.PHRASE_CTC,
            baseUrl = MIRROR,
        )

        fun of(lang: Lang): ListenPin = when (lang) {
            Lang.EN -> ENGLISH
            Lang.HI -> HINDI
            Lang.BN -> BENGALI
        }
    }
}

/** A [PinnedZipFiles] taking every file of [pin]'s kind; installed only while they have the pinned total size. */
fun listenFiles(root: File, pin: ListenPin, maxUnpackedBytes: Long, bufferBytes: Int): PinnedZipFiles = PinnedZipFiles(
    root = root,
    pin = pin,
    entries = pin.kind.entries,
    sizedEntries = pin.kind.entries,
    sizedBytes = pin.unpackedBytes,
    maxUnpackedBytes = maxUnpackedBytes,
    bufferBytes = bufferBytes,
    label = "speech model",
)
