package io.github.refael302.beamfinder.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val tag: String) {
    System(""),
    Hebrew("he"),
    English("en"),
}

/** Wraps per-app locale APIs (AppCompatDelegate + locales_config.xml). */
object LanguageManager {
    fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return AppLanguage.System
        val lang = locales[0]?.language
        // "iw" is the legacy code for Hebrew
        return when (lang) {
            "he", "iw" -> AppLanguage.Hebrew
            "en" -> AppLanguage.English
            else -> AppLanguage.System
        }
    }

    fun apply(language: AppLanguage) {
        val list = if (language == AppLanguage.System) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(list)
    }
}
