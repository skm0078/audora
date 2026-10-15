// File: build-logic/convention/src/main/kotlin/ProjectExtensions.kt
// Purpose: Typed access to the shared version catalog from convention plugins.

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.intVersion(alias: String): Int = findVersion(alias)
    .orElseThrow { IllegalStateException("Version '$alias' missing from gradle/libs.versions.toml") }
    .requiredVersion
    .toInt()

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias)
    .orElseThrow { IllegalStateException("Library '$alias' missing from gradle/libs.versions.toml") }

internal val Project.javaVersion: JavaVersion
    get() = JavaVersion.toVersion(libs.intVersion("jvmTarget"))

internal val Project.kotlinJvmTarget: JvmTarget
    get() = JvmTarget.fromTarget(libs.intVersion("jvmTarget").toString())
