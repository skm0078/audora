# File: pdf/src/main/cpp/CMakeLists.txt
# Purpose: Builds libaudora_pdfium_ext.so, the small JNI library over PdfiumAndroidKt's libpdfium.so (ADR-011).
#
# The library does not link against libpdfium.so: it looks up the FPDF* functions with dlsym at runtime, from
# the copy PdfiumAndroidKt already loaded. So no PDFium headers or import library are needed at build time, and
# the APK carries exactly one PDFium.

cmake_minimum_required(VERSION 3.22.1)
project(audora_pdfium_ext LANGUAGES CXX)

add_library(audora_pdfium_ext SHARED
    jni_bridge.cpp
    page_reader.cpp
    pdfium_api.cpp
)

target_compile_features(audora_pdfium_ext PRIVATE cxx_std_17)
target_compile_options(audora_pdfium_ext PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
target_link_libraries(audora_pdfium_ext PRIVATE dl)
# Every native library in the APK must be 16 KB aligned (AGENTS.md, scripts/check_16kb_alignment.py).
target_link_options(audora_pdfium_ext PRIVATE "-Wl,-z,max-page-size=16384")
