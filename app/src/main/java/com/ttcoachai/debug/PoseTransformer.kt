package com.ttcoachai.debug

import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.shared.models.PoseFrame
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies user-edited pose-shape deltas to a [PoseFrame].
 *
 * Not biomechanics — just kinematic approximations that make each slider
 * produce a visible, intuitive change. Transforms are applied in
 * proximal-to-distal order so a shoulder rotation correctly carries the
 * elbow + wrist with it.
 *
 * Order:
 *   1. Body yaw (bodyRotationDeltaDeg) — rotate upper body around hip center in xz.
 *   2. Torso tilt (torsoTiltDeltaDeg)   — rotate upper body around hip center in yz.
 *   3. Knee bend (kneeBendDeltaDeg)     — rotate both lower legs around knees in xy.
 *   4. Right shoulder (shoulderAngleDeltaDeg) — rotate elbow+wrist+hand around shoulder in xy.
 *   5. Right elbow X offset            — translate elbow + wrist + hand in x.
 *   6. Right elbow angle (elbowAngleDeltaDeg) — rotate wrist+hand around elbow in xy.
 *   7. Right wrist angle (wristAngleDeltaDeg) — rotate hand (17..22) around wrist in xy.
 *
 * After all slider deltas, a fixed [viewCameraYawDeg] rotates the whole pose
 * around the hip vertical axis so the player is rendered at a 3/4 angle
 * (per user request: camera at 7:30, not 6).
 */
object PoseTransformer {

    /** Default display-camera yaw: ~45° counterclockwise from 6 o'clock (7:30 view). */
    const val DEFAULT_VIEW_CAMERA_YAW_DEG: Float = 45f

    data class EditableParams(
        val bodyRotationDeltaDeg: Float = 0f,
        val torsoTiltDeltaDeg: Float = 0f,
        val rightShoulderAngleDeltaDeg: Float = 0f,
        val rightElbowAngleDeltaDeg: Float = 0f,
        val rightElbowXOffset: Float = 0f,
        val rightWristAngleDeltaDeg: Float = 0f,
        val kneeBendDeltaDeg: Float = 0f
    ) {
        val isIdentity: Boolean
            get() = bodyRotationDeltaDeg == 0f &&
                torsoTiltDeltaDeg == 0f &&
                rightShoulderAngleDeltaDeg == 0f &&
                rightElbowAngleDeltaDeg == 0f &&
                rightElbowXOffset == 0f &&
                rightWristAngleDeltaDeg == 0f &&
                kneeBendDeltaDeg == 0f
    }

    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24
    private const val RIGHT_SHOULDER = 12
    private const val RIGHT_ELBOW = 14
    private const val RIGHT_WRIST = 16
    private const val LEFT_KNEE = 25
    private const val RIGHT_KNEE = 26
    private const val LEFT_ANKLE = 27
    private const val RIGHT_ANKLE = 28
    private val LEFT_FOOT = intArrayOf(29, 31)    // heel, foot_index
    private val RIGHT_FOOT = intArrayOf(30, 32)
    private val RIGHT_HAND = intArrayOf(17, 19, 21) // pinky, index, thumb (right side)
    private val RIGHT_HAND_RIGHT = intArrayOf(18, 20, 22) // MediaPipe numbering: 18/20/22 are the right side
    // NOTE: MediaPipe labels the player's own right hand as 18/20/22 (right pinky/index/thumb).
    // 17/19/21 are the left hand. We rotate both sets when referring to "right-side hand" because
    // in practice for calibration UI the forehand drill is right-handed and MediaPipe's naming
    // depends on the camera perspective; apply to the same set (18/20/22) plus the cross-side
    // (17/19/21) for safety.
    private val RIGHT_HAND_FULL = intArrayOf(17, 18, 19, 20, 21, 22)

    fun apply(
        frame: PoseFrame,
        params: EditableParams,
        viewCameraYawDeg: Float = DEFAULT_VIEW_CAMERA_YAW_DEG
    ): PoseFrame {
        if (frame.landmarks.size < 33) return frame

        var working = frame.landmarks.toMutableList()

        val hipMidX = (working[LEFT_HIP].x + working[RIGHT_HIP].x) / 2f
        val hipMidY = (working[LEFT_HIP].y + working[RIGHT_HIP].y) / 2f
        val hipMidZ = (working[LEFT_HIP].z + working[RIGHT_HIP].z) / 2f

        if (!params.isIdentity) {
            // 1. Body yaw (xz) — affects all upper body (indices 0..22)
            if (params.bodyRotationDeltaDeg != 0f) {
                rotateUpperBodyXZ(working, hipMidX, hipMidZ, params.bodyRotationDeltaDeg)
            }
            // 2. Torso tilt (yz) — affects all upper body
            if (params.torsoTiltDeltaDeg != 0f) {
                rotateUpperBodyYZ(working, hipMidY, hipMidZ, params.torsoTiltDeltaDeg)
            }
            // 3. Knee bend — rotate both lower legs in xy
            if (params.kneeBendDeltaDeg != 0f) {
                rotateAroundPivot(
                    working, pivot = LEFT_KNEE, targets = intArrayOf(LEFT_ANKLE) + LEFT_FOOT,
                    deltaDeg = params.kneeBendDeltaDeg
                )
                rotateAroundPivot(
                    working, pivot = RIGHT_KNEE, targets = intArrayOf(RIGHT_ANKLE) + RIGHT_FOOT,
                    deltaDeg = params.kneeBendDeltaDeg
                )
            }
            // 4. Right shoulder — rotate elbow + wrist + hand around shoulder
            if (params.rightShoulderAngleDeltaDeg != 0f) {
                rotateAroundPivot(
                    working, pivot = RIGHT_SHOULDER,
                    targets = intArrayOf(RIGHT_ELBOW, RIGHT_WRIST) + RIGHT_HAND_FULL,
                    deltaDeg = params.rightShoulderAngleDeltaDeg
                )
            }
            // 5. Right elbow X offset — translate elbow + wrist + hand
            if (params.rightElbowXOffset != 0f) {
                translateIndices(
                    working,
                    intArrayOf(RIGHT_ELBOW, RIGHT_WRIST) + RIGHT_HAND_FULL,
                    dx = params.rightElbowXOffset
                )
            }
            // 6. Right elbow angle — rotate wrist + hand around elbow
            if (params.rightElbowAngleDeltaDeg != 0f) {
                rotateAroundPivot(
                    working, pivot = RIGHT_ELBOW,
                    targets = intArrayOf(RIGHT_WRIST) + RIGHT_HAND_FULL,
                    deltaDeg = params.rightElbowAngleDeltaDeg
                )
            }
            // 7. Right wrist angle — rotate hand landmarks around wrist
            if (params.rightWristAngleDeltaDeg != 0f) {
                rotateAroundPivot(
                    working, pivot = RIGHT_WRIST, targets = RIGHT_HAND_FULL,
                    deltaDeg = params.rightWristAngleDeltaDeg
                )
            }
        }

        // 8. View camera yaw — fixed display rotation around hip vertical axis.
        if (viewCameraYawDeg != 0f) {
            val fullBodyIndices = IntArray(working.size) { it }
            rotateAroundHipVerticalAxis(working, hipMidX, hipMidZ, viewCameraYawDeg, fullBodyIndices)
        }

        return frame.copy(landmarks = working)
    }

    // ---------------- transform primitives ----------------

    private fun rotateUpperBodyXZ(
        working: MutableList<Landmark3D>,
        hipMidX: Float, hipMidZ: Float,
        deltaDeg: Float
    ) {
        val rad = Math.toRadians(deltaDeg.toDouble())
        val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
        for (i in 0 until LEFT_HIP) {
            val lm = working[i]
            val dx = lm.x - hipMidX
            val dz = lm.z - hipMidZ
            val rx = dx * c - dz * s
            val rz = dx * s + dz * c
            working[i] = lm.copy(x = hipMidX + rx, z = hipMidZ + rz)
        }
    }

    private fun rotateUpperBodyYZ(
        working: MutableList<Landmark3D>,
        hipMidY: Float, hipMidZ: Float,
        deltaDeg: Float
    ) {
        val rad = Math.toRadians(deltaDeg.toDouble())
        val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
        for (i in 0 until LEFT_HIP) {
            val lm = working[i]
            val dy = lm.y - hipMidY
            val dz = lm.z - hipMidZ
            val ry = dy * c - dz * s
            val rz = dy * s + dz * c
            working[i] = lm.copy(y = hipMidY + ry, z = hipMidZ + rz)
        }
    }

    private fun rotateAroundPivot(
        working: MutableList<Landmark3D>,
        pivot: Int,
        targets: IntArray,
        deltaDeg: Float
    ) {
        val rad = Math.toRadians(deltaDeg.toDouble())
        val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
        val px = working[pivot].x; val py = working[pivot].y
        for (t in targets) {
            if (t >= working.size) continue
            val lm = working[t]
            val dx = lm.x - px; val dy = lm.y - py
            val rx = dx * c - dy * s
            val ry = dx * s + dy * c
            working[t] = lm.copy(x = px + rx, y = py + ry)
        }
    }

    private fun translateIndices(
        working: MutableList<Landmark3D>,
        targets: IntArray,
        dx: Float = 0f,
        dy: Float = 0f
    ) {
        for (t in targets) {
            if (t >= working.size) continue
            val lm = working[t]
            working[t] = lm.copy(x = lm.x + dx, y = lm.y + dy)
        }
    }

    private fun rotateAroundHipVerticalAxis(
        working: MutableList<Landmark3D>,
        hipMidX: Float, hipMidZ: Float,
        deltaDeg: Float,
        targets: IntArray
    ) {
        val rad = Math.toRadians(deltaDeg.toDouble())
        val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
        for (t in targets) {
            if (t >= working.size) continue
            val lm = working[t]
            val dx = lm.x - hipMidX
            val dz = lm.z - hipMidZ
            val rx = dx * c - dz * s
            val rz = dx * s + dz * c
            working[t] = lm.copy(x = hipMidX + rx, z = hipMidZ + rz)
        }
    }
}
