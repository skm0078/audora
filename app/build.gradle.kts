// File: app/build.gradle.kts
// Purpose: The Audora APK: Compose UI, navigation, dependency injection. No business logic here.

plugins {
    id("audora.android.application")
    id("audora.hilt")
}

val uiTestBuildType = "uiTest"

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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    androidResources {
        // The bundled voice's weights (:speech, ADR-014) are read straight from the APK; stored uncompressed they
        // load without inflating ~60 MB first.
        noCompress += "onnx"
    }

    packaging {
        jniLibs {
            // sherpa-onnx's Kotlin API loads only libsherpa-onnx-jni.so and libonnxruntime.so (ADR-012).
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
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
        // Only for the Compose UI tests on CI's x86_64 emulator, which cannot run arm64-only native code. Debug and
        // release stay arm64-v8a only (ADR-010); this variant is never uploaded, published or 16 KB checked.
        create(uiTestBuildType) {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            matchingFallbacks += "debug"
            ndk { abiFilters += "x86_64" }
        }
    }

    testBuildType = uiTestBuildType
}

// The UI-test variant's unit tests are the debug ones again; skip the second run.
tasks.matching { it.name == "testUiTestUnitTest" }.configureEach { enabled = false }

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
    // Settings → Voices talks to the voice manager directly (P4-01); still app → speech, never the reverse.
    implementation(project(":speech"))

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

    // Compose UI tests of the main flows on the CI emulator, hosted in a plain ComponentActivity with fakes.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    // Declares the ComponentActivity the tests host; in the UI-test variant only, so no shipped APK carries it.
    "uiTestImplementation"(libs.compose.ui.test.manifest)
}
