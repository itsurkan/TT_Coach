package com.ttcoachai.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorrectionTypeAvailabilityTest {
    @Test
    fun `visibleFor(true) returns exactly the 7-element RTM set`() {
        val rtmSet = CorrectionTypeAvailability.visibleFor(true)
        assertEquals(7, rtmSet.size)
        assertEquals(
            setOf(
                CorrectionType.ELBOW_BEND,
                CorrectionType.ELBOW_POSITION,
                CorrectionType.BODY_ROTATION,
                CorrectionType.POSTURE,
                CorrectionType.KNEE_BEND,
                CorrectionType.FOLLOW_THROUGH,
                CorrectionType.STROKE_SPEED,
            ),
            rtmSet
        )
    }

    @Test
    fun `visibleFor(false) returns exactly the 6-element LEGACY set`() {
        val legacySet = CorrectionTypeAvailability.visibleFor(false)
        assertEquals(6, legacySet.size)
        assertEquals(
            setOf(
                CorrectionType.WRIST,
                CorrectionType.BODY_ROTATION,
                CorrectionType.FOLLOW_THROUGH,
                CorrectionType.CONTACT_HEIGHT,
                CorrectionType.ELBOW_POSITION,
                CorrectionType.KNEE_BEND,
            ),
            legacySet
        )
    }

    @Test
    fun `RTM path hides WRIST`() {
        assertFalse(CorrectionType.WRIST in CorrectionTypeAvailability.visibleFor(true))
    }

    @Test
    fun `RTM path hides CONTACT_HEIGHT`() {
        assertFalse(CorrectionType.CONTACT_HEIGHT in CorrectionTypeAvailability.visibleFor(true))
    }

    @Test
    fun `RTM only includes POSTURE`() {
        assertTrue(CorrectionType.POSTURE in CorrectionTypeAvailability.visibleFor(true))
        assertFalse(CorrectionType.POSTURE in CorrectionTypeAvailability.visibleFor(false))
    }

    @Test
    fun `RTM only includes ELBOW_BEND`() {
        assertTrue(CorrectionType.ELBOW_BEND in CorrectionTypeAvailability.visibleFor(true))
        assertFalse(CorrectionType.ELBOW_BEND in CorrectionTypeAvailability.visibleFor(false))
    }

    @Test
    fun `RTM only includes STROKE_SPEED`() {
        assertTrue(CorrectionType.STROKE_SPEED in CorrectionTypeAvailability.visibleFor(true))
        assertFalse(CorrectionType.STROKE_SPEED in CorrectionTypeAvailability.visibleFor(false))
    }

    @Test
    fun `GENERAL is in neither set`() {
        assertFalse(CorrectionType.GENERAL in CorrectionTypeAvailability.visibleFor(true))
        assertFalse(CorrectionType.GENERAL in CorrectionTypeAvailability.visibleFor(false))
    }
}
