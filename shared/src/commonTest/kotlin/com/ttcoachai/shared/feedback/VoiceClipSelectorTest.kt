package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceClipSelectorTest {

    // ── clipBaseKey ───────────────────────────────────────────────────────────

    @Test
    fun clipBaseKey_firstEnabledNegative_isPicked() {
        val result = resultWith(
            FeedbackItem(message = "error_wrist_bent", type = CorrectionType.WRIST),
            FeedbackItem(message = "error_low_rotation", type = CorrectionType.BODY_ROTATION)
        )

        val key = VoiceClipSelector.clipBaseKey(result) { true }
        assertEquals("error_wrist_bent", key)
    }

    @Test
    fun clipBaseKey_disabledCorrectionType_isSkipped() {
        val result = resultWith(
            FeedbackItem(message = "error_wrist_bent", type = CorrectionType.WRIST),
            FeedbackItem(message = "error_low_rotation", type = CorrectionType.BODY_ROTATION)
        )

        val key = VoiceClipSelector.clipBaseKey(result) { type -> type != CorrectionType.WRIST }
        assertEquals("error_low_rotation", key)
    }

    @Test
    fun clipBaseKey_positiveItem_isSkipped() {
        val result = resultWith(
            FeedbackItem(message = "positive_ignored", type = CorrectionType.WRIST, isPositive = true),
            FeedbackItem(message = "error_low_rotation", type = CorrectionType.BODY_ROTATION)
        )

        val key = VoiceClipSelector.clipBaseKey(result) { true }
        assertEquals("error_low_rotation", key)
    }

    @Test
    fun clipBaseKey_blankMessage_isSkipped() {
        val result = resultWith(
            FeedbackItem(message = "", type = CorrectionType.WRIST),
            FeedbackItem(message = "error_low_rotation", type = CorrectionType.BODY_ROTATION)
        )

        val key = VoiceClipSelector.clipBaseKey(result) { true }
        assertEquals("error_low_rotation", key)
    }

    @Test
    fun clipBaseKey_allPositive_returnsNull() {
        val result = resultWith(
            FeedbackItem(message = "positive_1", type = CorrectionType.WRIST, isPositive = true),
            FeedbackItem(message = "positive_2", type = CorrectionType.BODY_ROTATION, isPositive = true)
        )

        assertNull(VoiceClipSelector.clipBaseKey(result) { true })
    }

    @Test
    fun clipBaseKey_emptyFeedbackItems_returnsNull() {
        val result = resultWith()
        assertNull(VoiceClipSelector.clipBaseKey(result) { true })
    }

    @Test
    fun clipBaseKey_allDisabled_returnsNull() {
        val result = resultWith(
            FeedbackItem(message = "error_wrist_bent", type = CorrectionType.WRIST),
            FeedbackItem(message = "error_low_rotation", type = CorrectionType.BODY_ROTATION)
        )

        assertNull(VoiceClipSelector.clipBaseKey(result) { false })
    }

    // ── clipResourceName ──────────────────────────────────────────────────────

    @Test
    fun clipResourceName_short_prependsShortPrefix() {
        assertEquals("short_error_wrist_bent", VoiceClipSelector.clipResourceName("error_wrist_bent", short = true))
    }

    @Test
    fun clipResourceName_full_appendsFullSuffix() {
        assertEquals("error_wrist_bent_full", VoiceClipSelector.clipResourceName("error_wrist_bent", short = false))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resultWith(vararg items: FeedbackItem): AnalysisResult =
        AnalysisResult(feedbackItems = items.toList())
}
