// File: spikes/voices/build.gradle.kts
// Purpose: S1 voice-benchmark spike: a standalone debug APK that downloads the candidate voices, measures load
//          time, first-sentence latency and RTF with sherpa-onnx on the phone, and runs a blind listening test.
//          Not shipped; P1-03 and P3-06 take the chosen voices from its report (ADR-012).

import java.security.MessageDigest

plugins {
    id("audora.android.application")
}

android {
    namespace = "io.github.skm0078.audora.spike.voices"

    defaultConfig {
        // Its own app id, so it installs next to Audora and the S3 spike.
        applicationId = "io.github.skm0078.audora.spike.voices"
        versionCode = 1
        versionName = "0.0.1-s1"
    }

    packaging {
        jniLibs {
            // The Kotlin API only loads libsherpa-onnx-jni.so (+ libonnxruntime.so). The C and C++ API libraries
            // ship in the AAR for other bindings; dropping them saves ~5 MB.
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }
}

// sherpa-onnx publishes its AAR only as a GitHub release asset (resolved via the ivy repository in
// settings.gradle.kts), so its checksum is pinned here and checked before every build.
val sherpaOnnxAar: Configuration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    implementation(project(":core"))

    implementation(variantOf(libs.sherpa.onnx) { artifactType("aar") })
    sherpaOnnxAar(variantOf(libs.sherpa.onnx) { artifactType("aar") })

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}

val verifySherpaOnnxAar by tasks.registering {
    description = "Fails the build if the downloaded sherpa-onnx AAR does not match the pinned SHA-256."
    val aar: FileCollection = sherpaOnnxAar
    val expected = libs.versions.sherpaOnnxAarSha256.get()
    inputs.files(aar)
    doLast {
        val file = aar.singleFile
        val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
            "%02x".format(it)
        }
        check(actual == expected) { "sherpa-onnx AAR ${file.name} has SHA-256 $actual, expected $expected" }
    }
}

tasks.named("preBuild") {
    dependsOn(verifySherpaOnnxAar)
}
