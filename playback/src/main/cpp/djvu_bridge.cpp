// File: playback/src/main/cpp/djvu_bridge.cpp
// Purpose: ddjvuapi calls behind djvu_bridge.h (ADR 2026-10-27-djvu-books). Each document has its own context, and
//          each call waits for DjVuLibre's decoding by draining that context's messages, so the caller sees plain
//          blocking calls; the Kotlin side calls one document from one thread at a time.

#include "djvu_bridge.h"

#include <libdjvu/ddjvuapi.h>
#include <libdjvu/miniexp.h>

namespace audora::djvu {

class Document {
  public:
    ddjvu_context_t *context = nullptr;
    ddjvu_document_t *document = nullptr;
    std::string lastError;
};

namespace {

/** Takes every waiting message off the context, keeping the last error's text; waits for one first if [wait]. */
void drain(Document *doc, bool wait) {
    if (wait) ddjvu_message_wait(doc->context);
    while (const ddjvu_message_t *message = ddjvu_message_peek(doc->context)) {
        if (message->m_any.tag == DDJVU_ERROR && message->m_error.message != nullptr) {
            doc->lastError = message->m_error.message;
        }
        ddjvu_message_pop(doc->context);
    }
}

/** Waits until [expression] is no longer miniexp_dummy, which means "still decoding". */
template <typename Fetch>
miniexp_t await(Document *doc, Fetch fetch) {
    miniexp_t expression;
    while ((expression = fetch()) == miniexp_dummy) drain(doc, true);
    return expression;
}

void quote(std::string &out, const char *text) {
    out += '"';
    for (const char *at = text; *at != '\0'; ++at) {
        if (*at == '"' || *at == '\\') out += '\\';
        out += *at;
    }
    out += '"';
}

/** Writes [expression] the way the Kotlin side reads it (DjvuExpressions.kt): lists, symbols, numbers, strings. */
void write(std::string &out, miniexp_t expression) {
    if (miniexp_stringp(expression)) {
        quote(out, miniexp_to_str(expression));
    } else if (miniexp_numberp(expression)) {
        out += std::to_string(miniexp_to_int(expression));
    } else if (miniexp_symbolp(expression)) {
        out += miniexp_to_name(expression);
    } else if (miniexp_consp(expression) || expression == miniexp_nil) {
        out += '(';
        bool first = true;
        miniexp_t rest = expression;
        for (; miniexp_consp(rest); rest = miniexp_cdr(rest)) {
            if (!first) out += ' ';
            write(out, miniexp_car(rest));
            first = false;
        }
        if (rest != miniexp_nil) {  // A dotted pair's tail is read as one more item.
            out += ' ';
            write(out, rest);
        }
        out += ')';
    }
}

std::string written(Document *doc, miniexp_t expression) {
    std::string out;
    if (expression != miniexp_nil && miniexp_consp(expression)) write(out, expression);
    ddjvu_miniexp_release(doc->document, expression);
    return out;
}

}  // namespace

Document *open(const char *path, std::string &error) {
    auto *doc = new Document();
    doc->context = ddjvu_context_create("audora");
    if (doc->context == nullptr) {
        error = "no DjVu context";
        delete doc;
        return nullptr;
    }
    doc->document = ddjvu_document_create_by_filename_utf8(doc->context, path, 1);
    if (doc->document != nullptr) {
        while (!ddjvu_document_decoding_done(doc->document)) drain(doc, true);
        drain(doc, false);
    }
    const ddjvu_document_type_t type =
        doc->document == nullptr ? DDJVU_DOCTYPE_UNKNOWN : ddjvu_document_get_type(doc->document);
    if (doc->document == nullptr || ddjvu_document_decoding_error(doc->document)) {
        error = doc->lastError.empty() ? "not a readable DjVu" : doc->lastError;
    } else if (type == DDJVU_DOCTYPE_INDIRECT || type == DDJVU_DOCTYPE_OLD_INDEXED) {
        // Its pages are separate files beside it, which an app given one file cannot reach.
        error = "an indirect DjVu (pages in separate files)";
    } else {
        return doc;
    }
    close(doc);
    return nullptr;
}

void close(Document *doc) {
    if (doc == nullptr) return;
    if (doc->document != nullptr) ddjvu_document_release(doc->document);
    if (doc->context != nullptr) ddjvu_context_release(doc->context);
    delete doc;
}

int pageCount(Document *doc) { return ddjvu_document_get_pagenum(doc->document); }

std::string pageText(Document *doc, int page) {
    return written(doc, await(doc, [&] { return ddjvu_document_get_pagetext(doc->document, page, nullptr); }));
}

std::string outline(Document *doc) {
    return written(doc, await(doc, [&] { return ddjvu_document_get_outline(doc->document); }));
}

std::string annotations(Document *doc) {
    return written(doc, await(doc, [&] { return ddjvu_document_get_anno(doc->document, 1); }));
}

bool pageSize(Document *doc, int page, int &width, int &height) {
    ddjvu_pageinfo_t info;
    ddjvu_status_t status;
    while ((status = ddjvu_document_get_pageinfo(doc->document, page, &info)) < DDJVU_JOB_OK) drain(doc, true);
    if (status != DDJVU_JOB_OK) return false;
    width = info.width;
    height = info.height;
    return width > 0 && height > 0;
}

bool render(Document *doc, int page, int width, int height, uint8_t *pixels, int stride) {
    ddjvu_page_t *djvuPage = ddjvu_page_create_by_pageno(doc->document, page);
    if (djvuPage == nullptr) return false;
    while (!ddjvu_page_decoding_done(djvuPage)) drain(doc, true);
    bool drawn = false;
    if (!ddjvu_page_decoding_error(djvuPage)) {
        // R, G, B then A in memory (a little-endian word 0xAABBGGRR); the fourth value is xor'ed in: opaque alpha.
        unsigned int masks[4] = {0x000000ffu, 0x0000ff00u, 0x00ff0000u, 0xff000000u};
        ddjvu_format_t *format = ddjvu_format_create(DDJVU_FORMAT_RGBMASK32, 4, masks);
        ddjvu_format_set_row_order(format, 1);
        ddjvu_rect_t whole = {0, 0, static_cast<unsigned int>(width), static_cast<unsigned int>(height)};
        if (!ddjvu_page_render(djvuPage, DDJVU_RENDER_COLOR, &whole, &whole, format, stride,
                               reinterpret_cast<char *>(pixels))) {
            // Nothing to draw (an empty page): white.
            for (int row = 0; row < height; ++row) {
                uint8_t *line = pixels + static_cast<ptrdiff_t>(row) * stride;
                for (int at = 0; at < width * 4; ++at) line[at] = 0xff;
            }
        }
        ddjvu_format_release(format);
        drawn = true;
    }
    ddjvu_page_release(djvuPage);
    return drawn;
}

}  // namespace audora::djvu
