package com.ttcoachai.pose

import com.ttcoachai.shared.drill.FeedbackLang
import java.util.Locale

object DrillTtsLocale {

    fun localeFor(lang: FeedbackLang): Locale = when (lang) {
        FeedbackLang.UA -> Locale("uk")
        FeedbackLang.EN -> Locale.ENGLISH
    }

    fun resolve(lang: FeedbackLang, isAvailable: (Locale) -> Boolean): Locale? {
        val locale = localeFor(lang)
        return if (isAvailable(locale)) locale else null
    }
}
