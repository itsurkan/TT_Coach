package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType

/**
 * Picks the pre-recorded voice clip (Android `res/raw`/`res/raw-uk`) to play for a
 * finalized live-training stroke, in place of the `ToneGenerator` ACK/NACK beeps.
 *
 * Pure logic, zero Android deps, so it can be unit-tested on the JVM and reused
 * from iOS later. Selection mirrors [LiveFeedbackFormatter.detailedFeedback]'s
 * negative-item filtering: same order (`result.feedbackItems` as given), same
 * [CorrectionType]-enabled predicate, same "skip positive items" rule — so the
 * spoken clip always matches the first bullet a user would see in detailed
 * feedback. Recorded clips are named after the `error_*` base keys in
 * `TechniqueErrors`; `rec_*` recommendation keys are out of scope here (no
 * recorded clips exist for them yet).
 */
object VoiceClipSelector {

    /**
     * Base key of the clip to voice for a finalized stroke, or null when the
     * caller should fall back to the tone beep.
     *
     * Walks [AnalysisResult.feedbackItems] in order and returns the `message` of
     * the first item that is not positive, has a non-blank message, and whose
     * [CorrectionType] is enabled per [isCorrectionTypeEnabled] — the same
     * order/filter convention as `LiveFeedbackFormatter.detailedFeedback`. Blank
     * messages and items whose correction type is disabled are skipped, not
     * treated as a match. Returns null when no item qualifies (e.g. a good
     * stroke with only positive feedback, or an empty list).
     */
    fun clipBaseKey(result: AnalysisResult, isCorrectionTypeEnabled: (CorrectionType) -> Boolean): String? {
        return result.feedbackItems.firstOrNull { item ->
            !item.isPositive && item.message.isNotBlank() && isCorrectionTypeEnabled(item.type)
        }?.message
    }

    /**
     * Android `res/raw`/`res/raw-uk` resource name for a base key (e.g.
     * `error_wrist_bent`): the `short_<key>` family for the live TTS cue when
     * [short] is true, else the `<key>_full` family — mirrors the naming
     * convention `LiveFeedbackCatalog.resolve` already uses for text lookups.
     */
    fun clipResourceName(baseKey: String, short: Boolean): String =
        if (short) "short_$baseKey" else "${baseKey}_full"
}
