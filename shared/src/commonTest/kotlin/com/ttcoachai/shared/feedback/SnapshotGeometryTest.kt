/*
 * AI Coach for Table Tennis
 * SnapshotGeometryTest — Unit tests for 2D joint-angle math and correction-type highlight mapping.
 */

package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Landmark3D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapshotGeometryTest {

    // ── angleAtJoint ──────────────────────────────────────────────────────────

    @Test
    fun angleAtJoint_perpendicular_returns90() {
        val frame = makeFrame(33)
        frame[14] = Landmark3D(1f, 0f, 0f, visibility = 1f)
        frame[16] = Landmark3D(0f, 0f, 0f, visibility = 1f) // vertex
        frame[20] = Landmark3D(0f, 1f, 0f, visibility = 1f)

        val angle = SnapshotGeometry.angleAtJoint(frame, a = 14, vertex = 16, b = 20)
        assertNotNull(angle)
        assertEqualsTol(90f, angle)
    }

    @Test
    fun angleAtJoint_collinear_returns180() {
        val frame = makeFrame(33)
        frame[12] = Landmark3D(2f, 0f, 0f, visibility = 1f)
        frame[14] = Landmark3D(1f, 0f, 0f, visibility = 1f) // vertex
        frame[16] = Landmark3D(0f, 0f, 0f, visibility = 1f)

        val angle = SnapshotGeometry.angleAtJoint(frame, a = 12, vertex = 14, b = 16)
        assertNotNull(angle)
        assertEqualsTol(180f, angle)
    }

    @Test
    fun angleAtJoint_ignoresZ() {
        // Same x,y as the perpendicular case but with differing z — result must be unaffected.
        val frame = makeFrame(33)
        frame[14] = Landmark3D(1f, 0f, 5f, visibility = 1f)
        frame[16] = Landmark3D(0f, 0f, -3f, visibility = 1f) // vertex
        frame[20] = Landmark3D(0f, 1f, 9f, visibility = 1f)

        val angle = SnapshotGeometry.angleAtJoint(frame, a = 14, vertex = 16, b = 20)
        assertNotNull(angle)
        assertEqualsTol(90f, angle)
    }

    @Test
    fun angleAtJoint_lowVisibilityVertex_returnsNull() {
        val frame = makeFrame(33)
        frame[14] = Landmark3D(1f, 0f, 0f, visibility = 1f)
        frame[16] = Landmark3D(0f, 0f, 0f, visibility = 0.1f) // low-visibility vertex
        frame[20] = Landmark3D(0f, 1f, 0f, visibility = 1f)

        assertNull(SnapshotGeometry.angleAtJoint(frame, a = 14, vertex = 16, b = 20))
    }

    @Test
    fun angleAtJoint_lowVisibilityEndpoint_returnsNull() {
        val frame = makeFrame(33)
        frame[14] = Landmark3D(1f, 0f, 0f, visibility = 0.2f) // low-visibility endpoint
        frame[16] = Landmark3D(0f, 0f, 0f, visibility = 1f)
        frame[20] = Landmark3D(0f, 1f, 0f, visibility = 1f)

        assertNull(SnapshotGeometry.angleAtJoint(frame, a = 14, vertex = 16, b = 20))
    }

    @Test
    fun angleAtJoint_missingLandmark_returnsNull() {
        val frame = makeFrame(10) // indices 14/16/20 out of bounds
        assertNull(SnapshotGeometry.angleAtJoint(frame, a = 14, vertex = 16, b = 20))
    }

    @Test
    fun angleAtJoint_emptyFrame_returnsNull() {
        assertNull(SnapshotGeometry.angleAtJoint(emptyList(), a = 14, vertex = 16, b = 20))
    }

    @Test
    fun angleAtJoint_zeroMagnitudeVector_returnsNull() {
        val frame = makeFrame(33)
        frame[14] = Landmark3D(0f, 0f, 0f, visibility = 1f) // coincident with vertex
        frame[16] = Landmark3D(0f, 0f, 0f, visibility = 1f)
        frame[20] = Landmark3D(0f, 1f, 0f, visibility = 1f)

        assertNull(SnapshotGeometry.angleAtJoint(frame, a = 14, vertex = 16, b = 20))
    }

    // ── highlightFor ──────────────────────────────────────────────────────────

    @Test
    fun highlightFor_wrist_hasArcAtWristTriple() {
        val highlight = SnapshotGeometry.highlightFor(CorrectionType.WRIST)
        assertTrue(highlight.showArc)
        assertEquals(Triple(14, 16, 20), highlight.jointTriple)
        assertTrue(highlight.highlightJoints.containsAll(listOf(14, 16, 20)))
    }

    @Test
    fun highlightFor_followThrough_hasArcAtShoulderElbowWristTriple() {
        val highlight = SnapshotGeometry.highlightFor(CorrectionType.FOLLOW_THROUGH)
        assertTrue(highlight.showArc)
        assertEquals(Triple(12, 14, 16), highlight.jointTriple)
        assertTrue(highlight.highlightJoints.containsAll(listOf(12, 14, 16)))
    }

    @Test
    fun highlightFor_elbowPosition_noArc_highlightsElbowAndHip() {
        val highlight = SnapshotGeometry.highlightFor(CorrectionType.ELBOW_POSITION)
        assertEquals(false, highlight.showArc)
        assertNull(highlight.jointTriple)
        assertTrue(highlight.highlightJoints.containsAll(listOf(14, 24)))
    }

    @Test
    fun highlightFor_bodyRotation_noArc_highlightsShouldersAndHips() {
        val highlight = SnapshotGeometry.highlightFor(CorrectionType.BODY_ROTATION)
        assertEquals(false, highlight.showArc)
        assertNull(highlight.jointTriple)
        assertTrue(highlight.highlightJoints.containsAll(listOf(11, 12, 23, 24)))
    }

    @Test
    fun highlightFor_contactHeight_noArc_highlightsWristShoulderHip() {
        val highlight = SnapshotGeometry.highlightFor(CorrectionType.CONTACT_HEIGHT)
        assertEquals(false, highlight.showArc)
        assertNull(highlight.jointTriple)
        assertTrue(highlight.highlightJoints.containsAll(listOf(16, 12, 24)))
    }

    @Test
    fun highlightFor_strokeSpeed_noArc_highlightsWristOnly() {
        val highlight = SnapshotGeometry.highlightFor(CorrectionType.STROKE_SPEED)
        assertEquals(false, highlight.showArc)
        assertNull(highlight.jointTriple)
        assertEquals(listOf(16), highlight.highlightJoints)
    }

    @Test
    fun highlightFor_general_noArc_emptyHighlights() {
        val highlight = SnapshotGeometry.highlightFor(CorrectionType.GENERAL)
        assertEquals(false, highlight.showArc)
        assertNull(highlight.jointTriple)
        assertTrue(highlight.highlightJoints.isEmpty())
    }

    @Test
    fun highlightFor_onlyWristAndFollowThroughShowArc() {
        val arcTypes = CorrectionType.entries.filter { SnapshotGeometry.highlightFor(it).showArc }
        assertEquals(setOf(CorrectionType.WRIST, CorrectionType.FOLLOW_THROUGH), arcTypes.toSet())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeFrame(count: Int): MutableList<Landmark3D> =
        MutableList(count) { Landmark3D(0.5f, 0.5f, 0f, visibility = 1f) }

    private fun assertEqualsTol(expected: Float, actual: Float, tolerance: Float = 0.1f) {
        val diff = kotlin.math.abs(expected - actual)
        if (diff > tolerance) {
            throw AssertionError("Expected $expected ± $tolerance but was $actual (diff=$diff)")
        }
    }
}
