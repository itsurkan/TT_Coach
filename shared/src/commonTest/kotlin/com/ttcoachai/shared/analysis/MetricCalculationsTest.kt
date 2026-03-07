/*
 * AI Coach for Table Tennis
 * MetricCalculationsTest — Unit tests for platform-independent metric calculation functions.
 */

package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.Landmark3D
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MetricCalculationsTest {

    // ── calculateContactHeight ────────────────────────────────────────────────

    @Test
    fun calculateContactHeight_emptyList_returnsNull() {
        assertNull(MetricCalculations.calculateContactHeight(emptyList()))
    }

    @Test
    fun calculateContactHeight_missingWrist_returnsNull() {
        assertNull(MetricCalculations.calculateContactHeight(makeLandmarks(10)))
    }

    @Test
    fun calculateContactHeight_zeroHipY_returnsNull() {
        val landmarks = makeLandmarks(33)
        landmarks[16] = Landmark3D(0.5f, 0.5f, 0f)  // wrist
        landmarks[24] = Landmark3D(0.5f, 0f, 0f)     // hip.y = 0 → null
        assertNull(MetricCalculations.calculateContactHeight(landmarks))
    }

    @Test
    fun calculateContactHeight_wristAtHipLevel_returnsZero() {
        val landmarks = makeLandmarks(33)
        landmarks[16] = Landmark3D(0.5f, 1.0f, 0f)  // wrist.y = hip.y
        landmarks[24] = Landmark3D(0.5f, 1.0f, 0f)  // hip.y = 1.0
        // height = 1 - 1.0/1.0 = 0.0
        val height = MetricCalculations.calculateContactHeight(landmarks)
        assertNotNull(height)
        assertEquals(0f, height, absoluteTolerance = 0.001f)
    }

    @Test
    fun calculateContactHeight_wristAboveHip_returnsPositive() {
        val landmarks = makeLandmarks(33)
        landmarks[16] = Landmark3D(0.5f, 0.5f, 0f)  // wrist.y = 0.5
        landmarks[24] = Landmark3D(0.5f, 1.0f, 0f)  // hip.y = 1.0
        // height = 1 - 0.5/1.0 = 0.5
        val height = MetricCalculations.calculateContactHeight(landmarks)
        assertNotNull(height)
        assertEquals(0.5f, height, absoluteTolerance = 0.001f)
    }

    @Test
    fun calculateContactHeight_wristBelowHip_returnsNegative() {
        val landmarks = makeLandmarks(33)
        landmarks[16] = Landmark3D(0.5f, 1.5f, 0f)  // wrist.y = 1.5 (below hip in image coords)
        landmarks[24] = Landmark3D(0.5f, 1.0f, 0f)  // hip.y = 1.0
        // height = 1 - 1.5/1.0 = -0.5
        val height = MetricCalculations.calculateContactHeight(landmarks)
        assertNotNull(height)
        assertEquals(-0.5f, height, absoluteTolerance = 0.001f)
    }

    // ── calculateElbowBodyDistance ────────────────────────────────────────────

    @Test
    fun calculateElbowBodyDistance_emptyList_returnsNull() {
        assertNull(MetricCalculations.calculateElbowBodyDistance(emptyList()))
    }

    @Test
    fun calculateElbowBodyDistance_missingElbow_returnsNull() {
        assertNull(MetricCalculations.calculateElbowBodyDistance(makeLandmarks(10)))
    }

    @Test
    fun calculateElbowBodyDistance_samePosition_returnsZero() {
        val landmarks = makeLandmarks(33)
        landmarks[14] = Landmark3D(0.5f, 0.5f, 0f)  // elbow
        landmarks[24] = Landmark3D(0.5f, 0.5f, 0f)  // hip (same position)
        val dist = MetricCalculations.calculateElbowBodyDistance(landmarks)
        assertNotNull(dist)
        assertEquals(0f, dist, absoluteTolerance = 0.001f)
    }

    @Test
    fun calculateElbowBodyDistance_knownDistance_returnsCorrect() {
        val landmarks = makeLandmarks(33)
        landmarks[14] = Landmark3D(0.3f, 0.4f, 0f)  // elbow
        landmarks[24] = Landmark3D(0f, 0f, 0f)      // hip at origin
        // distance = sqrt(0.3^2 + 0.4^2) = sqrt(0.09 + 0.16) = sqrt(0.25) = 0.5
        val dist = MetricCalculations.calculateElbowBodyDistance(landmarks)
        assertNotNull(dist)
        assertEquals(0.5f, dist, absoluteTolerance = 0.001f)
    }

    @Test
    fun calculateElbowBodyDistance_alwaysNonNegative() {
        val landmarks = makeLandmarks(33)
        landmarks[14] = Landmark3D(-0.3f, -0.4f, 0f)  // negative coordinates
        landmarks[24] = Landmark3D(0f, 0f, 0f)
        val dist = MetricCalculations.calculateElbowBodyDistance(landmarks)
        assertNotNull(dist)
        assert(dist >= 0f) { "Distance must be non-negative, was $dist" }
    }

    // ── calculateStrokeSpeed ──────────────────────────────────────────────────

    @Test
    fun calculateStrokeSpeed_emptyCurrentLandmarks_returnsNull() {
        assertNull(MetricCalculations.calculateStrokeSpeed(emptyList(), makeLandmarks(33), 100L))
    }

    @Test
    fun calculateStrokeSpeed_emptyPreviousLandmarks_returnsNull() {
        assertNull(MetricCalculations.calculateStrokeSpeed(makeLandmarks(33), emptyList(), 100L))
    }

    @Test
    fun calculateStrokeSpeed_zeroTimeDelta_returnsNull() {
        assertNull(MetricCalculations.calculateStrokeSpeed(makeLandmarks(33), makeLandmarks(33), 0L))
    }

    @Test
    fun calculateStrokeSpeed_negativeTimeDelta_returnsNull() {
        assertNull(MetricCalculations.calculateStrokeSpeed(makeLandmarks(33), makeLandmarks(33), -100L))
    }

    @Test
    fun calculateStrokeSpeed_noWristMovement_returnsZero() {
        val current = makeLandmarks(33)
        val previous = makeLandmarks(33)
        // Both wrists at same position
        current[16] = Landmark3D(0.5f, 0.5f, 0f)
        previous[16] = Landmark3D(0.5f, 0.5f, 0f)
        val speed = MetricCalculations.calculateStrokeSpeed(current, previous, 100L)
        assertNotNull(speed)
        assertEquals(0f, speed, absoluteTolerance = 0.001f)
    }

    @Test
    fun calculateStrokeSpeed_knownDisplacement_returnsCorrectSpeed() {
        val current = makeLandmarks(33)
        val previous = makeLandmarks(33)
        // Wrist moved 0.3 in X, 0.4 in Y over 500ms → displacement=0.5, speed=1.0 unit/s
        current[16]  = Landmark3D(0.8f, 0.9f, 0f)
        previous[16] = Landmark3D(0.5f, 0.5f, 0f)
        val speed = MetricCalculations.calculateStrokeSpeed(current, previous, 500L)
        assertNotNull(speed)
        // speed = 0.5 / 500 * 1000 = 1.0
        assertEquals(1.0f, speed, absoluteTolerance = 0.001f)
    }

    @Test
    fun calculateStrokeSpeed_3dDisplacement_returnsCorrectSpeed() {
        val current = makeLandmarks(33)
        val previous = makeLandmarks(33)
        // Move 1 unit in each of X, Y, Z → displacement = sqrt(3), over 1000ms
        current[16]  = Landmark3D(1f, 1f, 1f)
        previous[16] = Landmark3D(0f, 0f, 0f)
        val speed = MetricCalculations.calculateStrokeSpeed(current, previous, 1000L)
        assertNotNull(speed)
        val expected = sqrt(3.0).toFloat()
        assertEquals(expected, speed, absoluteTolerance = 0.001f)
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
