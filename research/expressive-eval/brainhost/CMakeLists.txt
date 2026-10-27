# File: research/expressive-eval/brainhost/CMakeLists.txt
# Purpose: Builds brainhost, the app's story brain code (speech/src/main/cpp/brain) on a PC, for llm_director.py
#          --brainhost (ADR 2026-10-25-story-brain). Point LLAMA_CPP_DIR at llama.cpp at the tag in gradle/libs.versions.toml:
#          cmake -B build -DLLAMA_CPP_DIR=/path/to/llama.cpp && cmake --build build -j
cmake_minimum_required(VERSION 3.22)
project(brainhost CXX C)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Release)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory(${LLAMA_CPP_DIR} llama)
set(BRAIN ${CMAKE_CURRENT_SOURCE_DIR}/../../../speech/src/main/cpp/brain)
add_executable(brainhost host_main.cpp ${BRAIN}/brain_core.cpp)
target_include_directories(brainhost PRIVATE ${BRAIN})
target_compile_options(brainhost PRIVATE -Wall -Wextra -Werror)
target_link_libraries(brainhost llama ggml)
