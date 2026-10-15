// File: core/src/main/kotlin/io/github/skm0078/audora/core/battery/BatteryGuide.kt
// Purpose: Which phone maker this is and which settings keep Audora reading with the screen off there (P1-06).

package io.github.skm0078.audora.core.battery

import java.util.Locale

/** Phone makers whose battery savers need their own steps (Build Spec §6, risk "OEM kills the FGS"). */
enum class PhoneBrand {
    MOTOROLA,
    ONEPLUS,
    SAMSUNG,
    XIAOMI,
    OPPO,
    REALME,
    VIVO,
    OTHER,
    ;

    companion object {
        /** From `Build.MANUFACTURER` and `Build.BRAND`; sub-brands (Redmi, POCO, iQOO) map to their maker. */
        fun of(manufacturer: String?, brand: String?): PhoneBrand {
            val names = listOfNotNull(manufacturer, brand).map { it.trim().lowercase(Locale.ROOT) }
            return entries.firstOrNull { candidate -> names.any { it in candidate.aliases } } ?: OTHER
        }
    }
}

private val PhoneBrand.aliases: Set<String>
    get() = when (this) {
        PhoneBrand.MOTOROLA -> setOf("motorola")
        PhoneBrand.ONEPLUS -> setOf("oneplus")
        PhoneBrand.SAMSUNG -> setOf("samsung")
        PhoneBrand.XIAOMI -> setOf("xiaomi", "redmi", "poco")
        PhoneBrand.OPPO -> setOf("oppo")
        PhoneBrand.REALME -> setOf("realme")
        PhoneBrand.VIVO -> setOf("vivo", "iqoo")
        PhoneBrand.OTHER -> emptySet()
    }

/**
 * A manual step in the phone's own settings. The app turns each into text; only the app info page is opened
 * for the user, because brand-specific settings screens are private activities that move between OS versions.
 */
enum class BatteryStep {
    /** Stock Android 12+, Motorola: App info → App battery usage → Allow background usage → Unrestricted. */
    APP_BATTERY_UNRESTRICTED,

    /** OxygenOS, ColorOS, realme UI: App info → Battery usage → Allow background activity. */
    ALLOW_BACKGROUND_ACTIVITY,

    /** ColorOS, realme UI, Xiaomi, vivo: allow auto launch / autostart. */
    ALLOW_AUTO_LAUNCH,

    /** Samsung: Settings → Battery → Background usage limits → Never sleeping apps → add Audora. */
    SAMSUNG_NEVER_SLEEPING,

    /** Xiaomi: App info → Battery saver → No restrictions. */
    XIAOMI_NO_RESTRICTIONS,

    /** vivo: Settings → Battery → Background power consumption management → Audora → Allow. */
    VIVO_HIGH_BACKGROUND_POWER,

    /** Recent apps → Audora's card → Lock, so "clear all" does not kill it (OxygenOS, MIUI, ColorOS, Funtouch). */
    LOCK_IN_RECENTS,
}

object BatteryGuide {
    /**
     * Steps after the system's "ignore battery optimization" prompt, which every brand gets first. OnePlus and
     * Motorola are the test phones (D4, ADR-012) and are kept short and exact; other brands follow
     * dontkillmyapp.com's advice for their current OS.
     */
    fun stepsFor(brand: PhoneBrand): List<BatteryStep> = when (brand) {
        PhoneBrand.MOTOROLA -> listOf(BatteryStep.APP_BATTERY_UNRESTRICTED)

        PhoneBrand.ONEPLUS -> listOf(BatteryStep.ALLOW_BACKGROUND_ACTIVITY, BatteryStep.LOCK_IN_RECENTS)

        PhoneBrand.SAMSUNG -> listOf(BatteryStep.APP_BATTERY_UNRESTRICTED, BatteryStep.SAMSUNG_NEVER_SLEEPING)

        PhoneBrand.XIAOMI -> listOf(
            BatteryStep.ALLOW_AUTO_LAUNCH,
            BatteryStep.XIAOMI_NO_RESTRICTIONS,
            BatteryStep.LOCK_IN_RECENTS,
        )

        PhoneBrand.OPPO, PhoneBrand.REALME -> listOf(
            BatteryStep.ALLOW_BACKGROUND_ACTIVITY,
            BatteryStep.ALLOW_AUTO_LAUNCH,
            BatteryStep.LOCK_IN_RECENTS,
        )

        PhoneBrand.VIVO -> listOf(
            BatteryStep.VIVO_HIGH_BACKGROUND_POWER,
            BatteryStep.ALLOW_AUTO_LAUNCH,
            BatteryStep.LOCK_IN_RECENTS,
        )

        PhoneBrand.OTHER -> listOf(BatteryStep.APP_BATTERY_UNRESTRICTED)
    }
}
