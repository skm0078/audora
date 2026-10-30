// File: build-logic/convention/src/main/kotlin/CookbookAssetsTask.kt
// Purpose: Turns the site's recipes (docs/_recipes/*.md, ADR 2026-10-29-site-recipes-and-blog) into one Markdown book
//          per app language in the APK's assets, so the ListenLock Cookbook in the app and the recipes on the site
//          come from the same files (ADR 2026-10-29-in-app-cookbook). A recipe missing a field fails the build.

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CookbookAssetsTask : DefaultTask() {
    /** docs/_recipes: English recipes at the top, translations in `hi/` and `bn/` under the same file name. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val recipes: DirectoryProperty

    /** Asset root; the books land in `cookbook/<lang>.md` under it. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    init {
        group = "build"
        description = "Builds the in-app ListenLock Cookbook from the site's recipes (docs/_recipes)."
    }

    @TaskAction
    fun write() {
        val root = recipes.get().asFile
        val english = read(root)
        if (english.isEmpty()) fail(root, "no recipes found")
        fileSystem.delete { delete(outputDirectory) }
        val target = outputDirectory.get().dir(ASSET_DIR).asFile.apply { mkdirs() }
        LABELS.keys.forEach { lang ->
            // A recipe not translated yet is read in English, so no language's book misses a recipe.
            val translated = if (lang == ENGLISH) emptyMap() else read(File(root, lang), lang).associateBy { it.slug }
            val book = english.map { translated[it.slug] ?: it }.sortedWith(compareBy({ it.order }, { it.slug }))
            File(target, "$lang.md").writeText(book.joinToString("\n\n") { it.chapter() } + "\n")
        }
        logger.lifecycle("Cookbook: ${english.size} recipes, books for ${LABELS.keys}")
    }

    private fun read(folder: File, lang: String = ENGLISH): List<Recipe> =
        folder.listFiles { file -> file.isFile && file.name.endsWith(".md") }.orEmpty().map { parse(it, lang) }

    private fun parse(file: File, lang: String): Recipe {
        val lines = file.readText().replace("\r\n", "\n").lines()
        if (lines.firstOrNull() != FENCE) fail(file, "must start with front matter (---)")
        val end = lines.drop(1).indexOf(FENCE).takeIf { it >= 0 }?.plus(1)
            ?: fail(file, "front matter has no closing ---")
        val fields = lines.subList(1, end).mapNotNull { FIELD.matchEntire(it.substringBefore(" #").trimEnd()) }
            .associate { it.groupValues[1] to it.groupValues[2].trim() }
        fun field(key: String) = fields[key]?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
            ?: fail(file, "front matter needs '$key'")
        val features = field("features").removeSurrounding("[", "]").split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return Recipe(
            slug = file.nameWithoutExtension,
            lang = lang,
            order = field("order").toIntOrNull() ?: fail(file, "'order' must be a whole number"),
            title = field("title"),
            description = field("description"),
            facts = listOf(field("needs"), field("setup"), features.joinToString(", "), field("cost")),
            body = spoken(lines.drop(end + 1).joinToString("\n").trim()),
        )
    }

    /** Site-only markup left out: Liquid links keep their words, other Liquid tags go. */
    private fun spoken(body: String): String = body
        .replace(LIQUID_LINK, "$1")
        .replace(LIQUID, "")

    private data class Recipe(
        val slug: String,
        val lang: String,
        val order: Int,
        val title: String,
        val description: String,
        val facts: List<String>,
        val body: String,
    ) {
        /** A level-1 heading starts a chapter in the app's Markdown reader; facts read as short labelled lines. */
        fun chapter(): String = buildString {
            append("# ").append(title).append("\n\n").append(description).append("\n\n")
            LABELS.getValue(lang).zip(facts).forEach { (label, value) ->
                append("- ").append(label).append(": ").append(value).append('\n')
            }
            append('\n').append(body)
        }
    }

    private fun fail(file: File, message: String): Nothing =
        throw GradleException("Cookbook recipe ${file.name}: $message (see docs/CONTENT.md)")

    private companion object {
        const val ASSET_DIR = "cookbook"
        const val FENCE = "---"
        const val ENGLISH = "en"
        val FIELD = Regex("""^([a-z_]+):\s*(.*)$""")
        val LIQUID_LINK = Regex("""\[([^\]\n]+)]\(\{\{[^)\n]*\}\}\)""")
        val LIQUID = Regex("""\{\{.*?\}\}|\{%.*?%\}""")

        /** The facts' labels in each app language, in the order of [Recipe.facts]; a recipe uses its own language's. */
        val LABELS = linkedMapOf(
            ENGLISH to listOf("You need", "Set-up", "Features combined", "Phone cost"),
            "hi" to listOf("आपको चाहिए", "तैयारी", "साथ में इस्तेमाल होने वाली सुविधाएँ", "फ़ोन पर असर"),
            "bn" to listOf("যা লাগবে", "প্রস্তুতি", "একসঙ্গে ব্যবহৃত সুবিধা", "ফোনের খরচ"),
        )
    }
}
