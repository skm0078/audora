// File: pdf/build.gradle.kts
// Purpose: PDF extraction pipeline: extract → quality → recover → layout → cleanup → ReadingUnit.

plugins {
    id("audora.android.library")
}

android {
    namespace = "io.github.skm0078.audora.pdf"
}

dependencies {
    implementation(project(":core"))
}
