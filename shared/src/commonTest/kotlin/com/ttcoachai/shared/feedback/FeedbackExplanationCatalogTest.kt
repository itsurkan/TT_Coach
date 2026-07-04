package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.models.CorrectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FeedbackExplanationCatalogTest {

    @Test
    fun everyCorrectionTypeAndLangHasNonBlankFields() {
        for (type in CorrectionType.entries) {
            for (lang in FeedbackLang.entries) {
                val explanation = FeedbackExplanationCatalog.explain(type, lang)
                assertTrue(explanation.title.isNotBlank(), "$type/$lang title must not be blank")
                assertTrue(explanation.whatItChecks.isNotBlank(), "$type/$lang whatItChecks must not be blank")
                assertTrue(explanation.whyItMatters.isNotBlank(), "$type/$lang whyItMatters must not be blank")
                assertTrue(explanation.howToFix.isNotBlank(), "$type/$lang howToFix must not be blank")
            }
        }
    }

    @Test
    fun ukrainianAndEnglishDifferForEveryType() {
        for (type in CorrectionType.entries) {
            val en = FeedbackExplanationCatalog.explain(type, FeedbackLang.EN)
            val ua = FeedbackExplanationCatalog.explain(type, FeedbackLang.UA)
            assertNotEquals(en.title, ua.title, "$type: EN/UA titles should differ")
            assertNotEquals(en.whatItChecks, ua.whatItChecks, "$type: EN/UA whatItChecks should differ")
            assertNotEquals(en.whyItMatters, ua.whyItMatters, "$type: EN/UA whyItMatters should differ")
            assertNotEquals(en.howToFix, ua.howToFix, "$type: EN/UA howToFix should differ")
        }
    }

    @Test
    fun ukrainianTextIsCyrillic() {
        for (type in CorrectionType.entries) {
            val ua = FeedbackExplanationCatalog.explain(type, FeedbackLang.UA)
            val combined = ua.title + ua.whatItChecks + ua.whyItMatters + ua.howToFix
            assertTrue(
                combined.any { it in 'А'..'я' || it == 'і' || it == 'ї' || it == 'є' || it == 'І' || it == 'Ї' || it == 'Є' },
                "$type: expected Cyrillic content: $combined"
            )
        }
    }

    @Test
    fun titlesAreUniquePerLanguage() {
        val enTitles = CorrectionType.entries.map { FeedbackExplanationCatalog.explain(it, FeedbackLang.EN).title }
        val uaTitles = CorrectionType.entries.map { FeedbackExplanationCatalog.explain(it, FeedbackLang.UA).title }
        assertEquals(enTitles.size, enTitles.toSet().size, "EN titles must be unique: $enTitles")
        assertEquals(uaTitles.size, uaTitles.toSet().size, "UA titles must be unique: $uaTitles")
    }

    @Test
    fun wristTitleMatchesExistingAppTerminology() {
        assertEquals("Wrist Angle", FeedbackExplanationCatalog.explain(CorrectionType.WRIST, FeedbackLang.EN).title)
        assertEquals("Кут зап'ястя", FeedbackExplanationCatalog.explain(CorrectionType.WRIST, FeedbackLang.UA).title)
    }

    @Test
    fun bodyRotationTitleMatchesExistingAppTerminology() {
        assertEquals(
            "Body Rotation",
            FeedbackExplanationCatalog.explain(CorrectionType.BODY_ROTATION, FeedbackLang.EN).title
        )
        assertEquals(
            "Ротація корпусу",
            FeedbackExplanationCatalog.explain(CorrectionType.BODY_ROTATION, FeedbackLang.UA).title
        )
    }

    @Test
    fun strokeSpeedTitleMatchesExistingAppTerminology() {
        assertEquals(
            "Stroke Speed",
            FeedbackExplanationCatalog.explain(CorrectionType.STROKE_SPEED, FeedbackLang.EN).title
        )
        assertEquals(
            "Швидкість удару",
            FeedbackExplanationCatalog.explain(CorrectionType.STROKE_SPEED, FeedbackLang.UA).title
        )
    }

    @Test
    fun generalIsGenericTechniqueExplanation() {
        val en = FeedbackExplanationCatalog.explain(CorrectionType.GENERAL, FeedbackLang.EN)
        val ua = FeedbackExplanationCatalog.explain(CorrectionType.GENERAL, FeedbackLang.UA)
        assertTrue(en.title.contains("Technique", ignoreCase = true))
        assertTrue(ua.title.contains("Техніка", ignoreCase = true))
    }
}
