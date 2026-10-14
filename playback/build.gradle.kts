// File: playback/build.gradle.kts
// Purpose: PlaybackService, TtsDataSource, render queue, progress. Synthesis runs here only.

plugins {
    id("audora.android.library")
}

android {
    namespace = "io.github.skm0078.audora.playback"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":pdf"))
    implementation(project(":speech"))
}
