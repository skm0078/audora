// File: build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt
// Purpose: Shared setup for the :app module: SDK levels, arm64-only ABI (D5), Compose.

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.intVersion("compileSdk")
            defaultConfig {
                minSdk = libs.intVersion("minSdk")
                targetSdk = libs.intVersion("targetSdk")
                ndk {
                    // D5: arm64-v8a only. Keeps the APK small and the 16 KB check meaningful.
                    abiFilters += ARM64_ABI
                }
            }
            compileOptions {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }
            buildFeatures {
                compose = true
                buildConfig = true
            }
            lint {
                // One run covers every module the app depends on, :core included, so NewApi checks their calls
                // against the app's minSdk (the Android 12 URLEncoder crash was a JVM-module call lint never saw).
                checkDependencies = true
                // Errors fail CI; warnings are listed in the report only.
                abortOnError = true
                warningsAsErrors = false
                textReport = true
                textOutput = file("build/reports/lint-results.txt")
            }
        }

        dependencies {
            add("testImplementation", libs.library("junit"))
        }
    }

    private companion object {
        const val ARM64_ABI = "arm64-v8a"
    }
}
