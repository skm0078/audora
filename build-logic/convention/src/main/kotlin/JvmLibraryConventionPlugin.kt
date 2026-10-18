// File: build-logic/convention/src/main/kotlin/JvmLibraryConventionPlugin.kt
// Purpose: Shared setup for pure-Kotlin modules (:core) so their logic is testable on the JVM.

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        // Android lint on plain Kotlin too, so :app's lint (checkDependencies) flags API calls above minSdk here.
        pluginManager.apply("com.android.lint")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(kotlinJvmTarget)
                // Compile against the JDK API of the target, not the JDK running Gradle (21): otherwise calls such as
                // MutableList.removeFirst() bind to java.util.List members that Android has only from API 35, and
                // crash on Android 12-14. Lint cannot see this module, so the compiler must.
                freeCompilerArgs.add("-Xjdk-release=${libs.intVersion("jvmTarget")}")
            }
        }

        dependencies {
            add("testImplementation", libs.library("junit"))
        }
    }
}
