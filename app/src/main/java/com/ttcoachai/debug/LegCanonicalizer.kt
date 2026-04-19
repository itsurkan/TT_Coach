package com.ttcoachai.debug

import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Freezes lower-body landmarks to a single canonical static stance for the
 * drill-shape editor preview.
 *
 * Why: time-normalized averaging across 5 real reps produces jittery legs —
 * MediaPipe knee/ankle noise is amplified because foot contacts don't line up
 * between reps. Locking the stance + enforcing a target knee angle gives a
 * clean reference pose. Default 145° matches the athletic "ready" stance
 * commonly taught for a forehand drive.
 */
object LegCanonicalizer {

    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24
    private const val LEFT_KNEE = 25
    private const val RIGHT_KNEE = 26
    private const val LEFT_ANKLE = 27
    private const val RIGHT_ANKLE = 28
    private val LEFT_FOOT = intArrayOf(29, 31)
    private val RIGHT_FOOT = intArrayOf(30, 32)
    private val LEG_INDICES = intArrayOf(
        LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE,
        LEFT_ANKLE, RIGHT_ANKLE,
        *LEFT_FOOT, *RIGHT_FOOT
    )

    fun canonicalize(
        frames: List<PoseFrame>,
        targetKneeAngleDeg: Float = 145f
    ): List<PoseFrame> {
        if (frames.isEmpty()) return frames
        if (frames.any { it.landmarks.size < 33 }) return frames

        val avgLeg = LEG_INDICES.associateWith { idx ->
            val xs = frames.map { it.landmarks[idx].x.toDouble() }
            val ys = frames.map { it.landmarks[idx].y.toDouble() }
            val zs = frames.map { it.landmarks[idx].z.toDouble() }
            val vs = frames.map { it.landmarks[idx].visibility.toDouble() }
            val ps = frames.map { it.landmarks[idx].presence.toDouble() }
            Landmark3D(
                x = xs.average().toFloat(),
                y = ys.average().toFloat(),
                z = zs.average().toFloat(),
                visibility = vs.average().toFloat(),
                presence = ps.average().toFloat()
            )
        }.toMutableMap()

        adjustKneeAngle(avgLeg, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE, LEFT_FOOT, targetKneeAngleDeg)
        adjustKneeAngle(avgLeg, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE, RIGHT_FOOT, targetKneeAngleDeg)

        return frames.map { frame ->
            val next = frame.landmarks.toMutableList()
            for ((idx, lm) in avgLeg) next[idx] = lm
            frame.copy(landmarks = next)
        }
    }

    /** Rotates shin + foot around the knee so the hip-knee-ankle interior angle becomes [targetDeg]. */
    private fun adjustKneeAngle(
        landmarks: MutableMap<Int, Landmark3D>,
        hipIdx: Int, kneeIdx: Int, ankleIdx: Int, footIndices: IntArray,
        targetDeg: Float
    ) {
        val hip = landmarks[hipIdx] ?: return
        val knee = landmarks[kneeIdx] ?: return
        val ankle = landmarks[ankleIdx] ?: return

        val v1x = hip.x - knee.x; val v1y = hip.y - knee.y
        val v2x = ankle.x - knee.x; val v2y = ankle.y - knee.y
        val thighLen = sqrt(v1x * v1x + v1y * v1y)
        val shinLen = sqrt(v2x * v2x + v2y * v2y)
        if (thighLen < 1e-4f || shinLen < 1e-4f) return

        val cross = (v1x * v2y - v1y * v2x).toDouble()
        val dot = (v1x * v2x + v1y * v2y).toDouble()
        val signedCurrent = atan2(cross, dot)
        if (signedCurrent == 0.0) return

        val signedTarget = Math.toRadians(targetDeg.toDouble()) * sign(signedCurrent)
        val deltaRad = signedTarget - signedCurrent
        if (abs(deltaRad) < 1e-4) return

        val c = cos(deltaRad).toFloat()
        val s = sin(deltaRad).toFloat()

        val rotateIndices = intArrayOf(ankleIdx) + footIndices
        for (idx in rotateIndices) {
            val lm = landmarks[idx] ?: continue
            val dx = lm.x - knee.x; val dy = lm.y - knee.y
            val rx = dx * c - dy * s
            val ry = dx * s + dy * c
            landmarks[idx] = lm.copy(x = knee.x + rx, y = knee.y + ry)
        }
    }
}
