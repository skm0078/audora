// File: playback/build.gradle.kts
// Purpose: PlaybackService, TtsDataSource, render queue, progress and the app's database. Synthesis runs here only.

plugins {
    id("audora.android.library")
    id("audora.hilt")
    id("audora.room")
}

android {
    namespace = "io.github.skm0078.audora.playback"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        jniLibs {
            // sherpa-onnx (via :speech) ships C and C++ API libraries nobody loads (ADR-012); keeps the test APK lean.
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":pdf"))
    implementation(project(":speech"))

    // The playback contract (PlaybackContract) speaks Media3 types, so controllers get them through this module.
    api(libs.media3.session)
    implementation(libs.media3.exoplayer)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
