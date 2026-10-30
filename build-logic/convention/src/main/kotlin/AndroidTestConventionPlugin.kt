// File: build-logic/convention/src/main/kotlin/AndroidTestConventionPlugin.kt
// Purpose: Shared setup for test-only modules that drive the app from outside (:baselineprofile): SDK levels and Java
//          version, matching :app. Such a module builds a test APK only; nothing in it ships.

import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 compiles Kotlin itself (built-in Kotlin); no separate kotlin-android plugin.
        pluginManager.apply("com.android.test")

        extensions.configure<TestExtension> {
            compileSdk = libs.intVersion("compileSdk")
            defaultConfig {
                minSdk = libs.intVersion("minSdk")
                targetSdk = libs.intVersion("targetSdk")
            }
            compileOptions {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }
        }
    }
}
