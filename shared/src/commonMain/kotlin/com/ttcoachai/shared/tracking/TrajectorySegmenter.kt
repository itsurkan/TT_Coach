package com.ttcoachai.shared.tracking

import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.ContactEvent
import com.ttcoachai.shared.models.ContactType
import com.ttcoachai.shared.models.ParabolicFit
import com.ttcoachai.shared.models.TrajectorySegment
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Splits a ball rally into trajectory segments by detecting contact events
 * (bounces, paddle contacts, net clips) and fitting a parabola to each segment.
 *
 * Three-signal detector (research R9):
 *   1. Vertical velocity reversal → BOUNCE
 *   2. Speed ratio > [speedRatioThreshold] → PADDLE_CONTACT
 *   3. Direction angle > [directionAngleThreshold]° → NET_CLIP or UNKNOWN_CONTACT
 *
 * All math uses `kotlin.math` only — no JVM-specific imports (research R16).
 */
class TrajectorySegmenter(
    private val bounceAngleThreshold: Float = 30f,
    private val speedRatioThreshold: Float = 1.8f,
    private val directionAngleThreshold: Float = 30f,
    private val maxFitRmsError: Double = 0.02
) {

    companion object {
        private const val MIN_SPEED_THRESHOLD = 1e-6f  // Avoid division by zero
        private const val MAX_RECURSION_DEPTH = 1       // One level of sub-splitting
    }

    /**
     * Detect contact events in a sequence of detected ball positions.
     *
     * Requires at least 3 detections. Returns an empty list otherwise.
     */
    fun detectContacts(detections: List<BallDetection>): List<ContactEvent> {
        val detected = detections.filter { it.status == BallDetectionStatus.DETECTED }
        if (detected.size < 3) return emptyList()

        val contacts = mutableListOf<ContactEvent>()

        for (i in 1 until detected.size - 1) {
            val prev = detected[i - 1]
            val curr = detected[i]
            val next = detected[i + 1]

            val vxIn  = curr.x - prev.x
            val vyIn  = curr.y - prev.y
            val vxOut = next.x - curr.x
            val vyOut = next.y - curr.y

            val speedIn  = sqrt(vxIn * vxIn + vyIn * vyIn)
            val speedOut = sqrt(vxOut * vxOut + vyOut * vyOut)

            // Signal 1: Vertical velocity reversal → BOUNCE
            val vySignChange = (vyIn > 0 && vyOut < 0) || (vyIn < 0 && vyOut > 0)
            val vySignificant = kotlin.math.abs(vyIn) > MIN_SPEED_THRESHOLD &&
                                kotlin.math.abs(vyOut) > MIN_SPEED_THRESHOLD

            if (vySignChange && vySignificant) {
                contacts.add(
                    ContactEvent(
                        type = ContactType.BOUNCE,
                        frameIndex = curr.frameIndex,
                        timestampMs = curr.timestampMs,
                        position = Pair(curr.x, curr.y),
                        velocityBefore = speedIn,
                        velocityAfter = speedOut,
                        confidence = 0.9f
                    )
                )
                continue
            }

            // Signal 2: Speed ratio spike → PADDLE_CONTACT
            if (speedIn > MIN_SPEED_THRESHOLD && speedOut > MIN_SPEED_THRESHOLD) {
                val ratio = speedOut / speedIn
                val inverseRatio = speedIn / speedOut
                if (ratio > speedRatioThreshold || inverseRatio > speedRatioThreshold) {
                    contacts.add(
                        ContactEvent(
                            type = ContactType.PADDLE_CONTACT,
                            frameIndex = curr.frameIndex,
                            timestampMs = curr.timestampMs,
                            position = Pair(curr.x, curr.y),
                            velocityBefore = speedIn,
                            velocityAfter = speedOut,
                            confidence = 0.85f
                        )
                    )
                    continue
                }
            }

            // Signal 3: Direction angle change → NET_CLIP or UNKNOWN_CONTACT
            if (speedIn > MIN_SPEED_THRESHOLD && speedOut > MIN_SPEED_THRESHOLD) {
                val dot = vxIn * vxOut + vyIn * vyOut
                val cosAngle = (dot / (speedIn * speedOut)).coerceIn(-1f, 1f)
                val angleDeg = (acos(cosAngle.toDouble()) * 180.0 / kotlin.math.PI).toFloat()

                if (angleDeg > directionAngleThreshold) {
                    // NET_CLIP: large direction change but no speed spike
                    val type = if (angleDeg > bounceAngleThreshold && angleDeg < 150f) {
                        ContactType.NET_CLIP
                    } else {
                        ContactType.UNKNOWN_CONTACT
                    }
                    contacts.add(
                        ContactEvent(
                            type = type,
                            frameIndex = curr.frameIndex,
                            timestampMs = curr.timestampMs,
                            position = Pair(curr.x, curr.y),
                            velocityBefore = speedIn,
                            velocityAfter = speedOut,
                            confidence = 0.7f
                        )
                    )
                }
            }
        }
        return contacts
    }

    /**
     * Split [detections] into trajectory segments at detected contact events.
     * Fits each segment with a parabola and fills detection gaps.
     * Segments with fitRmsError > [maxFitRmsError] undergo one level of recursive sub-splitting.
     *
     * @param detections All ball detections for a rally (including NOT_DETECTED frames)
     * @param frameDurationMs Duration of one frame in milliseconds
     * @return Ordered list of fitted trajectory segments
     */
    fun segment(
        detections: List<BallDetection>,
        frameDurationMs: Long
    ): List<TrajectorySegment> {
        if (detections.isEmpty()) return emptyList()

        val detected = detections.filter { it.status == BallDetectionStatus.DETECTED }
        if (detected.isEmpty()) return emptyList()

        val contacts = detectContacts(detected)

        // Build split points from contact frame indices
        val splitAfter = contacts.map { it.frameIndex }.toSortedSet()

        // Partition detected frames into groups separated by contacts
        val groups = mutableListOf<MutableList<BallDetection>>()
        var current = mutableListOf<BallDetection>()
        for (d in detected) {
            current.add(d)
            if (d.frameIndex in splitAfter) {
                groups.add(current)
                // Next segment starts with the contact point (for continuity)
                current = mutableListOf(d)
            }
        }
        if (current.isNotEmpty()) groups.add(current)

        // Fit each group and build TrajectorySegments
        val segments = mutableListOf<TrajectorySegment>()
        for ((groupIdx, group) in groups.withIndex()) {
            val contactAtEnd = contacts.firstOrNull { it.frameIndex == group.last().frameIndex }
            val contactAtStart = contacts.firstOrNull { it.frameIndex == group.first().frameIndex }

            val built = fitGroup(
                group = group,
                segmentIndex = segments.size,
                contactBefore = if (groupIdx > 0) contactAtStart else null,
                contactAfter = contactAtEnd,
                frameDurationMs = frameDurationMs,
                allDetections = detections,
                recursionDepth = 0
            )
            segments.addAll(built)
        }

        return segments
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun fitGroup(
        group: List<BallDetection>,
        segmentIndex: Int,
        contactBefore: ContactEvent?,
        contactAfter: ContactEvent?,
        frameDurationMs: Long,
        allDetections: List<BallDetection>,
        recursionDepth: Int
    ): List<TrajectorySegment> {
        val fit = TrajectoryFilter.fit(group) ?: return emptyList()
        val rms = TrajectoryFilter.rmsError(fit, group)

        val startFrame = group.first().frameIndex
        val endFrame   = group.last().frameIndex

        // Retrieve all detections (including NOT_DETECTED) in this frame range for gap filling
        val rangeDetections = allDetections.filter { it.frameIndex in startFrame..endFrame }
        val filled = TrajectoryFilter.fillGaps(fit, rangeDetections, startFrame, endFrame, frameDurationMs)
        val durationMs = (endFrame - startFrame) * frameDurationMs

        val segment = TrajectorySegment(
            segmentIndex = segmentIndex,
            startFrameIndex = startFrame,
            endFrameIndex = endFrame,
            detections = group,
            fittedPositions = filled,
            contactBefore = contactBefore,
            contactAfter = contactAfter,
            fitCoefficients = fit,
            fitRmsError = rms,
            segmentDurationMs = durationMs
        )

        // Recursive sub-split on high RMS (max 1 level — research R10)
        if (rms > maxFitRmsError && recursionDepth < MAX_RECURSION_DEPTH && group.size >= 4) {
            return trySplit(
                group = group,
                segmentIndex = segmentIndex,
                contactBefore = contactBefore,
                contactAfter = contactAfter,
                frameDurationMs = frameDurationMs,
                allDetections = allDetections,
                recursionDepth = recursionDepth,
                fallback = segment
            )
        }

        return listOf(segment)
    }

    /**
     * Sub-split at the max-residual point within the group.
     * Falls back to [fallback] if sub-splitting produces worse or invalid results.
     */
    private fun trySplit(
        group: List<BallDetection>,
        segmentIndex: Int,
        contactBefore: ContactEvent?,
        contactAfter: ContactEvent?,
        frameDurationMs: Long,
        allDetections: List<BallDetection>,
        recursionDepth: Int,
        fallback: TrajectorySegment
    ): List<TrajectorySegment> {
        val fit = TrajectoryFilter.fit(group) ?: return listOf(fallback)
        val t0 = group.first().frameIndex.toDouble()

        // Find the detection with the highest residual
        val maxResidualIdx = group.indices.maxByOrNull { i ->
            val d = group[i]
            val t = d.frameIndex - t0
            val xPred = fit.ax + fit.bx * t
            val yPred = fit.ay + fit.by * t + fit.cy * t * t
            val dx = d.x - xPred
            val dy = d.y - yPred
            dx * dx + dy * dy
        } ?: return listOf(fallback)

        // Need at least one detection on each side
        if (maxResidualIdx <= 0 || maxResidualIdx >= group.size - 1) return listOf(fallback)

        val left  = group.subList(0, maxResidualIdx + 1)
        val right = group.subList(maxResidualIdx, group.size)

        val splitContact = ContactEvent(
            type = ContactType.UNKNOWN_CONTACT,
            frameIndex = group[maxResidualIdx].frameIndex,
            timestampMs = group[maxResidualIdx].timestampMs,
            position = Pair(group[maxResidualIdx].x, group[maxResidualIdx].y),
            confidence = 0.5f
        )

        val leftSegments = fitGroup(
            group = left,
            segmentIndex = segmentIndex,
            contactBefore = contactBefore,
            contactAfter = splitContact,
            frameDurationMs = frameDurationMs,
            allDetections = allDetections,
            recursionDepth = recursionDepth + 1
        )
        val rightSegments = fitGroup(
            group = right,
            segmentIndex = segmentIndex + leftSegments.size,
            contactBefore = splitContact,
            contactAfter = contactAfter,
            frameDurationMs = frameDurationMs,
            allDetections = allDetections,
            recursionDepth = recursionDepth + 1
        )

        return if (leftSegments.isEmpty() || rightSegments.isEmpty()) {
            listOf(fallback)
        } else {
            // Re-index right segments to keep indices sequential
            val offset = leftSegments.size
            val reindexed = rightSegments.mapIndexed { idx, seg ->
                seg.copy(segmentIndex = segmentIndex + offset + idx)
            }
            leftSegments + reindexed
        }
    }
}
