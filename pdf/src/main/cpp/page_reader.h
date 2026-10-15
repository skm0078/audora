// File: pdf/src/main/cpp/page_reader.h
// Purpose: Reads one page's characters and text objects from PDFium into plain vectors (no JNI here).

#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "pdfium_api.h"

namespace audora {

constexpr int kUnknown = -1;

// One entry per PDFium text-page character.
struct CharData {
    std::vector<int32_t> codePoints;
    std::vector<int32_t> generated;     // 1, 0, or kUnknown
    std::vector<int32_t> mapError;      // 1, 0, or kUnknown
    std::vector<int32_t> renderMode;    // 0..7, or kUnknown
    std::vector<float> boxes;           // left, top, right, bottom per char; NaN when PDFium has none
    std::vector<float> fontSizes;
    std::vector<int32_t> fontIds;       // index into fontNames, or kUnknown
    std::vector<std::string> fontNames; // UTF-8 bytes as PDFium reports them, deduplicated
};

// One entry per text object, in content order, form XObjects flattened depth-first.
struct TextObjectData {
    std::vector<std::u16string> texts;
    std::vector<bool> hasActualText;
    std::vector<std::string> actualTexts;  // raw PDF string bytes; decoded in Kotlin
    std::vector<int64_t> spanKeys;         // the /ActualText mark's identity, 0 when none
    std::vector<float> widths;
};

void ReadChars(const PdfiumApi& api, FpdfTextPage textPage, CharData* out);

void ReadTextObjects(const PdfiumApi& api, FpdfPage page, FpdfTextPage textPage, int maxFormDepth,
                     TextObjectData* out);

}  // namespace audora
