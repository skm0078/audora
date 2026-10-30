// File: build-logic/convention/build.gradle.kts
// Purpose: Builds the convention plugins that keep module build files short and consistent.

plugins {
    `kotlin-dsl`
}

dependencies {
    // implementation (not compileOnly): the conventions apply these plugins by id,
    // so they must be on the build classpath. Versions come only from libs.versions.toml.
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "audora.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "audora.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidTest") {
            id = "audora.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("jvmLibrary") {
            id = "audora.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("hilt") {
            id = "audora.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("room") {
            id = "audora.room"
            implementationClass = "RoomConventionPlugin"
        }
    }
}
