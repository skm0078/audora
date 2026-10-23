# File: speech/src/main/cpp/CMakeLists.txt
# Purpose: Builds libaudora_acted.so, the acted Hindi voice (Chatterbox) on ONNX Runtime's C API (ADR-059).
#
# The library does not link against ONNX Runtime: it looks up OrtGetApiBase with dlsym at runtime, in the
# libonnxruntime.so that sherpa-onnx's AAR already ships. So only the C API headers (third_party/onnxruntime) are
# needed at build time, and the APK carries exactly one ONNX Runtime.

cmake_minimum_required(VERSION 3.22.1)
project(audora_acted LANGUAGES CXX)

add_library(audora_acted SHARED
    acted_jni.cpp
    chatterbox.cpp
    ort_api.cpp
)

target_compile_features(audora_acted PRIVATE cxx_std_17)
target_compile_options(audora_acted PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
target_link_libraries(audora_acted PRIVATE dl)
# Every native library in the APK must be 16 KB aligned (AGENTS.md, scripts/check_16kb_alignment.py).
target_link_options(audora_acted PRIVATE "-Wl,-z,max-page-size=16384")
