package com.ttcoachai.shared.models

/**
 * Which correction-type toggles are effective on each live-feedback path.
 * Visibility only — stored per-type enabled settings are never modified.
 *
 * RTM (RTMPose live drill) grades the 5 in-plane angle metrics + the 2 qualitative derived proxies
 * (coil→BODY_ROTATION, stroke speed): elbow bend, elbow position, body rotation, posture, knee bend,
 * follow-through, stroke speed. WRIST (needs hand keypoints) and CONTACT_HEIGHT (needs the ball) are
 * Stage-2 and hidden on RTM. LEGACY (MediaPipe pipeline) keeps its original six. GENERAL is a catch-all,
 * never a user-facing toggle, so it is in neither set.
 */
object CorrectionTypeAvailability {
    val RTM: Set<CorrectionType> = setOf(
        CorrectionType.ELBOW_BEND,
        CorrectionType.ELBOW_POSITION,
        CorrectionType.BODY_ROTATION,
        CorrectionType.POSTURE,
        CorrectionType.KNEE_BEND,
        CorrectionType.FOLLOW_THROUGH,
        CorrectionType.STROKE_SPEED,
    )

    val LEGACY: Set<CorrectionType> = setOf(
        CorrectionType.WRIST,
        CorrectionType.BODY_ROTATION,
        CorrectionType.FOLLOW_THROUGH,
        CorrectionType.CONTACT_HEIGHT,
        CorrectionType.ELBOW_POSITION,
        CorrectionType.KNEE_BEND,
    )

    fun visibleFor(rtmPath: Boolean): Set<CorrectionType> = if (rtmPath) RTM else LEGACY
}
