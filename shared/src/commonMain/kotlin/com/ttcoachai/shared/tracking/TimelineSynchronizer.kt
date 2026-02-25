package com.ttcoachai.shared.tracking

import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.DataSource
import com.ttcoachai.shared.models.PoseFrame
import com.ttcoachai.shared.models.SynchronizedFrame

/**
 * Merges ball position data and skeleton pose data into a single synchronized timeline.
 *
 * Algorithm (post-hoc batch mode — contract section 3):
 *  1. Build a lookup map from timestampMs → PoseFrame and timestampMs → BallDetection.
 *  2. For every timestamp in [allTimestampsMs], emit one [SynchronizedFrame].
 *  3. If a stream has no entry at that timestamp, check whether the gap is exactly 1
 *     frame wide (neighbours present on both sides). If so, linearly interpolate.
 *     Larger gaps remain ABSENT.
 *
 * All math is pure Kotlin (no platform-specific APIs) for KMP commonMain compatibility.
 */
class TimelineSynchronizer {

    /**
     * Merge [poses] and [balls] into one [SynchronizedFrame] per entry in [allTimestampsMs].
     *
     * @param poses  Pose frames sorted by timestampMs
     * @param balls  Ball detections sorted by timestampMs
     * @param allTimestampsMs Master timeline — output has exactly this many entries in the same order
     */
    fun merge(
        poses: List<PoseFrame>,
        balls: List<BallDetection>,
        allTimestampsMs: List<Long>
    ): List<SynchronizedFrame> {
        if (allTimestampsMs.isEmpty()) return emptyList()

        val poseByTs = poses.associateBy { it.timestampMs }
        val ballByTs = balls
            .filter { it.status == BallDetectionStatus.DETECTED }
            .associateBy { it.timestampMs }

        return allTimestampsMs.mapIndexed { frameIndex, tsMs ->
            // --- Pose ---
            val poseEntry = poseByTs[tsMs]
            val (resolvedPose, poseSource) = if (poseEntry != null) {
                Pair(poseEntry, DataSource.DETECTED)
            } else {
                Pair(null, DataSource.ABSENT)
            }

            // --- Ball ---
            val ballEntry = ballByTs[tsMs]
            val (resolvedBall, ballSource) = if (ballEntry != null) {
                Pair(ballEntry, DataSource.DETECTED)
            } else {
                // Attempt 1-frame interpolation
                val interpolated = tryInterpolateBall(tsMs, allTimestampsMs, frameIndex, ballByTs)
                if (interpolated != null) {
                    Pair(interpolated, DataSource.INTERPOLATED)
                } else {
                    Pair(null, DataSource.ABSENT)
                }
            }

            SynchronizedFrame(
                frameIndex = frameIndex,
                timestampMs = tsMs,
                pose = resolvedPose,
                ball = resolvedBall,
                poseSource = poseSource,
                ballSource = ballSource
            )
        }
    }

    /**
     * Linearly interpolate a ball position between [before] and [after] at [targetTimestampMs].
     *
     * The returned [BallDetection] uses [BallDetectionStatus.DETECTED] to indicate valid
     * position data; callers should record [DataSource.INTERPOLATED] in the enclosing
     * [SynchronizedFrame.ballSource] to communicate provenance.
     *
     * Confidence and radius are averaged from the two neighbours.
     */
    fun interpolateBall(
        before: BallDetection,
        after: BallDetection,
        targetTimestampMs: Long
    ): BallDetection {
        val span = (after.timestampMs - before.timestampMs).toFloat()
        val alpha = if (span == 0f) 0f else
            (targetTimestampMs - before.timestampMs).toFloat() / span

        val x = before.x + (after.x - before.x) * alpha
        val y = before.y + (after.y - before.y) * alpha
        val confidence = (before.confidence + after.confidence) / 2f
        val radiusPx   = (before.radiusPx   + after.radiusPx)   / 2f

        return BallDetection(
            x = x,
            y = y,
            confidence = confidence,
            radiusPx = radiusPx,
            frameIndex = -1,       // synthetic — no corresponding source frame
            timestampMs = targetTimestampMs,
            status = BallDetectionStatus.DETECTED
        )
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns an interpolated [BallDetection] for [tsMs] if and only if the gap is
     * exactly 1 frame wide (i.e., the immediately preceding and following timestamps
     * both have a detected ball).
     *
     * "1-frame gap" means: the entry at [frameIndex]-1 and [frameIndex]+1 in
     * [allTimestampsMs] both have ball data, and the current [frameIndex] does not.
     */
    private fun tryInterpolateBall(
        tsMs: Long,
        allTimestampsMs: List<Long>,
        frameIndex: Int,
        ballByTs: Map<Long, BallDetection>
    ): BallDetection? {
        val prevTs = allTimestampsMs.getOrNull(frameIndex - 1) ?: return null
        val nextTs = allTimestampsMs.getOrNull(frameIndex + 1) ?: return null
        val before = ballByTs[prevTs] ?: return null
        val after  = ballByTs[nextTs] ?: return null
        return interpolateBall(before, after, tsMs)
    }
}
