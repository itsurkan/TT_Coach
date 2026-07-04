/*
 * AI Coach for Table Tennis
 * Stroke Snapshot Selector - picks the representative frame of a stroke rep for a static snapshot
 */

package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.Landmark3D
import kotlin.math.abs
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
    private const val SNAPSHOT_WINDOW = 4

    /** Landmark indices used to judge pose-render completeness for [bestSnapshotFrameIndex]. */
    private val CORE_LANDMARKS = intArrayOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)

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
     * contact-frame detector exists). See [bestSnapshotFrameIndex] for the render-quality-aware
     * choice, which searches a small window around this peak for a more completely-tracked frame.
     */
    fun contactFrameIndex(frames: List<List<Landmark3D>>): Int = peakFrameIndex(frames)

    /**
     * Like [peakFrameIndex], but guards against motion-blur frames where many landmarks drop
     * below [MIN_VISIBILITY] and the rendered skeleton comes out malformed. Searches a small
     * window of candidates around the wrist-speed peak (± [SNAPSHOT_WINDOW] frames, clamped to
     * the sequence bounds) and picks the most completely-tracked one instead.
     *
     * Scoring per candidate, in priority order:
     * 1. Count of [CORE_LANDMARKS] with `visibility >= MIN_VISIBILITY` (higher wins).
     * 2. Mean visibility over [CORE_LANDMARKS] (higher wins).
     * 3. Distance from the peak index (closer wins).
     * 4. Frame index (lower wins).
     *
     * - Empty list -> -1 (mirrors [peakFrameIndex])
     */
    fun bestSnapshotFrameIndex(frames: List<List<Landmark3D>>): Int {
        val peak = peakFrameIndex(frames)
        if (peak < 0) return -1

        val windowStart = (peak - SNAPSHOT_WINDOW).coerceAtLeast(0)
        val windowEnd = (peak + SNAPSHOT_WINDOW).coerceAtMost(frames.size - 1)

        var bestIndex = peak
        var bestCount = -1
        var bestMeanVisibility = -1f

        for (idx in windowStart..windowEnd) {
            val frame = frames[idx]
            val (count, meanVisibility) = coreCompleteness(frame)

            val isBetter = when {
                count != bestCount -> count > bestCount
                meanVisibility != bestMeanVisibility -> meanVisibility > bestMeanVisibility
                else -> {
                    val bestDistance = abs(bestIndex - peak)
                    val candidateDistance = abs(idx - peak)
                    when {
                        candidateDistance != bestDistance -> candidateDistance < bestDistance
                        else -> idx < bestIndex
                    }
                }
            }

            if (isBetter) {
                bestCount = count
                bestMeanVisibility = meanVisibility
                bestIndex = idx
            }
        }

        return bestIndex
    }

    private fun coreCompleteness(frame: List<Landmark3D>): Pair<Int, Float> {
        var visibleCount = 0
        var visibilitySum = 0f

        for (index in CORE_LANDMARKS) {
            val visibility = frame.getOrNull(index)?.visibility ?: 0f
            visibilitySum += visibility
            if (visibility >= MIN_VISIBILITY) visibleCount++
        }

        val meanVisibility = visibilitySum / CORE_LANDMARKS.size
        return visibleCount to meanVisibility
    }

    private fun isWristVisible(frame: List<Landmark3D>): Boolean {
        val wrist = frame.getOrNull(WRIST_INDEX) ?: return false
        return wrist.visibility >= MIN_VISIBILITY
    }
}
