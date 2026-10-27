// File: playback/src/main/cpp/djvu_bridge_check.cpp
// Purpose: Builds on a computer with the vendored DjVuLibre and prints what djvu_bridge.cpp gives for a file, to check
//          the bridge without a phone (ADR 2026-10-27-djvu-books). Not part of the app; see the CMakeLists.txt note.
//   usage: djvu_bridge_check <file.djvu>

#include <cstdio>
#include <vector>

#include "djvu_bridge.h"

int main(int argc, char **argv) {
    if (argc != 2) return 2;
    std::string error;
    audora::djvu::Document *doc = audora::djvu::open(argv[1], error);
    if (doc == nullptr) {
        std::printf("error: %s\n", error.c_str());
        return 1;
    }
    const int pages = audora::djvu::pageCount(doc);
    std::printf("pages: %d\noutline: %s\nanno: %s\n", pages, audora::djvu::outline(doc).c_str(),
                audora::djvu::annotations(doc).c_str());
    for (int page = 0; page < pages; ++page) {
        int width = 0;
        int height = 0;
        if (!audora::djvu::pageSize(doc, page, width, height)) {
            std::printf("page %d: no size\n", page + 1);
            continue;
        }
        const int w = width / 4;
        const int h = height / 4;
        std::vector<uint8_t> pixels(static_cast<size_t>(w) * h * 4);
        const bool drawn = audora::djvu::render(doc, page, w, h, pixels.data(), w * 4);
        long dark = 0;
        for (size_t at = 0; at < pixels.size(); at += 4) dark += pixels[at] < 128;
        std::printf("page %d: %dx%d drawn=%d dark=%ld alpha=%d text=%s\n", page + 1, width, height, drawn, dark,
                    pixels[3], audora::djvu::pageText(doc, page).c_str());
    }
    audora::djvu::close(doc);
    return 0;
}
