// File: app/src/main/kotlin/io/github/skm0078/audora/Cookbook.kt
// Purpose: The ListenLock Cookbook (ADR 2026-10-29-in-app-cookbook): the site's recipes, built into the APK's assets
//          by CookbookAssetsTask, opened as a book in the app's language like text shared to ListenLock.

package io.github.skm0078.audora

import android.content.Context
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.IOException

/**
 * The cookbook as shared text, saved and opened like the sample ([sampleBook]). [context] must be the activity, whose
 * resources are in the language picked in Settings; any other language gets the English book. Its first chapter says
 * how to use it, then one chapter per recipe. If the asset is missing (it is built with the app, so only a broken
 * build), the first chapter still opens.
 */
internal fun cookbookBook(context: Context): SharedText {
    val lang = context.resources.configuration.locales[0].language
    val recipes = readCookbookAsset(context, lang) ?: readCookbookAsset(context, COOKBOOK_FALLBACK_LANG).orEmpty()
    return SharedText(
        title = context.getString(R.string.cookbook_title),
        text = cookbookText(
            introTitle = context.getString(R.string.cookbook_intro_title),
            intro = context.getString(R.string.cookbook_intro),
            recipes = recipes,
        ),
    )
}

/** The book's Markdown: the introduction as its first chapter (a level-1 heading starts one), then the recipes. */
internal fun cookbookText(introTitle: String, intro: String, recipes: String): String =
    "# $introTitle\n\n$intro\n\n${recipes.trim()}\n".replace(Regex("\n{3,}"), "\n\n")

private fun readCookbookAsset(context: Context, lang: String): String? = try {
    context.assets.open("$COOKBOOK_ASSET_DIR/$lang.md").bufferedReader().use { it.readText() }
} catch (e: IOException) {
    AudoraLog.w(COOKBOOK_TAG, "Cookbook for $lang is missing: ${e.javaClass.simpleName}")
    null
}

internal const val COOKBOOK_ASSET_DIR = "cookbook"
private const val COOKBOOK_FALLBACK_LANG = "en"
private const val COOKBOOK_TAG = "Audora.Cookbook"
