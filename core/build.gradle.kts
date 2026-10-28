// File: core/build.gradle.kts
// Purpose: Domain model, Result/error types, logging facade, config objects. Pure Kotlin, no Android.

plugins {
    id("audora.jvm.library")
    // The load tests' made-up library and budget (ADR 2026-10-28-performance-budget), shared with :app's JVM and
    // emulator tests without shipping in the app.
    `java-test-fixtures`
}

dependencies {
    // api: the library's contracts expose Flow and suspend functions to the modules that implement them.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
