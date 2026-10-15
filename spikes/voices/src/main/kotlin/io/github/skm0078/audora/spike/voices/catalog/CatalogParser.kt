// File: spikes/voices/src/main/kotlin/io/github/skm0078/audora/spike/voices/catalog/CatalogParser.kt
// Purpose: Parses assets/s1-voices.json with the platform's org.json. Fails loudly on a malformed catalog.

package io.github.skm0078.audora.spike.voices.catalog

import org.json.JSONArray
import org.json.JSONObject

object CatalogParser {
    fun parse(json: String): Catalog {
        val root = JSONObject(json)
        return Catalog(
            version = root.getInt("version"),
            modelBaseUrl = root.getString("modelBaseUrl").also {
                require(it.endsWith("/")) { "modelBaseUrl must end with '/'" }
            },
            texts = root.getJSONArray("texts").objects().map(::parseText),
            voices = root.getJSONArray("voices").objects().map(::parseVoice),
        ).also(::validate)
    }

    private fun parseText(o: JSONObject) = BenchText(
        id = o.getString("id"),
        lang = o.getString("lang"),
        kind = TextKind.fromKey(o.getString("kind")),
        text = o.getString("text"),
    )

    private fun parseVoice(o: JSONObject) = VoiceSpec(
        id = o.getString("id"),
        label = o.getString("label"),
        lang = o.getString("lang"),
        family = ModelFamily.fromKey(o.getString("family")),
        archive = o.getString("archive"),
        model = o.optString("model"),
        voices = o.optString("voices"),
        dataDir = o.optString("dataDir"),
        lexicon = o.optString("lexicon"),
        kokoroLang = o.optString("kokoroLang"),
        extraLang = o.optString("extraLang"),
        sid = o.optInt("sid", 0),
        numSteps = o.optInt("numSteps", VoiceSpec.DEFAULT_SUPERTONIC_STEPS),
        license = o.optString("license"),
    )

    private fun validate(catalog: Catalog) {
        val duplicate = catalog.voices.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicate.isEmpty()) { "Duplicate voice ids: $duplicate" }
        catalog.languages.forEach { lang ->
            val kinds = catalog.textsFor(lang).map { it.kind }.toSet()
            require(kinds == TextKind.entries.toSet()) { "Language '$lang' needs one text of each kind, has $kinds" }
        }
        catalog.voices.forEach { voice ->
            val needsModelFile = voice.family != ModelFamily.SUPERTONIC
            require(!needsModelFile || voice.model.isNotEmpty()) { "Voice ${voice.id} has no model file" }
            require(voice.family != ModelFamily.SUPERTONIC || voice.extraLang.isNotEmpty()) {
                "Supertonic voice ${voice.id} needs extraLang"
            }
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
