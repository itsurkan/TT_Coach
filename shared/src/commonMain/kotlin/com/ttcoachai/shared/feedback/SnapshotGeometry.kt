/*
 * AI Coach for Table Tennis
 * Snapshot Geometry - 2D angle math + per-correction-type highlight mapping for stroke snapshots
 */

package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Landmark3D
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sqrt

private fun toDegrees(radians: Double): Double = radians * 180.0 / PI

/**
 * Pure geometry helpers for rendering a static skeleton snapshot: a 2D-projected joint angle plus
 * a mapping from [CorrectionType] to which joints/arc to highlight. Landmark indices mirror those
 * used by [com.ttcoachai.shared.analysis.AngleCalculations] / [com.ttcoachai.shared.analysis.MetricCalculations]
 * / [com.ttcoachai.shared.analysis.StrokeAnalyzer] so the snapshot matches what was actually measured.
 */
object SnapshotGeometry {

    private const val MIN_VISIBILITY = 0.5f

    // Mirrors AngleCalculations.calculateWristAngle: 14 = right elbow, 16 = right wrist, 20 = right index
    private const val ELBOW = 14
    private const val WRIST = 16
    private const val INDEX_FINGER = 20

    // Mirrors AngleCalculations.calculateFollowThroughAngle: 12 = right shoulder, 14 = right elbow, 16 = right wrist
    private const val SHOULDER_R = 12
    private const val SHOULDER_L = 11

    // Mirrors MetricCalculations.calculateElbowBodyDistance / calculateContactHeight: 24 = right hip
    private const val HIP_R = 24
    private const val HIP_L = 23

    /**
     * 2D-projected angle in degrees at [vertex] between rays to [a] and [b] (x,y only, ignores z).
     * Null if any of the three landmarks is missing (index out of bounds) or has
     * visibility < [MIN_VISIBILITY].
     */
    fun angleAtJoint(frame: List<Landmark3D>, a: Int, vertex: Int, b: Int): Float? {
        val pointA = frame.getOrNull(a) ?: return null
        val pointVertex = frame.getOrNull(vertex) ?: return null
        val pointB = frame.getOrNull(b) ?: return null

        if (pointA.visibility < MIN_VISIBILITY ||
            pointVertex.visibility < MIN_VISIBILITY ||
            pointB.visibility < MIN_VISIBILITY
        ) {
            return null
        }

        val vax = pointA.x - pointVertex.x
        val vay = pointA.y - pointVertex.y

        val vbx = pointB.x - pointVertex.x
        val vby = pointB.y - pointVertex.y

        val dotProduct = vax * vbx + vay * vby
        val magVA = sqrt((vax * vax + vay * vay).toDouble())
        val magVB = sqrt((vbx * vbx + vby * vby).toDouble())

        if (magVA == 0.0 || magVB == 0.0) return null

        val cosAngle = (dotProduct / (magVA * magVB)).coerceIn(-1.0, 1.0)
        val angle = toDegrees(acos(cosAngle)).toFloat()
        return if (angle.isNaN() || angle.isInfinite()) null else angle
    }

    /**
     * Which joint triple (for the angle arc) and which joints to highlight on the snapshot
     * skeleton for a given [CorrectionType].
     *
     * @property jointTriple (a, vertex, b) indices for the angle arc, or null if this correction
     *   type has no associated angle (arc not drawn).
     * @property highlightJoints landmark indices to visually emphasize on the skeleton.
     * @property showArc whether to render the angle arc for [jointTriple]. Rotational metrics are
     *   qualitative-only per the repo trust rule, so BODY_ROTATION never shows a precise arc.
     */
    data class SnapshotHighlight(
        val jointTriple: Triple<Int, Int, Int>?,
        val highlightJoints: List<Int>,
        val showArc: Boolean
    )

    fun highlightFor(type: CorrectionType): SnapshotHighlight = when (type) {
        CorrectionType.WRIST -> SnapshotHighlight(
            jointTriple = Triple(ELBOW, WRIST, INDEX_FINGER),
            highlightJoints = listOf(ELBOW, WRIST, INDEX_FINGER),
            showArc = true
        )

        CorrectionType.FOLLOW_THROUGH -> SnapshotHighlight(
            jointTriple = Triple(SHOULDER_R, ELBOW, WRIST),
            highlightJoints = listOf(SHOULDER_R, ELBOW, WRIST),
            showArc = true
        )

        CorrectionType.ELBOW_POSITION -> SnapshotHighlight(
            jointTriple = null,
            highlightJoints = listOf(ELBOW, HIP_R),
            showArc = false
        )

        CorrectionType.BODY_ROTATION -> SnapshotHighlight(
            jointTriple = null,
            highlightJoints = listOf(SHOULDER_L, SHOULDER_R, HIP_L, HIP_R),
            showArc = false
        )

        CorrectionType.CONTACT_HEIGHT -> SnapshotHighlight(
            jointTriple = null,
            highlightJoints = listOf(WRIST, SHOULDER_R, HIP_R),
            showArc = false
        )

        CorrectionType.STROKE_SPEED -> SnapshotHighlight(
            jointTriple = null,
            highlightJoints = listOf(WRIST),
            showArc = false
        )

        CorrectionType.GENERAL -> SnapshotHighlight(
            jointTriple = null,
            highlightJoints = emptyList(),
            showArc = false
        )
    }
}
