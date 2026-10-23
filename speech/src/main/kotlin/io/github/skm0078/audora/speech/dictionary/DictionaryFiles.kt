// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/dictionary/DictionaryFiles.kt
// Purpose: The dictionary's pinned zip on disk: its sorted text and licence, installed only at the pinned size (33).

package io.github.skm0078.audora.speech.dictionary

import io.github.skm0078.audora.speech.download.PinnedZipFiles
import java.io.File

/** A [PinnedZipFiles] taking the dictionary text and its licence; installed only while the text has its pinned size. */
fun dictionaryFiles(root: File, pin: DictionaryPin, maxUnpackedBytes: Long, bufferBytes: Int): PinnedZipFiles =
    PinnedZipFiles(
        root = root,
        pin = pin,
        entries = setOf(pin.entryName, pin.licenceName),
        sizedEntries = setOf(pin.entryName),
        sizedBytes = pin.unpackedBytes,
        maxUnpackedBytes = maxUnpackedBytes,
        bufferBytes = bufferBytes,
        label = "dictionary",
    )

/** The installed dictionary text. */
fun PinnedZipFiles.dictionaryText(pin: DictionaryPin): File = file(pin.entryName)
