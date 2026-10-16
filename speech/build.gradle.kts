// File: speech/build.gradle.kts
// Purpose: Normalization, script routing, SpeechEngine implementations, model manager.
//          Also packs the bundled English voice (ADR-014) and the downloadable-voice catalog (ADR-022) into the
//          module's assets at build time.

import java.security.MessageDigest

plugins {
    id("audora.android.library")
}

android {
    namespace = "io.github.skm0078.audora.speech"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            // arm64-v8a ships (D5). x86_64 only so the instrumented tests run on CI's emulator; the app's own
            // abiFilters keep it out of the APK.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    androidResources {
        // Model weights are read straight from the APK; stored uncompressed they load without inflating ~60 MB.
        noCompress += "onnx"
    }

    packaging {
        jniLibs {
            // The Kotlin API only loads libsherpa-onnx-jni.so (+ libonnxruntime.so); the C and C++ API libraries
            // ship in the AAR for other bindings (ADR-012). Applies to this module's test APK; :app repeats it.
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }
}

// The bundled voice: speech/bundled-voices.json names it and pins its archive's SHA-256. Downloads are cached in the
// Gradle user home, so only the first build (or a changed pin) fetches the ~65 MB archive.
val bundledVoiceAssets = tasks.register<BundledVoiceAssetsTask>("bundledVoiceAssets") {
    catalog.set(layout.projectDirectory.file("bundled-voices.json"))
    downloadDirectory.set(File(gradle.gradleUserHomeDir, "caches/audora-voices"))
}

// The downloadable voices (P4-01, ADR-022): speech/models.json, validated and packed as one properties file per voice.
val voiceCatalogAssets = tasks.register<VoiceCatalogAssetsTask>("voiceCatalogAssets") {
    catalog.set(layout.projectDirectory.file("models.json"))
    bundledCatalog.set(layout.projectDirectory.file("bundled-voices.json"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundledVoiceAssets, BundledVoiceAssetsTask::outputDirectory)
        variant.sources.assets?.addGeneratedSourceDirectory(voiceCatalogAssets, VoiceCatalogAssetsTask::outputDirectory)
    }
}

// sherpa-onnx publishes its AAR only as a GitHub release asset (resolved via the ivy repository in
// settings.gradle.kts), so its checksum is pinned and checked before every build (ADR-012).
val sherpaOnnxAar: Configuration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    implementation(project(":core"))

    implementation(variantOf(libs.sherpa.onnx) { artifactType("aar") })
    // Voice downloads only (AGENTS.md stack: WorkManager for downloads).
    implementation(libs.work.runtime.ktx)
    sherpaOnnxAar(variantOf(libs.sherpa.onnx) { artifactType("aar") })

    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
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
