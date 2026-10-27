# File: playback/src/main/cpp/CMakeLists.txt
# Purpose: Builds libaudora_djvu.so: DjVuLibre 3.5.28's libdjvu (vendored in djvulibre/, GPL-2.0-or-later) and a small
#          bridge to it, so DjVu books are read (ADR 2026-10-27-djvu-books). The community app is GPL-3.0, which may
#          carry it; the paid Pro app must never use this library.
#
# djvu_bridge_check.cpp is not built here: it is the same bridge's computer-side check (see its header).

cmake_minimum_required(VERSION 3.22.1)
project(audora_djvu LANGUAGES CXX)

# DjVuLibre as it ships, compiled as C++11 (it uses `register`), its warnings left to upstream.
file(GLOB DJVULIBRE_SOURCES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/djvulibre/libdjvu/*.cpp")
add_library(djvulibre STATIC ${DJVULIBRE_SOURCES})
target_include_directories(djvulibre PUBLIC djvulibre PRIVATE djvulibre/libdjvu)
target_compile_definitions(djvulibre PRIVATE HAVE_CONFIG_H NDEBUG)
target_compile_options(djvulibre PRIVATE -std=gnu++11 -w -Os -fvisibility=hidden -ffunction-sections -fdata-sections
    -fexceptions -frtti)
set_target_properties(djvulibre PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(audora_djvu SHARED
    djvu_bridge.cpp
    djvu_jni.cpp
)

target_compile_features(audora_djvu PRIVATE cxx_std_17)
target_compile_options(audora_djvu PRIVATE -Wall -Wextra -Werror -fvisibility=hidden -fexceptions)
target_link_libraries(audora_djvu PRIVATE djvulibre jnigraphics)
# Unused DjVuLibre code (its encoders, PostScript output) is dropped. Every native library in the APK must be 16 KB
# aligned (AGENTS.md, scripts/check_16kb_alignment.py).
target_link_options(audora_djvu PRIVATE "-Wl,--gc-sections" "-Wl,-z,max-page-size=16384")
