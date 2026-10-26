# File: speech/src/main/cpp/brain/CMakeLists.txt
# Purpose: Builds libaudora_brain.so, the story brain (ADR 2026-10-25-story-brain), on llama.cpp from the pinned source :speech unpacks
#          (LLAMA_CPP_DIR, from the llamaCppSource task).
#
# llama.cpp is built as shared libraries with every Android CPU variant of ggml (GGML_CPU_ALL_VARIANTS): at load
# ggml picks the best one the phone's cores can run (dot product, int8 matrix maths, SVE), so an older core never
# meets an instruction it lacks. Always optimised, a debug build too: unoptimised it would be too slow to try.
# Variables set here stay in this directory, so the acted voice's library keeps its own flags.

set(CMAKE_C_FLAGS_DEBUG "-O2 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O2 -DNDEBUG")
set(BUILD_SHARED_LIBS ON)
set(GGML_NATIVE OFF)
set(GGML_BACKEND_DL ON)
set(GGML_CPU_ALL_VARIANTS ON)
set(GGML_SYSTEM_ARCH "ARM")
set(GGML_CPU_KLEIDIAI OFF)
set(GGML_OPENMP OFF)
set(GGML_LLAMAFILE OFF)
set(LLAMA_BUILD_COMMON OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_TOOLS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_APP OFF)
set(LLAMA_OPENSSL OFF)
# Every native library in the APK must be 16 KB aligned (AGENTS.md, scripts/check_16kb_alignment.py).
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

enable_language(C)
add_subdirectory(${LLAMA_CPP_DIR} llama)

add_library(audora_brain SHARED brain_jni.cpp brain_core.cpp)
target_compile_features(audora_brain PRIVATE cxx_std_17)
target_compile_options(audora_brain PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
# ggml depends on every CPU variant, so they build (and land in the APK) with it; brain_core picks one at run time.
target_link_libraries(audora_brain PRIVATE llama ggml log dl)
