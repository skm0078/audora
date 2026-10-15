// File: build-logic/convention/src/main/kotlin/RoomConventionPlugin.kt
// Purpose: Adds Room (with KSP) to an Android module and exports its schema for migration tests.

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<KspExtension> {
            // Committed: every schema version is kept so later tickets can test migrations from it.
            // CI fails if a build changes or adds a schema file that is not committed.
            arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
            arg("room.generateKotlin", "true")
        }

        dependencies {
            add("implementation", libs.library("room-runtime"))
            add("implementation", libs.library("room-ktx"))
            add("ksp", libs.library("room-compiler"))
        }
    }
}
