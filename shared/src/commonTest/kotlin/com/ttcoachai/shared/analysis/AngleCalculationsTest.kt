/*
 * AI Coach for Table Tennis
 * AngleCalculationsTest — Unit tests for platform-independent angle calculation functions.
 */

package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Landmark3D
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AngleCalculationsTest {

    // ── calculate3DAngle ──────────────────────────────────────────────────────

    @Test
    fun calculate3DAngle_perpendicular_returns90() {
        val a = Landmark3D(1f, 0f, 0f)
        val b = Landmark3D(0f, 0f, 0f)  // vertex
        val c = Landmark3D(0f, 1f, 0f)
        assertEquals(90f, AngleCalculations.calculate3DAngle(a, b, c), absoluteTolerance = 0.01f)
    }

    @Test
    fun calculate3DAngle_collinear_returns180() {
        val a = Landmark3D(-1f, 0f, 0f)
        val b = Landmark3D(0f, 0f, 0f)
        val c = Landmark3D(1f, 0f, 0f)
        assertEquals(180f, AngleCalculations.calculate3DAngle(a, b, c), absoluteTolerance = 0.01f)
    }

    @Test
    fun calculate3DAngle_sameDirection_returns0() {
        val a = Landmark3D(1f, 0f, 0f)
        val b = Landmark3D(0f, 0f, 0f)
        val c = Landmark3D(2f, 0f, 0f)
        assertEquals(0f, AngleCalculations.calculate3DAngle(a, b, c), absoluteTolerance = 0.01f)
    }

    @Test
    fun calculate3DAngle_zeroMagnitude_returns0() {
        val coincident = Landmark3D(0f, 0f, 0f)
        assertEquals(0f, AngleCalculations.calculate3DAngle(coincident, coincident, coincident), absoluteTolerance = 0.01f)
    }

    @Test
    fun calculate3DAngle_known45degrees() {
        // b at origin, a along X, c along diagonal X+Y (45° between them)
        val a = Landmark3D(1f, 0f, 0f)
        val b = Landmark3D(0f, 0f, 0f)
        val c = Landmark3D(1f, 1f, 0f)
        assertEquals(45f, AngleCalculations.calculate3DAngle(a, b, c), absoluteTolerance = 0.1f)
    }

    // ── calculateWristAngle ───────────────────────────────────────────────────

    @Test
    fun calculateWristAngle_emptyList_returnsNull() {
        assertNull(AngleCalculations.calculateWristAngle(emptyList()))
    }

    @Test
    fun calculateWristAngle_insufficientLandmarks_returnsNull() {
        // Need indices 14, 16, 20 — list of 10 is too short
        assertNull(AngleCalculations.calculateWristAngle(makeLandmarks(10)))
    }

    @Test
    fun calculateWristAngle_validLandmarks_returnsKnownAngle() {
        val landmarks = makeLandmarks(33)
        // Place elbow(14), wrist(16), index(20) to form 90° at wrist
        landmarks[14] = Landmark3D(1f, 0f, 0f)
        landmarks[16] = Landmark3D(0f, 0f, 0f)
        landmarks[20] = Landmark3D(0f, 1f, 0f)
        val angle = AngleCalculations.calculateWristAngle(landmarks)
        assertNotNull(angle)
        assertEquals(90f, angle, absoluteTolerance = 0.01f)
    }

    @Test
    fun calculateWristAngle_straightWrist_returnsNear180() {
        val landmarks = makeLandmarks(33)
        // Straight arm: elbow → wrist → index all along X axis
        landmarks[14] = Landmark3D(2f, 0f, 0f)
        landmarks[16] = Landmark3D(1f, 0f, 0f)
        landmarks[20] = Landmark3D(0f, 0f, 0f)
        val angle = AngleCalculations.calculateWristAngle(landmarks)
        assertNotNull(angle)
        assertEquals(180f, angle, absoluteTolerance = 0.01f)
    }

    // ── calculateBodyRotation ─────────────────────────────────────────────────

    @Test
    fun calculateBodyRotation_emptyList_returnsNull() {
        assertNull(AngleCalculations.calculateBodyRotation(emptyList()))
    }

    @Test
    fun calculateBodyRotation_insufficientLandmarks_returnsNull() {
        assertNull(AngleCalculations.calculateBodyRotation(makeLandmarks(10)))
    }

    @Test
    fun calculateBodyRotation_parallelShoulderHip_returnsZero() {
        val landmarks = makeLandmarks(33)
        // Shoulders and hips both horizontal → same angle → rotation = 0
        landmarks[11] = Landmark3D(0f, 0f, 0f)
        landmarks[12] = Landmark3D(1f, 0f, 0f)
        landmarks[23] = Landmark3D(0f, 0.5f, 0f)
        landmarks[24] = Landmark3D(1f, 0.5f, 0f)
        val rotation = AngleCalculations.calculateBodyRotation(landmarks)
        assertNotNull(rotation)
        assertEquals(0f, rotation, absoluteTolerance = 0.01f)
    }

    @Test
    fun calculateBodyRotation_rotatedShoulder_returnsNonZero() {
        val landmarks = makeLandmarks(33)
        // Shoulders horizontal, hips at 45° → rotation ≈ 45°
        landmarks[11] = Landmark3D(0f, 0f, 0f)
        landmarks[12] = Landmark3D(1f, 0f, 0f)  // shoulder angle = 0°
        landmarks[23] = Landmark3D(0f, 0f, 0f)
        landmarks[24] = Landmark3D(1f, 1f, 0f)  // hip angle = 45°
        val rotation = AngleCalculations.calculateBodyRotation(landmarks)
        assertNotNull(rotation)
        assertEquals(45f, rotation, absoluteTolerance = 0.1f)
    }

    // ── calculateFollowThroughAngle ───────────────────────────────────────────

    @Test
    fun calculateFollowThroughAngle_emptyList_returnsNull() {
        assertNull(AngleCalculations.calculateFollowThroughAngle(emptyList()))
    }

    @Test
    fun calculateFollowThroughAngle_insufficientLandmarks_returnsNull() {
        assertNull(AngleCalculations.calculateFollowThroughAngle(makeLandmarks(10)))
    }

    @Test
    fun calculateFollowThroughAngle_validLandmarks_returnsKnownAngle() {
        val landmarks = makeLandmarks(33)
        // shoulder(12) → elbow(14) → wrist(16) form 90° at elbow
        landmarks[12] = Landmark3D(1f, 0f, 0f)
        landmarks[14] = Landmark3D(0f, 0f, 0f)
        landmarks[16] = Landmark3D(0f, 1f, 0f)
        val angle = AngleCalculations.calculateFollowThroughAngle(landmarks)
        assertNotNull(angle)
        assertEquals(90f, angle, absoluteTolerance = 0.01f)
    }

    @Test
    fun calculateFollowThroughAngle_straightArm_returnsNear180() {
        val landmarks = makeLandmarks(33)
        landmarks[12] = Landmark3D(2f, 0f, 0f)
        landmarks[14] = Landmark3D(1f, 0f, 0f)
        landmarks[16] = Landmark3D(0f, 0f, 0f)
        val angle = AngleCalculations.calculateFollowThroughAngle(landmarks)
        assertNotNull(angle)
        assertEquals(180f, angle, absoluteTolerance = 0.01f)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeLandmarks(count: Int): MutableList<Landmark3D> =
        MutableList(count) { Landmark3D(0.5f, 0.5f, 0f) }
}

private fun assertEquals(expected: Float, actual: Float, absoluteTolerance: Float) {
    val diff = kotlin.math.abs(expected - actual)
    if (diff > absoluteTolerance) {
        throw AssertionError("Expected $expected ± $absoluteTolerance but was $actual (diff=$diff)")
    }
}
