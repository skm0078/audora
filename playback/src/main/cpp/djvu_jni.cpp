// File: playback/src/main/cpp/djvu_jni.cpp
// Purpose: JNI for io.github.skm0078.audora.playback.djvu.DjvuNative over djvu_bridge.h (ADR 2026-10-27-djvu-books).
//          Text crosses as UTF-8 bytes, not jstring: NewStringUTF wants modified UTF-8, which a page's emoji or
//          supplementary characters would break.

#include <android/bitmap.h>
#include <jni.h>

#include <string>

#include "djvu_bridge.h"

using audora::djvu::Document;

namespace {

Document *asDocument(jlong handle) { return reinterpret_cast<Document *>(handle); }

jbyteArray bytes(JNIEnv *env, const std::string &text) {
    jbyteArray array = env->NewByteArray(static_cast<jsize>(text.size()));
    if (array != nullptr && !text.empty()) {
        env->SetByteArrayRegion(array, 0, static_cast<jsize>(text.size()),
                                reinterpret_cast<const jbyte *>(text.data()));
    }
    return array;
}

/** A DjvuException (an IOException) whose message is kept to ASCII, so a file name in DjVuLibre's error is safe. */
void throwDjvu(JNIEnv *env, const std::string &message) {
    std::string ascii;
    for (char ch : message) ascii += (static_cast<unsigned char>(ch) < 0x80 && ch != '\0') ? ch : '?';
    jclass type = env->FindClass("io/github/skm0078/audora/playback/djvu/DjvuException");
    if (type != nullptr) env->ThrowNew(type, ascii.c_str());
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_io_github_skm0078_audora_playback_djvu_DjvuNative_open(JNIEnv *env, jobject,
                                                                                    jstring path) {
    const char *utf = env->GetStringUTFChars(path, nullptr);
    if (utf == nullptr) return 0;
    std::string error;
    Document *document = audora::djvu::open(utf, error);
    env->ReleaseStringUTFChars(path, utf);
    if (document == nullptr) throwDjvu(env, error);
    return reinterpret_cast<jlong>(document);
}

JNIEXPORT void JNICALL Java_io_github_skm0078_audora_playback_djvu_DjvuNative_close(JNIEnv *, jobject,
                                                                                   jlong handle) {
    audora::djvu::close(asDocument(handle));
}

JNIEXPORT jint JNICALL Java_io_github_skm0078_audora_playback_djvu_DjvuNative_pageCount(JNIEnv *, jobject,
                                                                                       jlong handle) {
    return audora::djvu::pageCount(asDocument(handle));
}

JNIEXPORT jbyteArray JNICALL Java_io_github_skm0078_audora_playback_djvu_DjvuNative_pageText(JNIEnv *env, jobject,
                                                                                            jlong handle,
                                                                                            jint page) {
    return bytes(env, audora::djvu::pageText(asDocument(handle), page));
}

JNIEXPORT jbyteArray JNICALL Java_io_github_skm0078_audora_playback_djvu_DjvuNative_outline(JNIEnv *env, jobject,
                                                                                           jlong handle) {
    return bytes(env, audora::djvu::outline(asDocument(handle)));
}

JNIEXPORT jbyteArray JNICALL Java_io_github_skm0078_audora_playback_djvu_DjvuNative_annotations(JNIEnv *env,
                                                                                               jobject,
                                                                                               jlong handle) {
    return bytes(env, audora::djvu::annotations(asDocument(handle)));
}

JNIEXPORT jintArray JNICALL Java_io_github_skm0078_audora_playback_djvu_DjvuNative_pageSize(JNIEnv *env, jobject,
                                                                                           jlong handle,
                                                                                           jint page) {
    int width = 0;
    int height = 0;
    if (!audora::djvu::pageSize(asDocument(handle), page, width, height)) return nullptr;
    jintArray size = env->NewIntArray(2);
    if (size != nullptr) {
        const jint values[2] = {width, height};
        env->SetIntArrayRegion(size, 0, 2, values);
    }
    return size;
}

JNIEXPORT jboolean JNICALL Java_io_github_skm0078_audora_playback_djvu_DjvuNative_render(JNIEnv *env, jobject,
                                                                                        jlong handle, jint page,
                                                                                        jobject bitmap) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return JNI_FALSE;
    }
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    const bool drawn = audora::djvu::render(asDocument(handle), page, static_cast<int>(info.width),
                                            static_cast<int>(info.height), static_cast<uint8_t *>(pixels),
                                            static_cast<int>(info.stride));
    AndroidBitmap_unlockPixels(env, bitmap);
    return drawn ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
