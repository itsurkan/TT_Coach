/*
 * AI Coach for Table Tennis
 * Locale Helper - Manages app language/locale
 */

package com.ttcoachai

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.*

object LocaleHelper {
    
    private const val PREF_LANGUAGE = "app_language"
    private const val PREF_NAME = "ai_coach_prefs"
    
    /**
     * Set and persist app language
     * @param context Context
     * @param languageCode Language code (e.g., "en", "uk") or empty string for system default
     */
    fun setLocale(context: Context, languageCode: String): Context {
        saveLanguagePreference(context, languageCode)
        return updateResources(context, languageCode)
    }
    
    /**
     * Apply saved language preference
     */
    fun applyLocale(context: Context): Context {
        val languageCode = getSavedLanguage(context)
        return updateResources(context, languageCode)
    }
    
    /**
     * Get currently saved language code
     * Returns empty string if following system language
     */
    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LANGUAGE, getDefaultLanguage(context)) ?: getDefaultLanguage(context)
    }
    
    /**
     * Get default language based on requirements:
     * - If system is English or Ukrainian, use system language
     * - Otherwise, default to Ukrainian
     */
    private fun getDefaultLanguage(context: Context): String {
        val systemLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        }
        
        return when (systemLanguage) {
            "en", "uk" -> systemLanguage
            else -> "en" // Default to English
        }
    }
    
    /**
     * Save language preference
     */
    private fun saveLanguagePreference(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LANGUAGE, languageCode).apply()
    }
    
    /**
     * Update context resources with new locale
     */
    private fun updateResources(context: Context, languageCode: String): Context {
        val locale = if (languageCode.isEmpty()) {
            // Use system default
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
            }
        } else {
            Locale(languageCode)
        }
        
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
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
