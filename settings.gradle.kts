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
        // sherpa-onnx ships its Android AAR only as a GitHub release asset, not on Maven (ADR-012). Exclusive, so
        // this module is never looked up anywhere else and nothing else is looked up here. Checksum: see
        // sherpaOnnxAarSha256 in libs.versions.toml.
        exclusiveContent {
            forRepository {
                ivy {
                    name = "sherpaOnnxGitHubReleases"
                    url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download/")
                    patternLayout { artifact("v[revision]/[artifact]-[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.k2fsa", "sherpa-onnx") }
        }
        // Tesseract4Android is published only on JitPack (ADR-025). Exclusive and limited to its group, so JitPack
        // serves nothing else and this library is looked up nowhere else. Checksum: tesseract4androidAarSha256.
        exclusiveContent {
            forRepository {
                maven {
                    name = "jitpack"
                    url = uri("https://jitpack.io")
                }
            }
            filter { includeGroup("cz.adaptech.tesseract4android") }
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
