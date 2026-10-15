// File: pdf/src/main/cpp/jni_bridge.cpp
// Purpose: JNI entry points for PdfiumExtNative.kt: resolve libpdfium.so once, then read a page per call.

#include <dlfcn.h>
#include <jni.h>

#include <mutex>
#include <string>

#include "page_reader.h"
#include "pdfium_api.h"

namespace {

using audora::CharData;
using audora::PdfiumApi;
using audora::TextObjectData;

std::once_flag gInitOnce;
PdfiumApi gApi{};
std::string gInitError = "not initialized";

// Slot order of the array nativeReadPage returns. Must match PdfiumExtNative.kt.
enum Slot : jsize {
    kCodePoints = 0,
    kGenerated,
    kMapError,
    kRenderMode,
    kBoxes,
    kFontSizes,
    kFontIds,
    kFontNames,
    kObjectTexts,
    kObjectActualTexts,
    kObjectSpanKeys,
    kObjectWidths,
    kSlotCount,
};

void Initialize() {
    // PdfiumAndroidKt has already loaded libpdfium.so into this namespace, so this returns that copy.
    void* handle = dlopen("libpdfium.so", RTLD_NOW);
    if (handle == nullptr) {
        const char* error = dlerror();
        gInitError = std::string("dlopen libpdfium.so failed: ") + (error != nullptr ? error : "unknown");
        return;
    }
    const char* missing = audora::LoadPdfiumApi(handle, &gApi);
    gInitError = missing == nullptr ? "" : std::string("libpdfium.so lacks ") + missing;
}

bool Put(JNIEnv* env, jobjectArray array, jsize slot, jobject value) {
    if (value == nullptr) return false;
    env->SetObjectArrayElement(array, slot, value);
    env->DeleteLocalRef(value);
    return !env->ExceptionCheck();
}

jintArray IntArray(JNIEnv* env, const std::vector<int32_t>& values) {
    jintArray array = env->NewIntArray(static_cast<jsize>(values.size()));
    if (array != nullptr) env->SetIntArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    return array;
}

jfloatArray FloatArray(JNIEnv* env, const std::vector<float>& values) {
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(values.size()));
    if (array != nullptr) env->SetFloatArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    return array;
}

jlongArray LongArray(JNIEnv* env, const std::vector<int64_t>& values) {
    jlongArray array = env->NewLongArray(static_cast<jsize>(values.size()));
    if (array != nullptr) {
        env->SetLongArrayRegion(array, 0, static_cast<jsize>(values.size()),
                                reinterpret_cast<const jlong*>(values.data()));
    }
    return array;
}

jbyteArray ByteArray(JNIEnv* env, const std::string& bytes) {
    jbyteArray array = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (array != nullptr) {
        env->SetByteArrayRegion(array, 0, static_cast<jsize>(bytes.size()),
                                reinterpret_cast<const jbyte*>(bytes.data()));
    }
    return array;
}

// byte[][]; entries where `present` is false stay null.
jobjectArray ByteArrays(JNIEnv* env, const std::vector<std::string>& values, const std::vector<bool>* present) {
    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == nullptr) return nullptr;
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(values.size()), byteArrayClass, nullptr);
    env->DeleteLocalRef(byteArrayClass);
    if (array == nullptr) return nullptr;
    for (size_t i = 0; i < values.size(); ++i) {
        if (present != nullptr && !(*present)[i]) continue;
        if (!Put(env, array, static_cast<jsize>(i), ByteArray(env, values[i]))) return nullptr;
    }
    return array;
}

jobjectArray Strings(JNIEnv* env, const std::vector<std::u16string>& values) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(values.size()), stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    if (array == nullptr) return nullptr;
    for (size_t i = 0; i < values.size(); ++i) {
        jstring s = env->NewString(reinterpret_cast<const jchar*>(values[i].data()),
                                   static_cast<jsize>(values[i].size()));
        if (!Put(env, array, static_cast<jsize>(i), s)) return nullptr;
    }
    return array;
}

void ThrowIllegalState(JNIEnv* env, const std::string& message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message.c_str());
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_skm0078_audora_pdf_pdfium_PdfiumExtNative_nativeInit(JNIEnv* env, jclass) {
    std::call_once(gInitOnce, Initialize);
    return gInitError.empty() ? nullptr : env->NewStringUTF(gInitError.c_str());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_skm0078_audora_pdf_pdfium_PdfiumExtNative_nativeReadPage(JNIEnv* env, jclass, jlong pagePtr,
                                                                          jlong textPagePtr, jint maxFormDepth) {
    if (!gInitError.empty()) {
        ThrowIllegalState(env, "PdfiumExtNative not initialized: " + gInitError);
        return nullptr;
    }
    auto page = reinterpret_cast<audora::FpdfPage>(static_cast<intptr_t>(pagePtr));
    auto textPage = reinterpret_cast<audora::FpdfTextPage>(static_cast<intptr_t>(textPagePtr));
    if (page == nullptr || textPage == nullptr) {
        ThrowIllegalState(env, "null page or text page pointer");
        return nullptr;
    }

    CharData chars;
    audora::ReadChars(gApi, textPage, &chars);
    TextObjectData objects;
    audora::ReadTextObjects(gApi, page, textPage, maxFormDepth, &objects);

    jclass objectClass = env->FindClass("java/lang/Object");
    if (objectClass == nullptr) return nullptr;
    jobjectArray result = env->NewObjectArray(kSlotCount, objectClass, nullptr);
    env->DeleteLocalRef(objectClass);
    if (result == nullptr) return nullptr;

    bool ok = Put(env, result, kCodePoints, IntArray(env, chars.codePoints)) &&
              Put(env, result, kGenerated, IntArray(env, chars.generated)) &&
              Put(env, result, kMapError, IntArray(env, chars.mapError)) &&
              Put(env, result, kRenderMode, IntArray(env, chars.renderMode)) &&
              Put(env, result, kBoxes, FloatArray(env, chars.boxes)) &&
              Put(env, result, kFontSizes, FloatArray(env, chars.fontSizes)) &&
              Put(env, result, kFontIds, IntArray(env, chars.fontIds)) &&
              Put(env, result, kFontNames, ByteArrays(env, chars.fontNames, nullptr)) &&
              Put(env, result, kObjectTexts, Strings(env, objects.texts)) &&
              Put(env, result, kObjectActualTexts, ByteArrays(env, objects.actualTexts, &objects.hasActualText)) &&
              Put(env, result, kObjectSpanKeys, LongArray(env, objects.spanKeys)) &&
              Put(env, result, kObjectWidths, FloatArray(env, objects.widths));
    // On failure a Java exception (usually OutOfMemoryError) is pending and propagates to Kotlin.
    return ok ? result : nullptr;
}
