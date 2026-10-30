// File: app/build.gradle.kts
// Purpose: The Audora APK: Compose UI, navigation, dependency injection. No business logic here.

plugins {
    id("audora.android.application")
    id("audora.hilt")
}

val uiTestBuildType = "uiTest"
val devBuildType = "dev"
val benchmarkBuildType = "benchmark"

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

    bundle {
        // Settings can switch the app to Hindi or Bengali whatever the phone's language is (ADR-036). Play's
        // language splits would install only the phone's languages, so the switch would show English.
        language { enableSplit = false }
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
            buildConfigField("boolean", "SIMPLE_VOICES", simpleVoices(release = false))
            buildConfigField("boolean", "DEV_TOOLS", "true")
        }
        release {
            // R8 shrinks and optimises the code (ADR-072). Names are kept (-dontobfuscate), so shared logs and crash
            // stacks read the same as in a debug build.
            isMinifyEnabled = true
            // Drops images, layouts and other resources no code or manifest reaches (ADR 2026-10-29-speed-quick-wins).
            // Nothing looks a resource up by name at run time, which is the one thing this would break.
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The Play build lists one download per language and hides voices still being judged (ADR-056).
            buildConfigField("boolean", "SIMPLE_VOICES", simpleVoices(release = true))
            buildConfigField("boolean", "DEV_TOOLS", "false")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // The dev APK the phones run (ADR-072): built like release (not debuggable, R8, optimised native code), so
        // what is tested is as fast as what ships, but signed with the dev key under the debug build's application
        // id, so it updates the installed dev app in place. Every voice and the debug tools stay on.
        create(devBuildType) {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("boolean", "SIMPLE_VOICES", simpleVoices(release = false))
            buildConfigField("boolean", "DEV_TOOLS", "true")
        }
        // The dev build again for CI's x86_64 emulator, only to time it against the debug build (scripts/perf/).
        // Never uploaded, published or 16 KB checked, like uiTest.
        create(benchmarkBuildType) {
            initWith(getByName(devBuildType))
            applicationIdSuffix = ".bench"
            matchingFallbacks += "release"
            // The Baseline Profile generator finds buttons by their string's name (getIdentifier), which shrinking
            // takes out of the APK. Code, and so the recorded profile, is the same either way.
            isShrinkResources = false
            ndk { abiFilters += "x86_64" }
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

/** `-Paudora.simpleVoices=true|false` overrides the build type's default: simple in release, everything in debug. */
fun simpleVoices(release: Boolean): String =
    (providers.gradleProperty("audora.simpleVoices").orNull?.toBooleanStrict() ?: release).toString()

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
    testImplementation(testFixtures(project(":core")))

    // Compose UI tests of the main flows on the CI emulator, hosted in a plain ComponentActivity with fakes.
    androidTestImplementation(testFixtures(project(":core")))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    // Declares the ComponentActivity the tests host; in the UI-test variant only, so no shipped APK carries it.
    "uiTestImplementation"(libs.compose.ui.test.manifest)
}

// The in-app ListenLock Cookbook (ADR 2026-10-29-in-app-cookbook): the site's recipes, one Markdown book per language.
val cookbookAssets = tasks.register<CookbookAssetsTask>("cookbookAssets") {
    recipes.set(rootProject.layout.projectDirectory.dir("docs/_recipes"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(cookbookAssets, CookbookAssetsTask::outputDirectory)
    }
}
