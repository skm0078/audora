// File: core/build.gradle.kts
// Purpose: Domain model, Result/error types, logging facade, config objects. Pure Kotlin, no Android.

plugins {
    id("audora.jvm.library")
}

dependencies {
    // api: the library's contracts expose Flow and suspend functions to the modules that implement them.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
