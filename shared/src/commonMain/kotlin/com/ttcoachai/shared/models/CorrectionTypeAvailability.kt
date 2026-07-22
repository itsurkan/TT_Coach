package com.ttcoachai.shared.models

/**
 * Which correction-type toggles are effective on each live-feedback path.
 * Visibility only — stored per-type enabled settings are never modified.
 */
object CorrectionTypeAvailability {
    val RTM: Set<CorrectionType> = setOf(
        CorrectionType.ELBOW_POSITION,
        CorrectionType.BODY_ROTATION,
        CorrectionType.KNEE_BEND,
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
