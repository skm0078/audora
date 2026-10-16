// File: core/src/test/kotlin/io/github/skm0078/audora/core/battery/BatteryGuideTest.kt
// Purpose: Phone makers are recognized from Build values and each gets battery steps.

package io.github.skm0078.audora.core.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryGuideTest {
    @Test
    fun `makers and sub-brands are recognized`() {
        val cases = listOf(
            Triple("motorola", "motorola", PhoneBrand.MOTOROLA),
            Triple("OnePlus", "OnePlus", PhoneBrand.ONEPLUS),
            Triple("samsung", "samsung", PhoneBrand.SAMSUNG),
            Triple("Xiaomi", "Redmi", PhoneBrand.XIAOMI),
            Triple("Xiaomi", "POCO", PhoneBrand.XIAOMI),
            Triple("OPPO", "OPPO", PhoneBrand.OPPO),
            Triple("realme", "realme", PhoneBrand.REALME),
            Triple("vivo", "iQOO", PhoneBrand.VIVO),
            Triple("Google", "google", PhoneBrand.OTHER),
            Triple(" motorola ", null, PhoneBrand.MOTOROLA),
            Triple(null, null, PhoneBrand.OTHER),
        )
        for ((manufacturer, brand, expected) in cases) {
            assertEquals("$manufacturer/$brand", expected, PhoneBrand.of(manufacturer, brand))
        }
    }

    @Test
    fun `every brand has at least one step`() {
        for (brand in PhoneBrand.entries) assertTrue(brand.name, BatteryGuide.stepsFor(brand).isNotEmpty())
    }

    @Test
    fun `the test phones get their own steps`() {
        assertEquals(listOf(BatteryStep.APP_BATTERY_UNRESTRICTED), BatteryGuide.stepsFor(PhoneBrand.MOTOROLA))
        assertEquals(
            listOf(BatteryStep.ALLOW_BACKGROUND_ACTIVITY, BatteryStep.LOCK_IN_RECENTS),
            BatteryGuide.stepsFor(PhoneBrand.ONEPLUS),
        )
    }
}
