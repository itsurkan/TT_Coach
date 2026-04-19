package com.ttcoachai.debug

import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Freezes lower-body landmarks to a single canonical static stance for the
 * drill-shape editor preview.
 *
 * Takes the FIRST frame of the (already trimmed + smoothed) mean stroke as the
 * reference stance — not the per-frame average, because averaging hip
 * positions across a stroke where the torso tilts forward puts the averaged
 * hip close to the knee height and collapses the thigh geometry.
 *
 * Post-processing on that reference stance:
 *   1. Scale thigh (hip→knee) length by [THIGH_SCALE] so the leg reads longer
 *      visually.
 *   2. Rotate shin + foot around the knee so the hip-knee-ankle interior
 *      angle becomes [targetKneeAngleDeg] (default 150° = slight bend, the
 *      ready-stance baseline for a forehand drive).
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

    private const val THIGH_SCALE = 1.3f

    fun canonicalize(
        frames: List<PoseFrame>,
        targetKneeAngleDeg: Float = 150f
    ): List<PoseFrame> {
        if (frames.isEmpty()) return frames
        val reference = frames.first()
        if (reference.landmarks.size < 33) return frames

        val legMap = LEG_INDICES.associateWith { reference.landmarks[it] }.toMutableMap()

        scaleThigh(legMap, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE, LEFT_FOOT, THIGH_SCALE)
        scaleThigh(legMap, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE, RIGHT_FOOT, THIGH_SCALE)

        adjustKneeAngle(legMap, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE, LEFT_FOOT, targetKneeAngleDeg)
        adjustKneeAngle(legMap, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE, RIGHT_FOOT, targetKneeAngleDeg)

        return frames.map { frame ->
            val next = frame.landmarks.toMutableList()
            for ((idx, lm) in legMap) next[idx] = lm
            frame.copy(landmarks = next)
        }
    }

    /** Extends hip→knee along its current direction by [scale] and moves shin+foot rigidly. */
    private fun scaleThigh(
        landmarks: MutableMap<Int, Landmark3D>,
        hipIdx: Int, kneeIdx: Int, ankleIdx: Int, footIndices: IntArray,
        scale: Float
    ) {
        val hip = landmarks[hipIdx] ?: return
        val knee = landmarks[kneeIdx] ?: return
        val ankle = landmarks[ankleIdx] ?: return

        val newKneeX = hip.x + (knee.x - hip.x) * scale
        val newKneeY = hip.y + (knee.y - hip.y) * scale
        val newKneeZ = hip.z + (knee.z - hip.z) * scale

        val shinDx = ankle.x - knee.x
        val shinDy = ankle.y - knee.y
        val shinDz = ankle.z - knee.z
        val newAnkleX = newKneeX + shinDx
        val newAnkleY = newKneeY + shinDy
        val newAnkleZ = newKneeZ + shinDz

        landmarks[kneeIdx] = knee.copy(x = newKneeX, y = newKneeY, z = newKneeZ)
        landmarks[ankleIdx] = ankle.copy(x = newAnkleX, y = newAnkleY, z = newAnkleZ)

        for (fidx in footIndices) {
            val foot = landmarks[fidx] ?: continue
            val offX = foot.x - ankle.x
            val offY = foot.y - ankle.y
            val offZ = foot.z - ankle.z
            landmarks[fidx] = foot.copy(
                x = newAnkleX + offX,
                y = newAnkleY + offY,
                z = newAnkleZ + offZ
            )
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

        val cosAng = ((v1x * v2x + v1y * v2y) / (thighLen * shinLen)).coerceIn(-1f, 1f)
        val currentRad = acos(cosAng.toDouble())

        // Cross-product sign determines which side the ankle is on. If near
        // zero (colinear hip/knee/ankle), default to +1 so the shin bends
        // consistently relative to the thigh.
        val cross = v1x * v2y - v1y * v2x
        val direction = if (cross >= 0f) 1.0 else -1.0

        val targetRad = Math.toRadians(targetDeg.toDouble())
        val rotationRad = (targetRad - currentRad) * direction

        val c = cos(rotationRad).toFloat()
        val s = sin(rotationRad).toFloat()

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
