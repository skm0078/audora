// File: speech/src/main/cpp/brain/brain_jni.cpp
// Purpose: JNI entry points for StoryBrainNative.kt (ADR-078). Text crosses as UTF-8 bytes, not Java's modified UTF-8.

#include <jni.h>

#include <string>

#include "brain_core.h"

namespace {

using audora::Brain;

Brain* From(jlong handle) { return reinterpret_cast<Brain*>(handle); }

// [bytes] as a string; false with an OutOfMemoryError pending.
bool ReadBytes(JNIEnv* env, jbyteArray bytes, std::string* out) {
    const jsize length = env->GetArrayLength(bytes);
    out->resize(static_cast<size_t>(length));
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(out->data()));
    return !env->ExceptionCheck();
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL Java_io_github_skm0078_audora_speech_brain_StoryBrainNative_nativeInit(JNIEnv*, jclass) {
    return audora::initBackends() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_io_github_skm0078_audora_speech_brain_StoryBrainNative_nativeLoad(
    JNIEnv* env, jclass, jbyteArray path, jint contextTokens, jint threads) {
    std::string file;
    if (!ReadBytes(env, path, &file)) return 0;
    return reinterpret_cast<jlong>(Brain::load(file, contextTokens, threads));
}

// The answer's bytes, or null when it did not finish (cancelled, too long, or failed: the reason is in logcat).
JNIEXPORT jbyteArray JNICALL Java_io_github_skm0078_audora_speech_brain_StoryBrainNative_nativeAnswer(
    JNIEnv* env, jclass, jlong handle, jbyteArray prompt, jbyteArray grammar, jint maxTokens) {
    std::string question;
    std::string rules;
    if (!ReadBytes(env, prompt, &question) || !ReadBytes(env, grammar, &rules)) return nullptr;
    std::string answer;
    if (!From(handle)->answer(question, rules, maxTokens, answer)) return nullptr;
    jbyteArray out = env->NewByteArray(static_cast<jsize>(answer.size()));
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(answer.size()), reinterpret_cast<const jbyte*>(answer.data()));
    return out;
}

JNIEXPORT void JNICALL Java_io_github_skm0078_audora_speech_brain_StoryBrainNative_nativeCancel(
    JNIEnv*, jclass, jlong handle) {
    From(handle)->cancel();
}

JNIEXPORT void JNICALL Java_io_github_skm0078_audora_speech_brain_StoryBrainNative_nativeFree(
    JNIEnv*, jclass, jlong handle) {
    delete From(handle);
}

}  // extern "C"
