// File: playback/build.gradle.kts
// Purpose: PlaybackService, TtsDataSource, render queue, progress and the app's database. Synthesis runs here only.

plugins {
    id("audora.android.library")
    id("audora.room")
}

android {
    namespace = "io.github.skm0078.audora.playback"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":pdf"))
    implementation(project(":speech"))

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
