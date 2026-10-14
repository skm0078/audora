// File: build.gradle.kts
// Purpose: Root build. Hosts the repo-wide code-quality tasks (ktlintCheck, ktlintFormat, detekt).
//
// ktlint and detekt run as their official CLIs rather than Gradle plugins. The CLIs do not depend on
// how AGP wires Kotlin source sets (AGP 9 built-in Kotlin changed that), so they cannot silently skip
// a module. The trade-off is that detekt runs without type resolution, which only disables a few rules.

val lintedModules = listOf("app", "core", "pdf", "speech", "playback", "build-logic/convention")

val ktlintCli: Configuration by configurations.creating
val detektCli: Configuration by configurations.creating

dependencies {
    ktlintCli(libs.ktlint.cli) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
    detektCli(libs.detekt.cli)
}

val kotlinSources =
    fileTree(rootDir) {
        include("*.kts", "gradle/**/*.kts")
        lintedModules.forEach { module ->
            include("$module/src/**/*.kt", "$module/*.kts")
        }
    }

val ktlintPatterns =
    listOf("*.kts") + lintedModules.flatMap { module -> listOf("$module/src/**/*.kt", "$module/*.kts") }

// ktlint reflects into java.lang on JDK 16+.
val ktlintJvmArgs = listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Checks Kotlin code style with ktlint (rules from .editorconfig)."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs(ktlintJvmArgs)
    workingDir = rootDir
    inputs.files(kotlinSources)
    inputs.file(".editorconfig")
    val report = layout.buildDirectory.file("reports/ktlint/ktlint.xml")
    outputs.file(report)
    args("--relative", "--reporter=plain", "--reporter=checkstyle,output=${report.get().asFile.path}")
    args(ktlintPatterns)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Auto-fixes Kotlin code style with ktlint."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs(ktlintJvmArgs)
    workingDir = rootDir
    args("--relative", "--format")
    args(ktlintPatterns)
}

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Runs detekt static analysis on every module."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    workingDir = rootDir
    val config = file("config/detekt/detekt.yml")
    val sourceDirs = lintedModules.map { file("$it/src") }.filter { it.isDirectory }
    inputs.files(kotlinSources)
    inputs.file(config)
    val reportDir = layout.buildDirectory.dir("reports/detekt")
    outputs.dir(reportDir)
    args(
        "--input",
        sourceDirs.joinToString(",") { it.path },
        "--config",
        config.path,
        "--build-upon-default-config",
        "--report",
        "html:${reportDir.get().asFile.path}/detekt.html",
        "--report",
        "sarif:${reportDir.get().asFile.path}/detekt.sarif",
    )
}
