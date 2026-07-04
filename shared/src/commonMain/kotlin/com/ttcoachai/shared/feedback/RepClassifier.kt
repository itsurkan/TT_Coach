/*
 * AI Coach for Table Tennis
 * RepClassifier — ports the poses_viewer / 2D-drill-pipeline rep-validity taxonomy
 * (detect -> forward -> locomotion -> band) to legacy live-training data so the
 * Android rep strip can mark reps the same way the viewer does.
 */

package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.analysis.SignalMath
import com.ttcoachai.shared.models.Landmark3D
import kotlin.math.sign

/**
 * Verdict for a single already-segmented rep (a candidate stroke, produced by the
 * legacy live pipeline's own stroke detector — detection itself is NOT redone here).
 */
enum class RepVerdict { VALID, NO_POSE, LOCOMOTION, RECOVERY_SWING, SPEED_DURATION_OUTLIER }

/**
 * Classifies legacy MediaPipe-33 [Landmark3D] reps with the same taxonomy the 2D
 * drill pipeline applies to Keypoint2D/COCO-17 reps (`ForwardStrokeFilter` +
 * `RepFilter` + `LocomotionFilter`), so the Android rep strip can show the same
 * four non-VALID reasons the poses_viewer shows.
 *
 * This is a from-scratch adaptation, not a call into the 2D pipeline (frozen /
 * incompatible input: MediaPipe-33 vs COCO-17, no per-frame timestamps — only
 * frame order at an assumed-constant fps). Deviations from the 2D pipeline are
 * documented per-step below.
 *
 * Pipeline per session, mirroring the viewer's detect -> forward -> locomotion ->
 * band order (detection is already done: every entry in [reps] IS a candidate rep):
 * 1. [RepVerdict.NO_POSE] — no usable frame ([StrokeSnapshotSelector.hasUsablePose]
 *    false, or the rep is empty). Excluded from every group statistic below.
 * 2. Per-rep features (score gate: `visibility >= MIN_VISIBILITY`).
 * 3. [RepVerdict.LOCOMOTION] — hip-mid travel over the rep exceeds
 *    [MAX_HIP_TRAVEL_TORSO] torso-lengths.
 * 4. Forward vote (mirrors [com.ttcoachai.shared.drill.ForwardStrokeFilter]) over
 *    reps not yet excluded, keyed on `sign(approachDx)`: dominant-direction group
 *    wins if it has >= [MIN_GROUP_SIZE] members on both sides and one group's
 *    median peak speed beats the other by >= [SPEED_DOMINANCE_RATIO]; otherwise
 *    falls back to per-rep facing sign. Losers -> [RepVerdict.RECOVERY_SWING].
 * 5. Banding (mirrors [com.ttcoachai.shared.drill.RepFilter]) over the forward
 *    survivors: below [MIN_REPS_TO_BAND] reps, skipped; otherwise reps whose
 *    peakSpeed OR duration falls outside `[median/BAND, median*BAND]` ->
 *    [RepVerdict.SPEED_DURATION_OUTLIER].
 * 6. Everything left -> [RepVerdict.VALID].
 */
object RepClassifier {

    /** `visibility` gate for every landmark read below (matches [StrokeSnapshotSelector]). */
    const val MIN_VISIBILITY = 0.5f

    /**
     * Fallback torso length (normalized image units) used when a rep never has a
     * usable shoulder/hip pair to measure its own torso length — a typical
     * in-frame torso span at drill framing. Only affects wrist-speed
     * normalization for such a rep; noted per the task's requirement to document
     * this deviation (the 2D pipeline always has a measurable torso or drops the
     * frame from the median rather than substituting a constant).
     */
    const val FALLBACK_TORSO_LENGTH = 0.25f

    /** Mirrors [com.ttcoachai.shared.drill.ForwardStrokeFilter.SPEED_DOMINANCE_RATIO]. */
    const val SPEED_DOMINANCE_RATIO = 1.2f

    /** Mirrors [com.ttcoachai.shared.drill.ForwardStrokeFilter.MIN_GROUP_SIZE]. */
    const val MIN_GROUP_SIZE = 2

    /**
     * Number of usable frames walked back from the peak to measure the approach
     * displacement, approximating [com.ttcoachai.shared.drill.ForwardStrokeFilter]'s
     * ~100 ms window at ~30 fps (constant-fps assumption — no timestamps here, so
     * the window is expressed in usable-frame steps rather than milliseconds).
     */
    const val APPROACH_FRAME_STEPS = 3

    /** Mirrors [com.ttcoachai.shared.drill.LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO]. */
    const val MAX_HIP_TRAVEL_TORSO = 0.4f

    /** Mirrors [com.ttcoachai.shared.drill.RepFilter.MIN_STROKES_TO_FILTER]. */
    const val MIN_REPS_TO_BAND = 4

    /** Mirrors [com.ttcoachai.shared.drill.RepFilter.SPEED_BAND] / `DURATION_BAND`. */
    const val BAND_FACTOR = 2.0f

    private const val NOSE = 0
    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24
    private const val WRIST = 16

    private class RepFeatures(
        val index: Int,
        val peakSpeed: Float,
        val duration: Int,
        val approachDx: Float?,
        val facingSign: Float?,
        val hipTravelTorso: Float?
    )

    fun classify(reps: List<List<List<Landmark3D>>>): List<RepVerdict> {
        if (reps.isEmpty()) return emptyList()

        val verdicts = arrayOfNulls<RepVerdict>(reps.size)

        // 1. NO_POSE — excluded from every later group statistic.
        val candidateIdx = ArrayList<Int>()
        for (i in reps.indices) {
            val frames = reps[i]
            if (frames.isEmpty() || !StrokeSnapshotSelector.hasUsablePose(frames)) {
                verdicts[i] = RepVerdict.NO_POSE
            } else {
                candidateIdx.add(i)
            }
        }

        // 2. Per-rep features.
        val features = candidateIdx.map { i -> extractFeatures(i, reps[i]) }

        // 3. LOCOMOTION.
        val afterLocomotion = ArrayList<RepFeatures>()
        for (f in features) {
            if (f.hipTravelTorso != null && f.hipTravelTorso > MAX_HIP_TRAVEL_TORSO) {
                verdicts[f.index] = RepVerdict.LOCOMOTION
            } else {
                afterLocomotion.add(f)
            }
        }

        // 4. Forward vote.
        val forward = forwardVote(afterLocomotion)
        val afterForward = ArrayList<RepFeatures>()
        for (f in afterLocomotion) {
            if (forward.contains(f.index)) {
                afterForward.add(f)
            } else {
                verdicts[f.index] = RepVerdict.RECOVERY_SWING
            }
        }

        // 5. Banding.
        val outliers = if (afterForward.size >= MIN_REPS_TO_BAND) {
            val medSpeed = SignalMath.median(afterForward.map { it.peakSpeed })
            val medDur = SignalMath.median(afterForward.map { it.duration.toFloat() })
            afterForward.filter { f ->
                f.peakSpeed < medSpeed / BAND_FACTOR || f.peakSpeed > medSpeed * BAND_FACTOR ||
                    f.duration < medDur / BAND_FACTOR || f.duration > medDur * BAND_FACTOR
            }.map { it.index }.toSet()
        } else {
            emptySet()
        }
        for (f in afterForward) {
            verdicts[f.index] = if (f.index in outliers) {
                RepVerdict.SPEED_DURATION_OUTLIER
            } else {
                RepVerdict.VALID
            }
        }

        return verdicts.map { it ?: RepVerdict.VALID }
    }

    /**
     * Returns the set of rep indices classified as "forward" (survive the
     * direction vote). Mirrors
     * [com.ttcoachai.shared.drill.ForwardStrokeFilter.filter]: speed-dominance
     * vote over dx-sign groups first, falling back to per-rep facing sign when
     * dominance can't be resolved.
     */
    private fun forwardVote(features: List<RepFeatures>): Set<Int> {
        if (features.isEmpty()) return emptySet()

        val pos = features.filter { (it.approachDx ?: 0f) > 0f }
        val neg = features.filter { (it.approachDx ?: 0f) < 0f }

        if (pos.size >= MIN_GROUP_SIZE && neg.size >= MIN_GROUP_SIZE) {
            val posMed = SignalMath.median(pos.map { it.peakSpeed })
            val negMed = SignalMath.median(neg.map { it.peakSpeed })
            when {
                posMed >= negMed * SPEED_DOMINANCE_RATIO -> return pos.map { it.index }.toSet()
                negMed >= posMed * SPEED_DOMINANCE_RATIO -> return neg.map { it.index }.toSet()
            }
            // Dominance unresolved -> per-rep facing-sign fallback below.
            return facingFallback(features)
        }

        // Only one dx-sign group exists (or one/both are below MIN_GROUP_SIZE with the other
        // empty): if exactly one sign group is non-empty, that group is forward and everything
        // else (indeterminate / opposite, but too small to be a real group) is recovery.
        if (pos.isNotEmpty() && neg.isEmpty()) return pos.map { it.index }.toSet()
        if (neg.isNotEmpty() && pos.isEmpty()) return neg.map { it.index }.toSet()

        // Both groups present but at least one under MIN_GROUP_SIZE, or both empty
        // (all indeterminate dx) -> facing-sign fallback decides membership per rep.
        return facingFallback(features)
    }

    /**
     * Per-rep facing-sign fallback: a rep is forward if `sign(approachDx) == facingSign`
     * (facing right -> positive dx is forward, facing left -> negative dx is forward).
     * Reps with unmeasurable dx or facing are excluded (treated as recovery).
     */
    private fun facingFallback(features: List<RepFeatures>): Set<Int> =
        features.filter { f ->
            val dx = f.approachDx
            val facing = f.facingSign
            dx != null && facing != null && dx != 0f && sign(dx) == facing
        }.map { it.index }.toSet()

    private fun extractFeatures(index: Int, frames: List<List<Landmark3D>>): RepFeatures {
        val usableIndices = frames.indices.filter { isVisible(frames[it], WRIST) }

        val torsoLen = medianTorsoLength(frames) ?: FALLBACK_TORSO_LENGTH

        var peakSpeed = 0f
        var peakUsablePos = -1 // position within usableIndices, not frame index
        for (i in 1 until usableIndices.size) {
            val prev = frames[usableIndices[i - 1]][WRIST]
            val curr = frames[usableIndices[i]][WRIST]
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            val speed = kotlin.math.sqrt(dx * dx + dy * dy) / torsoLen
            if (speed > peakSpeed) {
                peakSpeed = speed
                peakUsablePos = i
            }
        }

        val approachDx = if (peakUsablePos >= 0) {
            val approachPos = (peakUsablePos - APPROACH_FRAME_STEPS).coerceAtLeast(0)
            val peakX = frames[usableIndices[peakUsablePos]][WRIST].x
            val approachX = frames[usableIndices[approachPos]][WRIST].x
            peakX - approachX
        } else {
            null
        }

        val peakFrameIdx = if (peakUsablePos >= 0) usableIndices[peakUsablePos] else null
        val facing = peakFrameIdx?.let { facingSignAt(frames[it]) }

        val hipTravel = hipMidTravelTorso(frames, torsoLen)

        return RepFeatures(
            index = index,
            peakSpeed = peakSpeed,
            duration = frames.size,
            approachDx = approachDx,
            facingSign = facing,
            hipTravelTorso = hipTravel
        )
    }

    /** Median shoulder-mid -> hip-mid distance over usable frames; null if never measurable. */
    private fun medianTorsoLength(frames: List<List<Landmark3D>>): Float? {
        val lens = frames.mapNotNull { frame ->
            val ls = scored(frame, LEFT_SHOULDER) ?: return@mapNotNull null
            val rs = scored(frame, RIGHT_SHOULDER) ?: return@mapNotNull null
            val lh = scored(frame, LEFT_HIP) ?: return@mapNotNull null
            val rh = scored(frame, RIGHT_HIP) ?: return@mapNotNull null
            val dx = (ls.x + rs.x - lh.x - rh.x) / 2f
            val dy = (ls.y + rs.y - lh.y - rh.y) / 2f
            kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return if (lens.isEmpty()) null else SignalMath.median(lens)
    }

    /** Peak-to-peak horizontal hip-mid travel over the rep, in torso-lengths; null if unmeasurable. */
    private fun hipMidTravelTorso(frames: List<List<Landmark3D>>, torsoLen: Float): Float? {
        val xs = frames.mapNotNull { frame ->
            val lh = scored(frame, LEFT_HIP) ?: return@mapNotNull null
            val rh = scored(frame, RIGHT_HIP) ?: return@mapNotNull null
            (lh.x + rh.x) / 2f
        }
        if (xs.isEmpty() || torsoLen <= 0f) return null
        return (xs.max() - xs.min()) / torsoLen
    }

    /** Facing sign at a single frame: nose.x vs shoulder-mid.x. +1 right, -1 left, null if indeterminate. */
    private fun facingSignAt(frame: List<Landmark3D>): Float? {
        val nose = scored(frame, NOSE) ?: return null
        val ls = scored(frame, LEFT_SHOULDER) ?: return null
        val rs = scored(frame, RIGHT_SHOULDER) ?: return null
        val shoulderMidX = (ls.x + rs.x) / 2f
        val offset = nose.x - shoulderMidX
        if (offset == 0f) return null
        return if (offset > 0f) 1f else -1f
    }

    private fun isVisible(frame: List<Landmark3D>, idx: Int): Boolean {
        val lm = frame.getOrNull(idx) ?: return false
        return lm.visibility >= MIN_VISIBILITY
    }

    private fun scored(frame: List<Landmark3D>, idx: Int): Landmark3D? {
        val lm = frame.getOrNull(idx) ?: return null
        return if (lm.visibility >= MIN_VISIBILITY) lm else null
    }
}
