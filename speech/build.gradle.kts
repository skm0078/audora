// File: speech/build.gradle.kts
// Purpose: Normalization, script routing, SpeechEngine implementations, model manager.

plugins {
    id("audora.android.library")
}

android {
    namespace = "io.github.skm0078.audora.speech"
}

dependencies {
    implementation(project(":core"))
}
