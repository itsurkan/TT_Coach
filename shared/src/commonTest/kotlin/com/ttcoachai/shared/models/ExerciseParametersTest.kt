/*
 * AI Coach for Table Tennis
 * ExerciseParametersTest — Unit tests for ExerciseParameters presets and validation methods.
 */

package com.ttcoachai.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseParametersTest {

    // ── Preset values — forehandDrive() ──────────────────────────────────────

    @Test
    fun forehandDrive_exerciseId() {
        assertEquals("forehand_drive", ExerciseParameters.forehandDrive().exerciseId)
    }

    @Test
    fun forehandDrive_wristAngle() {
        val p = ExerciseParameters.forehandDrive()
        assertEquals(165f, p.idealWristAngle)
        assertEquals(5f, p.wristAngleTolerance)
    }

    @Test
    fun forehandDrive_bodyRotation() {
        val p = ExerciseParameters.forehandDrive()
        assertEquals(45f, p.minBodyRotation)
        assertEquals(10f, p.bodyRotationTolerance)
    }

    @Test
    fun forehandDrive_followThrough() {
        val p = ExerciseParameters.forehandDrive()
        assertEquals(120f, p.followThroughAngle)
        assertEquals(20f, p.followThroughTolerance)
    }

    @Test
    fun forehandDrive_contactHeight() {
        val p = ExerciseParameters.forehandDrive()
        assertEquals(0.8f, p.contactHeightMin)
        assertEquals(1.0f, p.contactHeightMax)
    }

    // ── Preset values — backhandDrive() ──────────────────────────────────────

    @Test
    fun backhandDrive_exerciseId() {
        assertEquals("backhand_drive", ExerciseParameters.backhandDrive().exerciseId)
    }

    @Test
    fun backhandDrive_wristAngle() {
        val p = ExerciseParameters.backhandDrive()
        assertEquals(175f, p.idealWristAngle)
        assertEquals(10f, p.wristAngleTolerance)
    }

    @Test
    fun backhandDrive_minBodyRotation() {
        assertEquals(40f, ExerciseParameters.backhandDrive().minBodyRotation)
    }

    // ── Preset values — forehandDriveBeginner() ───────────────────────────────

    @Test
    fun forehandDriveBeginner_exerciseId() {
        assertEquals("forehand_drive_beginner", ExerciseParameters.forehandDriveBeginner().exerciseId)
    }

    @Test
    fun forehandDriveBeginner_veryLooseWrist() {
        val p = ExerciseParameters.forehandDriveBeginner()
        assertEquals(180f, p.idealWristAngle)
        assertEquals(60f, p.wristAngleTolerance)  // range [120, 240]
    }

    @Test
    fun forehandDriveBeginner_allowsVeryLowContact() {
        val p = ExerciseParameters.forehandDriveBeginner()
        assertEquals(-2.0f, p.contactHeightMin)
        assertEquals(2.0f, p.contactHeightMax)
    }

    // ── isWristAngleValid ─────────────────────────────────────────────────────

    @Test
    fun isWristAngleValid_idealAngle_returnsTrue() {
        val p = ExerciseParameters.forehandDrive()  // ideal=165, tol=5 → [160, 170]
        assertTrue(p.isWristAngleValid(165f))
    }

    @Test
    fun isWristAngleValid_atLowerBound_returnsTrue() {
        val p = ExerciseParameters.forehandDrive()
        assertTrue(p.isWristAngleValid(160f))
    }

    @Test
    fun isWristAngleValid_atUpperBound_returnsTrue() {
        val p = ExerciseParameters.forehandDrive()
        assertTrue(p.isWristAngleValid(170f))
    }

    @Test
    fun isWristAngleValid_belowRange_returnsFalse() {
        val p = ExerciseParameters.forehandDrive()
        assertFalse(p.isWristAngleValid(159f))
    }

    @Test
    fun isWristAngleValid_aboveRange_returnsFalse() {
        val p = ExerciseParameters.forehandDrive()
        assertFalse(p.isWristAngleValid(171f))
    }

    // ── isBodyRotationValid ───────────────────────────────────────────────────

    @Test
    fun isBodyRotationValid_sufficient_returnsTrue() {
        val p = ExerciseParameters.forehandDrive()  // min=45, tol=10 → threshold=35
        assertTrue(p.isBodyRotationValid(35f))
        assertTrue(p.isBodyRotationValid(50f))
        assertTrue(p.isBodyRotationValid(90f))
    }

    @Test
    fun isBodyRotationValid_belowThreshold_returnsFalse() {
        val p = ExerciseParameters.forehandDrive()  // threshold = 35
        assertFalse(p.isBodyRotationValid(34f))
        assertFalse(p.isBodyRotationValid(0f))
    }

    // ── isFollowThroughValid ──────────────────────────────────────────────────

    @Test
    fun isFollowThroughValid_inRange_returnsTrue() {
        val p = ExerciseParameters.forehandDrive()  // [100, 140]
        assertTrue(p.isFollowThroughValid(100f))
        assertTrue(p.isFollowThroughValid(120f))
        assertTrue(p.isFollowThroughValid(140f))
    }

    @Test
    fun isFollowThroughValid_outsideRange_returnsFalse() {
        val p = ExerciseParameters.forehandDrive()
        assertFalse(p.isFollowThroughValid(99f))
        assertFalse(p.isFollowThroughValid(141f))
    }

    @Test
    fun isFollowThroughValid_beginnerAllowsAll() {
        val p = ExerciseParameters.forehandDriveBeginner()  // [0, 180]
        assertTrue(p.isFollowThroughValid(0f))
        assertTrue(p.isFollowThroughValid(90f))
        assertTrue(p.isFollowThroughValid(180f))
    }

    // ── isContactHeightValid ──────────────────────────────────────────────────

    @Test
    fun isContactHeightValid_inRange_returnsTrue() {
        val p = ExerciseParameters.forehandDrive()  // [0.8, 1.0]
        assertTrue(p.isContactHeightValid(0.8f))
        assertTrue(p.isContactHeightValid(0.9f))
        assertTrue(p.isContactHeightValid(1.0f))
    }

    @Test
    fun isContactHeightValid_outsideRange_returnsFalse() {
        val p = ExerciseParameters.forehandDrive()
        assertFalse(p.isContactHeightValid(0.79f))
        assertFalse(p.isContactHeightValid(1.01f))
    }

    // ── isElbowPositionValid + isElbowNotTooClose ─────────────────────────────

    @Test
    fun isElbowPositionValid_withinMax_returnsTrue() {
        val p = ExerciseParameters.forehandDrive()  // max=0.25
        assertTrue(p.isElbowPositionValid(0.25f))
        assertTrue(p.isElbowPositionValid(0.1f))
    }

    @Test
    fun isElbowPositionValid_exceedsMax_returnsFalse() {
        val p = ExerciseParameters.forehandDrive()
        assertFalse(p.isElbowPositionValid(0.26f))
    }

    @Test
    fun isElbowNotTooClose_atMin_returnsTrue() {
        val p = ExerciseParameters.forehandDrive()  // min=0.12
        assertTrue(p.isElbowNotTooClose(0.12f))
        assertTrue(p.isElbowNotTooClose(0.2f))
    }

    @Test
    fun isElbowNotTooClose_belowMin_returnsFalse() {
        val p = ExerciseParameters.forehandDrive()
        assertFalse(p.isElbowNotTooClose(0.11f))
    }

    // ── isStrokeSpeedValid ────────────────────────────────────────────────────

    @Test
    fun isStrokeSpeedValid_inRange_returnsTrue() {
        val p = ExerciseParameters.forehandDrive()  // [1.5, 4.0]
        assertTrue(p.isStrokeSpeedValid(1.5f))
        assertTrue(p.isStrokeSpeedValid(2.5f))
        assertTrue(p.isStrokeSpeedValid(4.0f))
    }

    @Test
    fun isStrokeSpeedValid_outsideRange_returnsFalse() {
        val p = ExerciseParameters.forehandDrive()
        assertFalse(p.isStrokeSpeedValid(1.4f))
        assertFalse(p.isStrokeSpeedValid(4.1f))
    }
}
