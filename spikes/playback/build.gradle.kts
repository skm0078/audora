// File: spikes/playback/build.gradle.kts
// Purpose: S3 background-playback spike: a standalone debug APK that proves 60 minutes of screen-off playback
//          with a blocking TtsDataSource and a deliberately slow fake engine. Not shipped; P1-04 productionizes it.

plugins {
    id("audora.android.application")
}

android {
    namespace = "io.github.skm0078.audora.spike.playback"

    defaultConfig {
        // Its own app id, so it installs next to Audora and has its own battery settings.
        applicationId = "io.github.skm0078.audora.spike.playback"
        versionCode = 1
        versionName = "0.0.1-s3"
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}
