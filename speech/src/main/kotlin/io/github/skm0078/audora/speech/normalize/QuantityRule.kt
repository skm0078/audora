// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/QuantityRule.kt
// Purpose: Reads a number or number range with a percent sign or unit (50%, 10–20 kg, 37 °C, 4 GB) in words.

package io.github.skm0078.audora.speech.normalize

/**
 * Measurements never read as years ("1500 km" is "one thousand five hundred kilometres"). The unit is singular only
 * for exactly one ("1 kg", but "1.5 kg" and "1–2 kg" are plural). Spelling follows Indian/British English
 * (kilometre). Units that are also common words or letters ("m", "in", "l") are left alone.
 */
internal class QuantityRule(private val numbers: NumberReader) : NormalizationRule {

    private data class Unit(val one: String, val many: String = one + "s")

    override fun apply(text: String, role: TextRole): String = QUANTITY.replace(text) { match ->
        val (from, to, symbol) = match.destructured
        val unit = UNITS.getValue(symbol)
        val isOne = to.isEmpty() && numbers.valueOf(from) == 1L && !from.contains('.')
        val amount = numbers.read(from, allowYear = false) +
            if (to.isEmpty()) "" else " to ${numbers.read(to, allowYear = false)}"
        "$amount ${if (isOne) unit.one else unit.many}"
    }

    private companion object {
        val UNITS = mapOf(
            "%" to Unit("percent", "percent"),
            "°C" to Unit("degree Celsius", "degrees Celsius"),
            "°F" to Unit("degree Fahrenheit", "degrees Fahrenheit"),
            "°" to Unit("degree"),
            "km/h" to Unit("kilometre per hour", "kilometres per hour"),
            "kmph" to Unit("kilometre per hour", "kilometres per hour"),
            "mph" to Unit("mile per hour", "miles per hour"),
            "sq km" to Unit("square kilometre", "square kilometres"),
            "km" to Unit("kilometre"),
            "cm" to Unit("centimetre"),
            "mm" to Unit("millimetre"),
            "kg" to Unit("kilogram"),
            "mg" to Unit("milligram"),
            "g" to Unit("gram"),
            "ml" to Unit("millilitre"),
            "ft" to Unit("foot", "feet"),
            "lbs" to Unit("pound"),
            "lb" to Unit("pound"),
            "KB" to Unit("kilobyte"),
            "MB" to Unit("megabyte"),
            "GB" to Unit("gigabyte"),
            "TB" to Unit("terabyte"),
            "Hz" to Unit("hertz", "hertz"),
            "kHz" to Unit("kilohertz", "kilohertz"),
            "MHz" to Unit("megahertz", "megahertz"),
            "GHz" to Unit("gigahertz", "gigahertz"),
            "kWh" to Unit("kilowatt hour"),
            "kW" to Unit("kilowatt"),
            "MW" to Unit("megawatt"),
            "ms" to Unit("millisecond"),
            "hrs" to Unit("hour"),
            "yrs" to Unit("year"),
        )

        private val UNIT_PATTERN = UNITS.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }
        private const val NUMBER = NumberPatterns.NUMBER

        // Degrees and percent hug the number; letter units may follow a space. A letter unit must end the word.
        val QUANTITY = Regex(
            """(?<![\w.,])($NUMBER)(?:\s?[–—-]\s?($NUMBER))?\s?($UNIT_PATTERN)(?![\w/])""",
        )
    }
}
