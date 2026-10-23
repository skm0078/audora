// File: speech/src/main/cpp/ort_api.h
// Purpose: The ONNX Runtime C API, resolved at runtime from the libonnxruntime.so sherpa-onnx ships (ADR-059).

#pragma once

#include <string>

#include "third_party/onnxruntime/onnxruntime_c_api.h"

namespace audora {

// Every OrtApi function this library calls exists since API 17 (ONNX Runtime 1.17), so it asks for that version:
// a newer runtime answers older versions, an older one would refuse a newer request.
constexpr uint32_t kOrtApiVersion = 17;

// The process-wide OrtApi, or null with the reason in [error]. [library] is the file to dlopen; on Android
// "libonnxruntime.so", which sherpa-onnx's JNI library already loaded.
const OrtApi* LoadOrtApi(const char* library, std::string* error);

// The message of [status] (released here), or an empty string for success.
std::string TakeError(const OrtApi* api, OrtStatus* status);

}  // namespace audora
