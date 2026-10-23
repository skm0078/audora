// File: speech/src/main/cpp/acted_jni.cpp
// Purpose: JNI entry points for ChatterboxNative.kt (ADR-059).

#include <jni.h>

#include <string>
#include <vector>

#include "chatterbox.h"
#include "ort_api.h"

namespace {

using audora::Chatterbox;

// sherpa-onnx's JNI library brings this runtime into the app; the same file serves both (README in third_party).
constexpr char kRuntime[] = "libonnxruntime.so";

void Throw(JNIEnv* env, const std::string& message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message.c_str());
}

Chatterbox* From(jlong handle) { return reinterpret_cast<Chatterbox*>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_io_github_skm0078_audora_speech_acted_ChatterboxNative_nativeLoad(
    JNIEnv* env, jclass, jstring dir, jint threads) {
    std::string error;
    const OrtApi* api = audora::LoadOrtApi(kRuntime, &error);
    if (api == nullptr) {
        Throw(env, error);
        return 0;
    }
    const char* chars = env->GetStringUTFChars(dir, nullptr);
    if (chars == nullptr) return 0;  // OutOfMemoryError pending
    std::string path(chars);
    env->ReleaseStringUTFChars(dir, chars);
    std::unique_ptr<Chatterbox> model = Chatterbox::Load(api, path, threads, &error);
    if (model == nullptr) {
        Throw(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(model.release());
}

// Slot 0 says how the speech ended (1 the model stopped, 0 it reached maxTokens); the speech tokens follow.
// Null when cancelled.
JNIEXPORT jlongArray JNICALL Java_io_github_skm0078_audora_speech_acted_ChatterboxNative_nativeSpeak(
    JNIEnv* env, jclass, jlong handle, jlongArray text_tokens, jfloat exaggeration, jint max_tokens,
    jfloat repetition_penalty) {
    jsize count = env->GetArrayLength(text_tokens);
    std::vector<int64_t> tokens(count);
    env->GetLongArrayRegion(text_tokens, 0, count, reinterpret_cast<jlong*>(tokens.data()));
    audora::Generation generated = From(handle)->Speak(tokens, exaggeration, max_tokens, repetition_penalty);
    if (generated.cancelled) return nullptr;
    if (!generated.error.empty()) {
        Throw(env, generated.error);
        return nullptr;
    }
    std::vector<jlong> out;
    out.reserve(generated.speech_tokens.size() + 1);
    out.push_back(generated.stopped ? 1 : 0);
    out.insert(out.end(), generated.speech_tokens.begin(), generated.speech_tokens.end());
    jlongArray array = env->NewLongArray(static_cast<jsize>(out.size()));
    if (array != nullptr) env->SetLongArrayRegion(array, 0, static_cast<jsize>(out.size()), out.data());
    return array;
}

JNIEXPORT jfloatArray JNICALL Java_io_github_skm0078_audora_speech_acted_ChatterboxNative_nativeDecode(
    JNIEnv* env, jclass, jlong handle, jlongArray speech_tokens) {
    jsize count = env->GetArrayLength(speech_tokens);
    std::vector<int64_t> tokens(count);
    env->GetLongArrayRegion(speech_tokens, 0, count, reinterpret_cast<jlong*>(tokens.data()));
    std::vector<float> audio;
    std::string error;
    if (!From(handle)->Decode(tokens, &audio, &error)) {
        Throw(env, error);
        return nullptr;
    }
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(audio.size()));
    if (array != nullptr) env->SetFloatArrayRegion(array, 0, static_cast<jsize>(audio.size()), audio.data());
    return array;
}

JNIEXPORT void JNICALL Java_io_github_skm0078_audora_speech_acted_ChatterboxNative_nativeCancel(JNIEnv*, jclass,
                                                                                               jlong handle) {
    From(handle)->Cancel();
}

JNIEXPORT void JNICALL Java_io_github_skm0078_audora_speech_acted_ChatterboxNative_nativeRelease(JNIEnv*, jclass,
                                                                                                jlong handle) {
    delete From(handle);
}

}  // extern "C"
