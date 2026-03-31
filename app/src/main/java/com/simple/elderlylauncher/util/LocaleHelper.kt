package com.simple.elderlylauncher.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Helper for managing app language/locale settings.
 */
object LocaleHelper {

    private const val PREF_NAME = "language_prefs"
    private const val PREF_LANGUAGE = "app_language"
    private const val DEFAULT_LANGUAGE = "en"

    data class Language(
        val code: String,
        val nativeName: String,
        val englishName: String
    )

    /**
     * Available languages in the app.
     */
    val availableLanguages = listOf(
        Language("en", "English", "English"),
        Language("hi", "हिन्दी", "Hindi"),
        Language("ta", "தமிழ்", "Tamil"),
        Language("te", "తెలుగు", "Telugu"),
        Language("bn", "বাংলা", "Bengali")
    )

    /**
     * Get the saved language code, defaulting to English.
     */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * Save the selected language code.
     */
    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LANGUAGE, languageCode).apply()
    }

    /**
     * Wrap the context with the saved locale.
     * Call this in attachBaseContext() of each Activity.
     */
    fun wrap(context: Context): Context {
        val language = getLanguage(context)
        return updateResources(context, language)
    }

    /**
     * Update the context's configuration with the given language.
     */
    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }

    /**
     * Get the Language object for the current language.
     */
    fun getCurrentLanguage(context: Context): Language {
        val code = getLanguage(context)
        return availableLanguages.find { it.code == code } ?: availableLanguages[0]
    }
}
