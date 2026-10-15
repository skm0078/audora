// File: pdf/build.gradle.kts
// Purpose: PDF extraction pipeline: extract → quality → recover → layout → cleanup → ReadingUnit.

plugins {
    id("audora.android.library")
}

android {
    namespace = "io.github.skm0078.audora.pdf"
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // arm64-v8a ships (D5). x86_64 exists only so instrumented tests run on CI's emulator;
            // the app's own abiFilters keep it out of the APK.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

// Golden corpus (P2-01, pdf/golden/README.md). GoldenCorpusTest runs inside every unit test task: it scores the
// pipeline on the corpus, writes its report to build/golden/<task>/ and fails if the corpus score is lower than the
// baseline. The baseline is the committed pdf/golden/scores.tsv unless -Pgolden.baseline=<file> names another
// (CI passes the base branch's copy, so a branch cannot lower the bar by editing its own scores).
val goldenDir: Directory = layout.projectDirectory.dir("golden")
val goldenScores: File = goldenDir.file("scores.tsv").asFile
val goldenBaseline: String = providers.gradleProperty("golden.baseline").getOrElse(goldenScores.path)

tasks.withType<Test>().configureEach {
    val report = layout.buildDirectory.dir("golden/$name")
    systemProperty("audora.golden.dir", goldenDir.asFile.path)
    systemProperty("audora.golden.baseline", goldenBaseline)
    systemProperty("audora.golden.out", report.get().asFile.path)
    inputs.dir(goldenDir).withPropertyName("goldenCorpus").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(goldenBaseline).withPropertyName("goldenBaseline").withPathSensitivity(PathSensitivity.NONE)
    outputs.dir(report).withPropertyName("goldenReport")
}

// Records the debug run's report: docs/QUALITY.md for people, pdf/golden/scores.tsv as the next baseline.
// CI runs it and fails if either file then differs from the commit.
tasks.register("goldenTest") {
    group = "verification"
    description = "Scores the :pdf pipeline on the golden corpus and writes docs/QUALITY.md (P2-01)."
    dependsOn("testDebugUnitTest")
    val report = layout.buildDirectory.dir("golden/testDebugUnitTest")
    val qualityDoc = rootProject.layout.projectDirectory.file("docs/QUALITY.md").asFile
    val scores = goldenScores
    doLast {
        val dir = report.get().asFile
        dir.resolve("QUALITY.md").copyTo(qualityDoc, overwrite = true)
        dir.resolve("scores.tsv").copyTo(scores, overwrite = true)
        println("Golden scores written to $qualityDoc and $scores; spoken text in ${dir.resolve("spoken")}")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.pdfium.android.core)
    implementation(libs.pdfium.android.api)
    // PdfiumAndroidKt's lock uses coroutines but its POM does not declare them.
    implementation(libs.kotlinx.coroutines.core)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
