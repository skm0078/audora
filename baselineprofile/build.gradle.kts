// File: baselineprofile/build.gradle.kts
// Purpose: Writes the app's Baseline Profile (ADR 2026-10-30-baseline-profile). A test APK that starts the app's
//          benchmark build on an emulator, walks first run, opens a PDF and turns pages, and records which code ran.
//          scripts/ci/baseline_profile.sh copies the result to app/src/main/baseline-prof.txt. Never shipped.

plugins {
    id("audora.android.test")
}

// The app build this drives: :app's "benchmark" build type (the dev build, optimised like release, for x86_64).
val targetBuildType = "benchmark"
val targetAppId = "io.github.skm0078.audora.bench"

android {
    namespace = "io.github.skm0078.audora.baselineprofile"
    targetProjectPath = ":app"
    // Macrobenchmark runs in its own process and drives the app from outside, as a launcher and a finger would.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["targetAppId"] = targetAppId
        // Profiles are recorded on CI's emulator; only timing on one would be misleading, and nothing here times.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
        manifestPlaceholders["targetAppId"] = targetAppId
    }

    buildTypes {
        create(targetBuildType) {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

androidComponents {
    // Only the benchmark variant has an app build to drive on an x86_64 emulator (debug and release are arm64 only).
    beforeVariants { it.enable = it.buildType == targetBuildType }
    onVariants { variant ->
        // The same small English PDF the release journey reads; one copy in the repo.
        variant.sources.assets?.addStaticSourceDirectory(
            rootProject.file("app/src/androidTest/assets/pdfs").path,
        )
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.junit)
}
