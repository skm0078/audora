// File: pdf/src/main/cpp/page_reader.cpp
// Purpose: Reads one page's characters and text objects from PDFium into plain vectors (no JNI here).

#include "page_reader.h"

#include <cmath>
#include <unordered_map>

namespace audora {
namespace {

constexpr char kActualTextKey[] = "ActualText";

int32_t FontIdOf(const PdfiumApi& api, FpdfTextPage textPage, int index, std::vector<char>* buffer,
                 std::unordered_map<std::string, int32_t>* ids, CharData* out) {
    int flags = 0;
    unsigned long needed = api.textGetFontInfo(textPage, index, nullptr, 0, &flags);
    if (needed == 0) return kUnknown;
    if (buffer->size() < needed) buffer->resize(needed);
    api.textGetFontInfo(textPage, index, buffer->data(), needed, &flags);
    std::string name(buffer->data(), needed - 1);  // drop the terminating NUL
    auto found = ids->find(name);
    if (found != ids->end()) return found->second;
    auto id = static_cast<int32_t>(out->fontNames.size());
    ids->emplace(name, id);
    out->fontNames.push_back(std::move(name));
    return id;
}

// The /ActualText of the object's outermost marked-content span that has one.
bool ActualTextOf(const PdfiumApi& api, FpdfPageObject object, std::string* text, int64_t* key) {
    int marks = api.pageObjCountMarks(object);
    for (int m = 0; m < marks; ++m) {
        FpdfPageObjectMark mark = api.pageObjGetMark(object, static_cast<unsigned long>(m));
        if (mark == nullptr) continue;
        unsigned long length = 0;
        if (!api.markGetParamBlobValue(mark, kActualTextKey, nullptr, 0, &length)) continue;
        text->assign(length, '\0');
        if (length > 0) {
            api.markGetParamBlobValue(mark, kActualTextKey, reinterpret_cast<unsigned char*>(text->data()),
                                      length, &length);
        }
        *key = static_cast<int64_t>(reinterpret_cast<intptr_t>(mark));
        return true;
    }
    return false;
}

std::u16string TextOf(const PdfiumApi& api, FpdfPageObject object, FpdfTextPage textPage) {
    unsigned long bytes = api.textObjGetText(object, textPage, nullptr, 0);
    if (bytes <= sizeof(char16_t)) return {};
    std::u16string text(bytes / sizeof(char16_t), u'\0');
    api.textObjGetText(object, textPage, reinterpret_cast<unsigned short*>(text.data()), bytes);
    text.resize(text.size() - 1);  // drop the terminating NUL
    return text;
}

void ReadTextObject(const PdfiumApi& api, FpdfPageObject object, FpdfTextPage textPage, TextObjectData* out) {
    float left = 0;
    float bottom = 0;
    float right = 0;
    float top = 0;
    bool hasBounds = api.pageObjGetBounds(object, &left, &bottom, &right, &top) != 0;
    std::string actual;
    int64_t key = 0;
    bool hasActual = ActualTextOf(api, object, &actual, &key);
    out->texts.push_back(TextOf(api, object, textPage));
    out->hasActualText.push_back(hasActual);
    out->actualTexts.push_back(std::move(actual));
    out->spanKeys.push_back(key);
    out->widths.push_back(hasBounds ? right - left : 0.0f);
}

void Walk(const PdfiumApi& api, FpdfPage page, FpdfPageObject form, FpdfTextPage textPage, int depth,
          int maxDepth, TextObjectData* out) {
    int count = form == nullptr ? api.pageCountObjects(page) : api.formObjCountObjects(form);
    for (int i = 0; i < count; ++i) {
        FpdfPageObject object = form == nullptr ? api.pageGetObject(page, i)
                                                : api.formObjGetObject(form, static_cast<unsigned long>(i));
        if (object == nullptr) continue;
        int type = api.pageObjGetType(object);
        if (type == kPageObjText) {
            ReadTextObject(api, object, textPage, out);
        } else if (type == kPageObjForm && depth < maxDepth) {
            Walk(api, page, object, textPage, depth + 1, maxDepth, out);
        }
    }
}

}  // namespace

void ReadChars(const PdfiumApi& api, FpdfTextPage textPage, CharData* out) {
    int count = api.textCountChars(textPage);
    if (count < 0) count = 0;
    auto n = static_cast<size_t>(count);
    out->codePoints.reserve(n);
    out->generated.reserve(n);
    out->mapError.reserve(n);
    out->renderMode.reserve(n);
    out->boxes.reserve(n * 4);
    out->fontSizes.reserve(n);
    out->fontIds.reserve(n);
    std::vector<char> nameBuffer(64);
    std::unordered_map<std::string, int32_t> fontIds;
    for (int i = 0; i < count; ++i) {
        out->codePoints.push_back(static_cast<int32_t>(api.textGetUnicode(textPage, i)));
        out->generated.push_back(api.textIsGenerated(textPage, i));
        out->mapError.push_back(api.textHasUnicodeMapError(textPage, i));
        out->renderMode.push_back(api.textGetTextRenderMode != nullptr ? api.textGetTextRenderMode(textPage, i)
                                                                         : kUnknown);
        FsRectF box{};
        if (api.textGetLooseCharBox(textPage, i, &box)) {
            out->boxes.insert(out->boxes.end(), {box.left, box.top, box.right, box.bottom});
        } else {
            out->boxes.insert(out->boxes.end(), 4, NAN);
        }
        out->fontSizes.push_back(static_cast<float>(api.textGetFontSize(textPage, i)));
        out->fontIds.push_back(FontIdOf(api, textPage, i, &nameBuffer, &fontIds, out));
    }
}

void ReadTextObjects(const PdfiumApi& api, FpdfPage page, FpdfTextPage textPage, int maxFormDepth,
                     TextObjectData* out) {
    Walk(api, page, nullptr, textPage, 0, maxFormDepth, out);
}

}  // namespace audora
