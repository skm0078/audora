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

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(kotlinJvmTarget)
            }
        }

        dependencies {
            add("testImplementation", libs.library("junit"))
        }
    }
}
