// File: speech/src/main/cpp/ort_api.cpp
// Purpose: Looks up OrtGetApiBase with dlsym, once per process.

#include "ort_api.h"

#include <dlfcn.h>

#include <mutex>

namespace audora {
namespace {

std::once_flag gOnce;
const OrtApi* gApi = nullptr;
std::string gError;

void Load(const char* library) {
    void* handle = dlopen(library, RTLD_NOW);
    if (handle == nullptr) {
        const char* why = dlerror();
        gError = std::string("dlopen ") + library + " failed: " + (why != nullptr ? why : "unknown");
        return;
    }
    using GetApiBase = const OrtApiBase* (*)();
    auto get_base = reinterpret_cast<GetApiBase>(dlsym(handle, "OrtGetApiBase"));
    if (get_base == nullptr) {
        gError = std::string(library) + " lacks OrtGetApiBase";
        return;
    }
    const OrtApiBase* base = get_base();
    gApi = base->GetApi(kOrtApiVersion);
    if (gApi == nullptr) {
        gError = std::string("ONNX Runtime ") + base->GetVersionString() + " does not offer API version " +
                 std::to_string(kOrtApiVersion);
    }
}

}  // namespace

const OrtApi* LoadOrtApi(const char* library, std::string* error) {
    std::call_once(gOnce, Load, library);
    if (gApi == nullptr) *error = gError;
    return gApi;
}

std::string TakeError(const OrtApi* api, OrtStatus* status) {
    if (status == nullptr) return "";
    std::string message = api->GetErrorMessage(status);
    api->ReleaseStatus(status);
    return message.empty() ? "unknown ONNX Runtime error" : message;
}

}  // namespace audora
