/*
 * AI Coach for Table Tennis
 * Stroke Snapshot Selector - picks the representative frame of a stroke rep for a static snapshot
 */

package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Landmark3D
import kotlin.math.abs
import kotlin.math.sign
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
    private const val HIP_INDEX = 24
    private const val MIN_VISIBILITY = 0.5f
    private const val SNAPSHOT_WINDOW = 4
    private const val MIN_CORE_VISIBLE = 6

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
     * Index of the frame representing ball contact.
     *
     * The wrist-speed peak ([peakFrameIndex]) is a proxy for contact, but on real footage it can
     * drift up to a few frames away from the actual moment the racket meets the ball. This uses a
     * sturdier proxy: the frame where the wrist reaches its farthest forward extension (relative
     * to the hip) in the stroke's direction of travel — i.e. the point of maximum reach into the
     * shot, which tracks contact more reliably than raw wrist speed.
     *
     * Algorithm:
     * 1. `peak` = [peakFrameIndex]; if negative, propagate -1.
     * 2. Stroke direction `d` = sign of `wrist.x[peak] − wrist.x[prevUsable]`, where `prevUsable`
     *    is the nearest earlier frame with a visible wrist. If there is no such frame, or the
     *    direction is degenerate (zero delta), fall back to [bestSnapshotFrameIndex].
     * 3. Among frames where the wrist (16) and hip (24) are both visible
     *    (`visibility >= [MIN_VISIBILITY]`) and at least [MIN_CORE_VISIBLE] of the
     *    [CORE_LANDMARKS] are visible, pick the one maximizing `d * (wrist.x − hip.x)` — the
     *    farthest forward wrist extension in the stroke direction, on a renderable frame.
     * 4. If no frame qualifies, fall back to [bestSnapshotFrameIndex].
     */
    fun contactFrameIndex(frames: List<List<Landmark3D>>): Int {
        val peak = peakFrameIndex(frames)
        if (peak < 0) return -1

        val prevUsable = (peak - 1 downTo 0).firstOrNull { isWristVisible(frames[it]) }
        if (prevUsable == null) return bestSnapshotFrameIndex(frames)

        val peakWristX = frames[peak].getOrNull(WRIST_INDEX)?.x
        val prevWristX = frames[prevUsable].getOrNull(WRIST_INDEX)?.x
        if (peakWristX == null || prevWristX == null) return bestSnapshotFrameIndex(frames)

        val direction = sign(peakWristX - prevWristX)
        if (direction == 0f) return bestSnapshotFrameIndex(frames)

        var bestIndex = -1
        var bestExtension = Float.NEGATIVE_INFINITY

        for (idx in frames.indices) {
            val frame = frames[idx]
            val wrist = frame.getOrNull(WRIST_INDEX) ?: continue
            val hip = frame.getOrNull(HIP_INDEX) ?: continue
            if (wrist.visibility < MIN_VISIBILITY || hip.visibility < MIN_VISIBILITY) continue

            val (coreCount, _) = coreCompleteness(frame)
            if (coreCount < MIN_CORE_VISIBLE) continue

            val extension = direction * (wrist.x - hip.x)
            if (extension > bestExtension) {
                bestExtension = extension
                bestIndex = idx
            }
        }

        return if (bestIndex >= 0) bestIndex else bestSnapshotFrameIndex(frames)
    }

    /**
     * Index of the frame representing the follow-through: among frames *after* [contactFrameIndex]
     * with a visible wrist and at least [MIN_CORE_VISIBLE] visible [CORE_LANDMARKS], the one with
     * the minimum wrist `y` (highest raised wrist — normalized y grows downward).
     *
     * Falls back to [contactFrameIndex] if no qualifying frame exists after contact.
     */
    fun followThroughFrameIndex(frames: List<List<Landmark3D>>): Int {
        val contact = contactFrameIndex(frames)
        if (contact < 0) return contact

        var bestIndex = -1
        var bestWristY = Float.POSITIVE_INFINITY

        for (idx in (contact + 1) until frames.size) {
            val frame = frames[idx]
            val wrist = frame.getOrNull(WRIST_INDEX) ?: continue
            if (wrist.visibility < MIN_VISIBILITY) continue

            val (coreCount, _) = coreCompleteness(frame)
            if (coreCount < MIN_CORE_VISIBLE) continue

            if (wrist.y < bestWristY) {
                bestWristY = wrist.y
                bestIndex = idx
            }
        }

        return if (bestIndex >= 0) bestIndex else contact
    }

    /**
     * Dispatches to the appropriate frame-selection strategy for a given [CorrectionType]:
     * - [CorrectionType.WRIST], [CorrectionType.CONTACT_HEIGHT], [CorrectionType.ELBOW_POSITION],
     *   [CorrectionType.BODY_ROTATION] -> [contactFrameIndex] (these are all measured at contact).
     * - [CorrectionType.FOLLOW_THROUGH] -> [followThroughFrameIndex].
     * - [CorrectionType.STROKE_SPEED], [CorrectionType.GENERAL] -> [bestSnapshotFrameIndex].
     */
    fun snapshotFrameFor(type: CorrectionType, frames: List<List<Landmark3D>>): Int = when (type) {
        CorrectionType.WRIST,
        CorrectionType.CONTACT_HEIGHT,
        CorrectionType.ELBOW_POSITION,
        CorrectionType.BODY_ROTATION -> contactFrameIndex(frames)
        CorrectionType.FOLLOW_THROUGH -> followThroughFrameIndex(frames)
        CorrectionType.STROKE_SPEED,
        CorrectionType.GENERAL -> bestSnapshotFrameIndex(frames)
    }

    /**
     * True if at least one frame has [MIN_CORE_VISIBLE] or more of the [CORE_LANDMARKS] visible
     * (`visibility >= [MIN_VISIBILITY]`) — i.e. the rep has at least one renderable frame. Backs a
     * "no data" rep state in the UI for reps where pose tracking never produced a usable frame.
     */
    fun hasUsablePose(frames: List<List<Landmark3D>>): Boolean =
        frames.any { coreCompleteness(it).first >= MIN_CORE_VISIBLE }

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
