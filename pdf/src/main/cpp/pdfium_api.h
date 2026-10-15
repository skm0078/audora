// File: pdf/src/main/cpp/pdfium_api.h
// Purpose: The FPDF* functions this library calls, resolved at runtime from PdfiumAndroidKt's libpdfium.so.

#pragma once

#include <cstdint>

namespace audora {

// Opaque PDFium handles (fpdfview.h declares them as pointers to incomplete structs).
using FpdfPage = void*;
using FpdfTextPage = void*;
using FpdfPageObject = void*;
using FpdfPageObjectMark = void*;

// fpdfview.h FS_RECTF: left, top, right, bottom in page space.
struct FsRectF {
    float left;
    float top;
    float right;
    float bottom;
};

constexpr int kPageObjText = 1;  // FPDF_PAGEOBJ_TEXT
constexpr int kPageObjForm = 5;  // FPDF_PAGEOBJ_FORM

// Signatures copied from PDFium's public headers (fpdf_text.h, fpdf_edit.h). FPDF_BOOL is int.
struct PdfiumApi {
    int (*textCountChars)(FpdfTextPage);
    unsigned int (*textGetUnicode)(FpdfTextPage, int);
    int (*textIsGenerated)(FpdfTextPage, int);
    int (*textHasUnicodeMapError)(FpdfTextPage, int);
    double (*textGetFontSize)(FpdfTextPage, int);
    unsigned long (*textGetFontInfo)(FpdfTextPage, int, void*, unsigned long, int*);
    int (*textGetLooseCharBox)(FpdfTextPage, int, FsRectF*);
    // Optional: older or newer builds may not export it; then render modes read as unknown.
    int (*textGetTextRenderMode)(FpdfTextPage, int);

    int (*pageCountObjects)(FpdfPage);
    FpdfPageObject (*pageGetObject)(FpdfPage, int);
    int (*formObjCountObjects)(FpdfPageObject);
    FpdfPageObject (*formObjGetObject)(FpdfPageObject, unsigned long);
    int (*pageObjGetType)(FpdfPageObject);
    int (*pageObjGetBounds)(FpdfPageObject, float*, float*, float*, float*);
    int (*pageObjCountMarks)(FpdfPageObject);
    FpdfPageObjectMark (*pageObjGetMark)(FpdfPageObject, unsigned long);
    int (*markGetParamBlobValue)(FpdfPageObjectMark, const char*, unsigned char*, unsigned long, unsigned long*);
    unsigned long (*textObjGetText)(FpdfPageObject, FpdfTextPage, unsigned short*, unsigned long);
};

// Fills `api` from a dlopen() handle. Returns nullptr on success, else the name of the first missing
// required symbol.
const char* LoadPdfiumApi(void* libraryHandle, PdfiumApi* api);

}  // namespace audora
