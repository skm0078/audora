// File: build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt
// Purpose: Shared setup for Android library modules (:pdf, :speech, :playback).

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 compiles Kotlin itself (built-in Kotlin); no separate kotlin-android plugin.
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = libs.intVersion("compileSdk")
            defaultConfig {
                minSdk = libs.intVersion("minSdk")
            }
            compileOptions {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }
            lint {
                // TEMPORARY until the last landing (studio): the v0.1.0 gate makes this an error again.
                warning += "MissingTranslation"
            }
        }

        dependencies {
            add("testImplementation", libs.library("junit"))
        }
    }
}
