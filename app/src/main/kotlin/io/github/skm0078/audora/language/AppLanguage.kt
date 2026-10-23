// File: app/src/main/kotlin/io/github/skm0078/audora/language/AppLanguage.kt
// Purpose: The app's own language (plan item 44): English, Hindi, Bengali or the phone's, chosen in Settings and
//          applied without AppCompat (the system's per-app language on Android 13+, a wrapped context before).

package io.github.skm0078.audora.language

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import io.github.skm0078.audora.core.logging.AudoraLog

/** @property tag the BCP 47 language tag; null follows the phone's language. */
enum class AppLanguage(val tag: String?) {
    ENGLISH("en"),
    HINDI("hi"),
    BENGALI("bn"),
    PHONE(null),
    ;

    companion object {
        /** English until the maintainer picks the default (call 4); Hindi and Bengali are a tap away. */
        val DEFAULT = ENGLISH

        /** A stored name from this or an older version; anything unknown is [DEFAULT]. */
        fun fromName(name: String?): AppLanguage = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Where the choice is kept. SharedPreferences, not DataStore: the language must be known synchronously in
 * attachBaseContext, before the first frame, and it is one short string.
 */
class AppLanguageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): AppLanguage = AppLanguage.fromName(prefs.getString(KEY, null))

    /** Written at once (commit), because the screens are recreated in the new language straight after. */
    fun save(language: AppLanguage): Boolean = prefs.edit().putString(KEY, language.name).commit().also { saved ->
        if (!saved) AudoraLog.w(TAG, "Could not save the app language ${language.name}")
    }

    private companion object {
        const val FILE = "app_language"
        const val KEY = "language"
        const val TAG = "Audora.Language"
    }
}

/** Applies a language to the process (Android 13+) or to one activity's context (Android 12). */
object AppLanguages {
    /**
     * Android 13+: hands the choice to the system, so the activity, the playback notification, the widget and the
     * tile all use it. Called at start (the default is English even on a Hindi phone) and when the choice changes.
     */
    fun applyToProcess(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        val wanted = language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        if (manager.applicationLocales != wanted) manager.applicationLocales = wanted
    }

    /** Android 12: the activity's resources in the chosen language (its notification and widget keep the phone's). */
    fun wrap(base: Context, language: AppLanguage): Context {
        val tag = language.tag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || tag == null) return base
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
        return base.createConfigurationContext(config)
    }

    /** After a new choice is saved: Android 13+ recreates the screens itself; Android 12 needs the activity redone. */
    fun switchTo(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applyToProcess(activity, language)
        } else {
            activity.recreate()
        }
    }
}
