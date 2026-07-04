package com.ttcoachai.shared.help

import com.ttcoachai.shared.drill.FeedbackLang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HelpContentCatalogTest {

    @Test
    fun everyEntryHasNonBlankQuestionAndAnswerInBothLangs() {
        for (lang in FeedbackLang.entries) {
            val faq = HelpContentCatalog.faq(lang)
            assertTrue(faq.isNotEmpty(), "$lang FAQ must not be empty")
            for (entry in faq) {
                assertTrue(entry.question.isNotBlank(), "$lang question must not be blank: $entry")
                assertTrue(entry.answer.isNotBlank(), "$lang answer must not be blank: $entry")
            }
        }
    }

    @Test
    fun hasAtLeastSixEntries() {
        assertTrue(HelpContentCatalog.faq(FeedbackLang.EN).size >= 6, "expected at least 6 FAQ entries")
        assertTrue(HelpContentCatalog.faq(FeedbackLang.UA).size >= 6, "expected at least 6 FAQ entries")
    }

    @Test
    fun bothLangsHaveSameEntryCount() {
        val en = HelpContentCatalog.faq(FeedbackLang.EN)
        val ua = HelpContentCatalog.faq(FeedbackLang.UA)
        assertEquals(en.size, ua.size, "EN and UA FAQ lists must have the same number of entries")
    }

    @Test
    fun englishAndUkrainianDifferPerEntry() {
        val en = HelpContentCatalog.faq(FeedbackLang.EN)
        val ua = HelpContentCatalog.faq(FeedbackLang.UA)
        for (i in en.indices) {
            assertNotEquals(en[i].question, ua[i].question, "entry $i: EN/UA questions should differ")
            assertNotEquals(en[i].answer, ua[i].answer, "entry $i: EN/UA answers should differ")
        }
    }

    @Test
    fun ukrainianTextIsCyrillic() {
        val ua = HelpContentCatalog.faq(FeedbackLang.UA)
        for (entry in ua) {
            val combined = entry.question + entry.answer
            assertTrue(
                combined.any { it in 'А'..'я' || it == 'і' || it == 'ї' || it == 'є' || it == 'І' || it == 'Ї' || it == 'Є' },
                "expected Cyrillic content: $combined"
            )
        }
    }

    @Test
    fun questionsAreUniquePerLanguage() {
        val enQuestions = HelpContentCatalog.faq(FeedbackLang.EN).map { it.question }
        val uaQuestions = HelpContentCatalog.faq(FeedbackLang.UA).map { it.question }
        assertEquals(enQuestions.size, enQuestions.toSet().size, "EN questions must be unique: $enQuestions")
        assertEquals(uaQuestions.size, uaQuestions.toSet().size, "UA questions must be unique: $uaQuestions")
    }

    @Test
    fun coversCalibrationTopic() {
        val en = HelpContentCatalog.faq(FeedbackLang.EN)
        assertTrue(
            en.any { it.question.contains("calibrat", ignoreCase = true) || it.answer.contains("calibrat", ignoreCase = true) },
            "expected an entry covering calibration/personal baseline"
        )
    }

    @Test
    fun coversCameraPlacementTopic() {
        val en = HelpContentCatalog.faq(FeedbackLang.EN)
        assertTrue(
            en.any { it.question.contains("camera", ignoreCase = true) || it.answer.contains("camera", ignoreCase = true) },
            "expected an entry covering camera placement"
        )
    }

    @Test
    fun coversSubscriptionTopic() {
        val en = HelpContentCatalog.faq(FeedbackLang.EN)
        assertTrue(
            en.any { it.question.contains("subscri", ignoreCase = true) || it.answer.contains("subscri", ignoreCase = true) },
            "expected an entry covering subscription"
        )
    }

    @Test
    fun coversSupportContactTopic() {
        val en = HelpContentCatalog.faq(FeedbackLang.EN)
        assertTrue(
            en.any { it.question.contains("contact", ignoreCase = true) || it.answer.contains("support", ignoreCase = true) },
            "expected an entry covering contacting support"
        )
    }
}
