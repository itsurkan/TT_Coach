/*
 * AI Coach for Table Tennis
 * Stroke Snapshot Selector - picks the representative frame of a stroke rep for a static snapshot
 */

package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.Landmark3D
import kotlin.math.sqrt

/**
 * Selects which frame of a captured stroke rep (`FeedbackItem.strokeLandmarks`) best represents
 * the moment of interest for a static skeleton snapshot in the feedback-explanation bottom sheet.
 *
 * Mirrors the dominant wrist used throughout [com.ttcoachai.shared.analysis.AngleCalculations] /
 * [com.ttcoachai.shared.analysis.MetricCalculations] (landmark index 16 = right wrist), so the
 * snapshot lines up with what was actually measured.
 */
object StrokeSnapshotSelector {

    private const val WRIST_INDEX = 16
    private const val MIN_VISIBILITY = 0.5f

    /**
     * Index of the frame with the maximum 2D wrist speed (Euclidean distance between consecutive
     * frames' wrist landmark). Frames where the wrist's visibility is below [MIN_VISIBILITY] are
     * skipped both as a speed sample and as a candidate peak.
     *
     * - Empty list -> -1
     * - Fewer than 2 usable (visible-wrist) frames -> frames.size / 2
     */
    fun peakFrameIndex(frames: List<List<Landmark3D>>): Int {
        if (frames.isEmpty()) return -1

        val usableIndices = frames.indices.filter { isWristVisible(frames[it]) }
        if (usableIndices.size < 2) return frames.size / 2

        var bestIndex = usableIndices.first()
        var bestSpeed = -1f

        for (i in 1 until usableIndices.size) {
            val prevIdx = usableIndices[i - 1]
            val currIdx = usableIndices[i]
            val prevWrist = frames[prevIdx].getOrNull(WRIST_INDEX) ?: continue
            val currWrist = frames[currIdx].getOrNull(WRIST_INDEX) ?: continue

            val dx = currWrist.x - prevWrist.x
            val dy = currWrist.y - prevWrist.y
            val speed = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            if (speed > bestSpeed) {
                bestSpeed = speed
                bestIndex = currIdx
            }
        }

        return bestIndex
    }

    /**
     * Index of the frame representing ball contact. Currently an alias of [peakFrameIndex]:
     * contact time ≈ wrist-speed peak for a fixed forehand-drive drill. Kept as a distinct
     * entry point so callers express intent and this can diverge later (e.g. once a dedicated
     * contact-frame detector exists).
     */
    fun contactFrameIndex(frames: List<List<Landmark3D>>): Int = peakFrameIndex(frames)

    private fun isWristVisible(frame: List<Landmark3D>): Boolean {
        val wrist = frame.getOrNull(WRIST_INDEX) ?: return false
        return wrist.visibility >= MIN_VISIBILITY
    }
}
