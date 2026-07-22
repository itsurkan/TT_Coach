package com.ttcoachai.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CorrectionTypeAvailabilityTest {

    @Test
    fun `visibleFor rtm path returns exactly elbow, rotation, knee bend`() {
        val expected = setOf(
            CorrectionType.ELBOW_POSITION,
            CorrectionType.BODY_ROTATION,
            CorrectionType.KNEE_BEND,
        )
        assertEquals(expected, CorrectionTypeAvailability.visibleFor(true))
    }

    @Test
    fun `visibleFor legacy path returns all six legacy-effective types`() {
        val expected = setOf(
            CorrectionType.WRIST,
            CorrectionType.BODY_ROTATION,
            CorrectionType.FOLLOW_THROUGH,
            CorrectionType.CONTACT_HEIGHT,
            CorrectionType.ELBOW_POSITION,
            CorrectionType.KNEE_BEND,
        )
        assertEquals(expected, CorrectionTypeAvailability.visibleFor(false))
    }

    @Test
    fun `STROKE_SPEED and GENERAL are in neither path`() {
        val rtm = CorrectionTypeAvailability.visibleFor(true)
        val legacy = CorrectionTypeAvailability.visibleFor(false)
        assertFalse(CorrectionType.STROKE_SPEED in rtm)
        assertFalse(CorrectionType.STROKE_SPEED in legacy)
        assertFalse(CorrectionType.GENERAL in rtm)
        assertFalse(CorrectionType.GENERAL in legacy)
    }
}
