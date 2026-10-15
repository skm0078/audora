// File: app/build.gradle.kts
// Purpose: The Audora APK: Compose UI, navigation, dependency injection. No business logic here.

plugins {
    id("audora.android.application")
    id("audora.hilt")
}

val releaseVersionName: String = providers.gradleProperty("audora.versionName").get()

// Release signing (P0-02, docs/RELEASING.md). Only the release workflow sets these; locally and on
// ordinary CI runs they are absent and the release APK stays unsigned. The keystore never enters the repo.
val releaseKeystorePath: String? = providers.environmentVariable("AUDORA_KEYSTORE_PATH").orNull
val releaseSigningConfigured = !releaseKeystorePath.isNullOrBlank()

android {
    namespace = "io.github.skm0078.audora"

    defaultConfig {
        // D7: permanent once installed or registered for developer verification.
        applicationId = "io.github.skm0078.audora"
        versionCode = versionCodeOf(releaseVersionName)
        versionName = releaseVersionName
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storeType = "PKCS12"
                storePassword = requiredEnv("AUDORA_KEYSTORE_PASSWORD")
                keyAlias = requiredEnv("AUDORA_KEY_ALIAS")
                keyPassword = requiredEnv("AUDORA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to a release build on the same phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

/**
 * MAJOR.MINOR.PATCH → MAJOR * 1_000_000 + MINOR * 1_000 + PATCH, so every newer release has a higher
 * versionCode (Android refuses to update to a lower one) without a second number to remember to bump.
 */
fun versionCodeOf(name: String): Int {
    val parts = name.split(".").map { it.toIntOrNull() }
    require(parts.size == 3 && parts.all { it != null && it in 0..999 }) {
        "audora.versionName must be MAJOR.MINOR.PATCH with each part 0-999, got '$name'"
    }
    val (major, minor, patch) = parts.map { requireNotNull(it) }
    return major * 1_000_000 + minor * 1_000 + patch
}

fun requiredEnv(name: String): String = providers.environmentVariable(name).orNull
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("$name must be set when AUDORA_KEYSTORE_PATH is set (docs/RELEASING.md)")

dependencies {
    implementation(project(":core"))
    implementation(project(":pdf"))
    implementation(project(":playback"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    // The reader keeps the listener's speed in DataStore (P1-05).
    implementation(libs.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlinx.coroutines.test)
}
