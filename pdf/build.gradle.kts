// File: pdf/build.gradle.kts
// Purpose: PDF extraction pipeline: extract → quality → recover → layout → cleanup → ReadingUnit.

plugins {
    id("audora.android.library")
}

android {
    namespace = "io.github.skm0078.audora.pdf"
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // arm64-v8a ships (D5). x86_64 exists only so instrumented tests run on CI's emulator;
            // the app's own abiFilters keep it out of the APK.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.pdfium.android.core)
    implementation(libs.pdfium.android.api)
    // PdfiumAndroidKt's lock uses coroutines but its POM does not declare them.
    implementation(libs.kotlinx.coroutines.core)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
