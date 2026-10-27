// File: playback/src/main/cpp/djvu_bridge.h
// Purpose: The little of DjVuLibre's ddjvuapi the app needs (ADR 2026-10-27-djvu-books): open a bundled DjVu, count
//          its pages, give each page's hidden text, the outline and the metadata as s-expressions, and draw a page.
//          No JNI here, so it is built and tested on a computer too (scripts/fixtures/djvu_bridge_check.cpp).

#pragma once

#include <cstdint>
#include <string>

namespace audora::djvu {

class Document;

/** Opens the DjVu at [path]; null with [error] set when it cannot be read or is an indirect (multi-file) one. */
Document *open(const char *path, std::string &error);

void close(Document *document);

int pageCount(Document *document);

/**
 * The page's hidden text as an s-expression, `(page x0 y0 x1 y1 (line ... (word ... "text")))`, strings quoted
 * with only `\"` and `\\` escaped and UTF-8 left as it is; empty when the page has no text layer.
 */
std::string pageText(Document *document, int page);

/** The document's outline, `(bookmarks ("title" "#page" children...) ...)`; empty when it has none. */
std::string outline(Document *document);

/** The document's annotations, its `(metadata (title "...") ...)` among them; empty when it has none. */
std::string annotations(Document *document);

/** The page's size in pixels at its own resolution; false when the page cannot be decoded. */
bool pageSize(Document *document, int page, int &width, int &height);

/**
 * Draws the whole page scaled to [width] x [height] into [pixels] (RGBA bytes in memory, as Android's ARGB_8888,
 * opaque), [stride] bytes a row; false when the page cannot be decoded.
 */
bool render(Document *document, int page, int width, int height, uint8_t *pixels, int stride);

}  // namespace audora::djvu
