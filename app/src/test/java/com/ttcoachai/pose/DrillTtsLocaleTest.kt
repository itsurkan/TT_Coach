package com.ttcoachai.pose

import com.ttcoachai.shared.drill.FeedbackLang
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.util.Locale

class DrillTtsLocaleTest {

    @Test
    fun localeFor_UA_returns_uk_locale() {
        val locale = DrillTtsLocale.localeFor(FeedbackLang.UA)
        assertEquals("uk", locale.language)
    }

    @Test
    fun localeFor_EN_returns_en_locale() {
        val locale = DrillTtsLocale.localeFor(FeedbackLang.EN)
        assertEquals("en", locale.language)
    }

    @Test
    fun resolve_UA_available_returns_uk_locale() {
        val locale = DrillTtsLocale.resolve(FeedbackLang.UA) { true }
        assertEquals("uk", locale?.language)
    }

    @Test
    fun resolve_UA_not_available_returns_null() {
        val locale = DrillTtsLocale.resolve(FeedbackLang.UA) { false }
        assertNull(locale)
    }

    @Test
    fun resolve_EN_with_language_predicate_returns_en_locale() {
        val locale = DrillTtsLocale.resolve(FeedbackLang.EN) { it.language == "en" }
        assertEquals("en", locale?.language)
    }

    @Test
    fun resolve_UA_with_english_only_available_returns_null() {
        val locale = DrillTtsLocale.resolve(FeedbackLang.UA) { it.language == "en" }
        assertNull(locale)
    }
}
