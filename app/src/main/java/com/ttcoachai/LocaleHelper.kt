/*
 * AI Coach for Table Tennis
 * Locale Helper - Manages app language/locale
 */

package com.ttcoachai

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.ttcoachai.managers.SettingsManager
import androidx.core.os.LocaleListCompat

/**
 * Interface-language plumbing.
 *
 * Application goes through [AppCompatDelegate.setApplicationLocales], which re-creates every
 * live `AppCompatActivity` so a language switch is visible immediately — the previous
 * `attachBaseContext` + `createConfigurationContext` approach only took effect on the next
 * cold start. Persistence stays in our own `ai_coach_prefs/app_language` (read back on every
 * process start from `TTCoachApplication.onCreate`), so `autoStoreLocales` is not needed.
 */
object LocaleHelper {

    /**
     * Switch the interface language now and remember it.
     *
     * @param languageCode "en" / "uk", or empty string to follow the system language.
     */
    fun setLocale(context: Context, languageCode: String) {
        SettingsManager(context.applicationContext).setLanguageCode(languageCode)
        applyStoredLocale(languageCode)
    }

    /** Apply an already-persisted language code without re-writing it. */
    fun applyStoredLocale(languageCode: String) {
        AppCompatDelegate.setApplicationLocales(
            if (languageCode.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(languageCode)
        )
    }

    /**
     * The language actually in effect: the explicit choice when there is one, otherwise the
     * system language resolved to a language we ship ("en" fallback).
     */
    fun getSavedLanguage(context: Context): String {
        val saved = SettingsManager(context.applicationContext).getLanguageCode()
        if (saved.isNotEmpty()) return saved
        val system = AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)
            ?: context.resources.configuration.locales[0]
        return if (system?.language == "uk") "uk" else "en"
    }

    /**
     * Get display name of language
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            "en" -> "English"
            "uk" -> "Українська"
            else -> languageCode
        }
    }
    
    /**
     * Get list of supported languages
     */
    fun getSupportedLanguages(): List<Pair<String, String>> {
        return listOf(
            "en" to "English",
            "uk" to "Українська"
        )
    }
}
