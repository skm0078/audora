// File: playback/build.gradle.kts
// Purpose: PlaybackService, TtsDataSource, render queue, progress and the app's database. Synthesis runs here only.

plugins {
    id("audora.android.library")
    id("audora.hilt")
    id("audora.room")
}

android {
    namespace = "io.github.skm0078.audora.playback"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        jniLibs {
            // sherpa-onnx (via :speech) ships C and C++ API libraries nobody loads (ADR-012); keeps the test APK lean.
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }
}

// Listening score (RI-8a, ADR-035; pdf/golden/listening/README.md). :pdf's unit tests write the listening corpus's
// reading units into its golden report folder; ListeningCorpusTest, inside every unit test task here, turns them into
// what the voice is given, scores that, writes its report to build/listening/<task>/ and fails if the score is lower
// than the baseline: the committed pdf/golden/listening/scores.tsv unless -Plistening.baseline=<file> names another
// (CI passes the base branch's copy). Each test task waits for :pdf's task of the same name.
val listeningDir: Directory = rootProject.layout.projectDirectory.dir("pdf/golden/listening")
val listeningScores: File = listeningDir.file("scores.tsv").asFile
val listeningBaseline: String = providers.gradleProperty("listening.baseline").getOrElse(listeningScores.path)

tasks.withType<Test>().configureEach {
    dependsOn(":pdf:$name")
    val units = rootProject.layout.projectDirectory.dir("pdf/build/golden/$name/listening")
    val report = layout.buildDirectory.dir("listening/$name")
    systemProperty("audora.listening.dir", listeningDir.asFile.path)
    systemProperty("audora.listening.units", units.asFile.path)
    systemProperty("audora.listening.baseline", listeningBaseline)
    systemProperty("audora.listening.out", report.get().asFile.path)
    inputs.dir(listeningDir).withPropertyName("listeningCorpus").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(units).withPropertyName("listeningUnits").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(listeningBaseline).withPropertyName("listeningBaseline").withPathSensitivity(PathSensitivity.NONE)
    outputs.dir(report).withPropertyName("listeningReport")
}

// Records the debug run's report: docs/LISTENING.md for people, pdf/golden/listening/scores.tsv as the next baseline.
// CI runs it and fails if either file then differs from the commit.
tasks.register("listeningTest") {
    group = "verification"
    description = "Scores what the voice is given on the listening corpus and writes docs/LISTENING.md (RI-8a)."
    dependsOn("testDebugUnitTest")
    val report = layout.buildDirectory.dir("listening/testDebugUnitTest")
    val listeningDoc = rootProject.layout.projectDirectory.file("docs/LISTENING.md").asFile
    val scores = listeningScores
    doLast {
        val dir = report.get().asFile
        dir.resolve("LISTENING.md").copyTo(listeningDoc, overwrite = true)
        dir.resolve("scores.tsv").copyTo(scores, overwrite = true)
        println("Listening scores written to $listeningDoc and $scores; heard text in ${dir.resolve("heard")}")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":pdf"))
    implementation(project(":speech"))

    // The playback contract (PlaybackContract) speaks Media3 types, so controllers get them through this module.
    api(libs.media3.session)
    implementation(libs.media3.exoplayer)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
