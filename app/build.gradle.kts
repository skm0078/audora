// File: app/build.gradle.kts
// Purpose: The Audora APK: Compose UI, navigation, dependency injection. No business logic here.

plugins {
    id("audora.android.application")
    id("audora.hilt")
}

android {
    namespace = "io.github.skm0078.audora"

    defaultConfig {
        // D7: permanent once installed or registered for developer verification.
        applicationId = "io.github.skm0078.audora"
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to a release build on the same phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":playback"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
