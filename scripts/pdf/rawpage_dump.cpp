// File: scripts/pdf/rawpage_dump.cpp
// Purpose: Dumps one PDF page as a RawPage fixture for :pdf's JVM tests, using the production page reader
//          (pdf/src/main/cpp/page_reader.cpp) against a desktop libpdfium.so.
//
// Build and run on Linux (libpdfium.so from the pypdfium2 wheel works):
//   g++ -std=c++17 -O1 -Ipdf/src/main/cpp scripts/pdf/rawpage_dump.cpp pdf/src/main/cpp/page_reader.cpp
//       pdf/src/main/cpp/pdfium_api.cpp -o /tmp/rawpage_dump -ldl   (one command)
//   /tmp/rawpage_dump /path/to/libpdfium.so file.pdf PAGE_INDEX [PASSWORD] > fixture.rawpage
//
// Format (one record per line, text fields hex-encoded so no escaping is needed; "-" = absent, "=" = empty):
//   page INDEX
//   font ID NAME_UTF8_HEX
//   char CODEPOINT GENERATED MAP_ERROR RENDER_MODE LEFT TOP RIGHT BOTTOM FONT_SIZE FONT_ID
//   obj TEXT_UTF8_HEX ACTUALTEXT_RAW_HEX SPAN_ID WIDTH
// SPAN_ID renumbers PDFium's mark pointers 1, 2, 3... in first-seen order; 0 = no /ActualText.
// Reader: pdf/src/test/kotlin/.../extract/RawPageFixture.kt.

#include <dlfcn.h>

#include <cstdio>
#include <cstdlib>
#include <map>
#include <string>

#include "page_reader.h"

namespace {

std::string Hex(const std::string& bytes) {
    static const char* kDigits = "0123456789abcdef";
    std::string out;
    for (unsigned char c : bytes) {
        out += kDigits[c >> 4];
        out += kDigits[c & 15];
    }
    return out;
}

std::string Utf8(const std::u16string& s) {
    std::string out;
    for (size_t i = 0; i < s.size(); ++i) {
        uint32_t c = s[i];
        if (c >= 0xD800 && c < 0xDC00 && i + 1 < s.size()) c = 0x10000 + ((c - 0xD800) << 10) + (s[++i] - 0xDC00);
        if (c < 0x80) {
            out += static_cast<char>(c);
        } else if (c < 0x800) {
            out += static_cast<char>(0xC0 | (c >> 6));
            out += static_cast<char>(0x80 | (c & 63));
        } else if (c < 0x10000) {
            out += static_cast<char>(0xE0 | (c >> 12));
            out += static_cast<char>(0x80 | ((c >> 6) & 63));
            out += static_cast<char>(0x80 | (c & 63));
        } else {
            out += static_cast<char>(0xF0 | (c >> 18));
            out += static_cast<char>(0x80 | ((c >> 12) & 63));
            out += static_cast<char>(0x80 | ((c >> 6) & 63));
            out += static_cast<char>(0x80 | (c & 63));
        }
    }
    return out;
}

std::string Field(const std::string& bytes) { return bytes.empty() ? "-" : Hex(bytes); }

template <typename Fn>
Fn Symbol(void* handle, const char* name) {
    auto fn = reinterpret_cast<Fn>(dlsym(handle, name));
    if (fn == nullptr) {
        std::fprintf(stderr, "missing %s\n", name);
        std::exit(1);
    }
    return fn;
}

}  // namespace

int main(int argc, char** argv) {
    if (argc < 4) {
        std::fprintf(stderr, "usage: %s LIBPDFIUM PDF PAGE_INDEX [PASSWORD]\n", argv[0]);
        return 1;
    }
    void* handle = dlopen(argv[1], RTLD_NOW);
    if (handle == nullptr) {
        std::fprintf(stderr, "%s\n", dlerror());
        return 1;
    }
    audora::PdfiumApi api{};
    if (const char* missing = audora::LoadPdfiumApi(handle, &api)) {
        std::fprintf(stderr, "missing %s\n", missing);
        return 1;
    }
    Symbol<void (*)()>(handle, "FPDF_InitLibrary")();
    void* document = Symbol<void* (*)(const char*, const char*)>(handle, "FPDF_LoadDocument")(
        argv[2], argc > 4 ? argv[4] : nullptr);
    if (document == nullptr) {
        std::fprintf(stderr, "cannot open %s, error %lu\n", argv[2],
                     Symbol<unsigned long (*)()>(handle, "FPDF_GetLastError")());
        return 2;
    }
    int pageIndex = std::atoi(argv[3]);
    void* page = Symbol<void* (*)(void*, int)>(handle, "FPDF_LoadPage")(document, pageIndex);
    void* textPage = Symbol<void* (*)(void*)>(handle, "FPDFText_LoadPage")(page);

    audora::CharData chars;
    audora::ReadChars(api, textPage, &chars);
    audora::TextObjectData objects;
    audora::ReadTextObjects(api, page, textPage, 8, &objects);

    std::printf("page %d\n", pageIndex);
    for (size_t i = 0; i < chars.fontNames.size(); ++i) {
        std::printf("font %zu %s\n", i, Field(chars.fontNames[i]).c_str());
    }
    for (size_t i = 0; i < chars.codePoints.size(); ++i) {
        const float* box = &chars.boxes[4 * i];
        std::printf("char %d %d %d %d %.1f %.1f %.1f %.1f %.1f %d\n", chars.codePoints[i], chars.generated[i],
                    chars.mapError[i], chars.renderMode[i], box[0], box[1], box[2], box[3], chars.fontSizes[i],
                    chars.fontIds[i]);
    }
    std::map<int64_t, int> spanIds;
    for (size_t i = 0; i < objects.texts.size(); ++i) {
        int spanId = 0;
        if (objects.spanKeys[i] != 0) {
            auto it = spanIds.emplace(objects.spanKeys[i], static_cast<int>(spanIds.size()) + 1).first;
            spanId = it->second;
        }
        std::string actual = "-";
        if (objects.hasActualText[i]) actual = objects.actualTexts[i].empty() ? "=" : Hex(objects.actualTexts[i]);
        std::printf("obj %s %s %d %.2f\n", Field(Utf8(objects.texts[i])).c_str(), actual.c_str(), spanId,
                    objects.widths[i]);
    }
    return 0;
}
