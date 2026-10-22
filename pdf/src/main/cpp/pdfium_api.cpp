// File: pdf/src/main/cpp/pdfium_api.cpp
// Purpose: Resolves the FPDF* function table with dlsym.

#include "pdfium_api.h"

#include <dlfcn.h>

namespace audora {
namespace {

template <typename Fn>
bool Resolve(void* handle, const char* name, Fn* out) {
    *out = reinterpret_cast<Fn>(dlsym(handle, name));
    return *out != nullptr;
}

}  // namespace

const char* LoadPdfiumApi(void* handle, PdfiumApi* api) {
#define AUDORA_REQUIRE(field, symbol) \
    if (!Resolve(handle, symbol, &api->field)) return symbol
    AUDORA_REQUIRE(textCountChars, "FPDFText_CountChars");
    AUDORA_REQUIRE(textGetUnicode, "FPDFText_GetUnicode");
    AUDORA_REQUIRE(textIsGenerated, "FPDFText_IsGenerated");
    AUDORA_REQUIRE(textHasUnicodeMapError, "FPDFText_HasUnicodeMapError");
    AUDORA_REQUIRE(textGetFontSize, "FPDFText_GetFontSize");
    AUDORA_REQUIRE(textGetFontInfo, "FPDFText_GetFontInfo");
    AUDORA_REQUIRE(textGetLooseCharBox, "FPDFText_GetLooseCharBox");
    AUDORA_REQUIRE(pageCountObjects, "FPDFPage_CountObjects");
    AUDORA_REQUIRE(pageGetObject, "FPDFPage_GetObject");
    AUDORA_REQUIRE(formObjCountObjects, "FPDFFormObj_CountObjects");
    AUDORA_REQUIRE(formObjGetObject, "FPDFFormObj_GetObject");
    AUDORA_REQUIRE(pageObjGetType, "FPDFPageObj_GetType");
    AUDORA_REQUIRE(pageObjGetBounds, "FPDFPageObj_GetBounds");
    AUDORA_REQUIRE(pageObjCountMarks, "FPDFPageObj_CountMarks");
    AUDORA_REQUIRE(pageObjGetMark, "FPDFPageObj_GetMark");
    AUDORA_REQUIRE(markGetParamBlobValue, "FPDFPageObjMark_GetParamBlobValue");
    AUDORA_REQUIRE(textObjGetText, "FPDFTextObj_GetText");
#undef AUDORA_REQUIRE
    Resolve(handle, "FPDFText_GetTextRenderMode", &api->textGetTextRenderMode);
    Resolve(handle, "FPDF_GetPageLabel", &api->getPageLabel);
    return nullptr;
}

}  // namespace audora
