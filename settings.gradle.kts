// File: settings.gradle.kts
// Purpose: Declares the Gradle modules and where plugins and dependencies are resolved from.

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack serves exactly two libraries, and they are looked up nowhere else. Tesseract4Android is published
        // only there (ADR-025). sherpa-onnx's JitPack build republishes its GitHub release AAR byte for byte, and
        // JitPack is a source F-Droid trusts while a GitHub release asset is not (ADR 2026-10-31-fdroid-ready-build).
        // Checksums: tesseract4androidAarSha256 and sherpaOnnxAarSha256 in libs.versions.toml.
        exclusiveContent {
            forRepository {
                maven {
                    name = "jitpack"
                    url = uri("https://jitpack.io")
                }
            }
            filter {
                includeGroup("cz.adaptech.tesseract4android")
                includeModule("com.github.k2-fsa", "sherpa-onnx")
            }
        }
    }
}

rootProject.name = "audora"

include(":app")
include(":core")
include(":pdf")
include(":speech")
include(":playback")
// Test-only: drives the dev build on an emulator to write app/src/main/baseline-prof.txt
// (ADR 2026-10-30-baseline-profile).
include(":baselineprofile")

// Spikes: throwaway debug apps that answer one risky question each (docs/TICKET-PLAN.md). Never shipped.
include(":spikes:playback")
include(":spikes:voices")
