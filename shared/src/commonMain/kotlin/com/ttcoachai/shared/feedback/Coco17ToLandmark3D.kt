package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.Landmark3D

/**
 * Converts a COCO-17 keypoint list (RTM live/baseline path) into the 33-slot MediaPipe-BlazePose
 * layout that [SnapshotGeometry] / `com.ttcoachai.views.PoseSnapshotView` already index against,
 * so the existing snapshot renderer needs zero changes to draw RTM poses.
 *
 * x/y are copied through UNCHANGED — still per-axis normalized exactly as they arrive in the COCO
 * frame (x / videoWidth, y / videoHeight); this mapper does no xScale/aspect correction, matching
 * how `PoseSnapshotView` already treats legacy [Landmark3D] frames (uniform scale-to-fit, see its
 * class doc). [Keypoint2D.score] becomes [Landmark3D.visibility]; z is always 0f (RTM is 2D, no
 * depth).
 *
 * Mapping (COCO-17 index -> MediaPipe-33 index), derived by cross-checking every index
 * [SnapshotGeometry]/`PoseSnapshotView` actually reads:
 * nose 0->0; left eye 1->2 (MP's plain "left eye" center point, distinct from the inner/outer
 * variants at 1/3); right eye 2->5 (same reasoning); left ear 3->7; right ear 4->8;
 * left/right shoulder 5/6->11/12; left/right elbow 7/8->13/14; left/right wrist 9/10->15/16;
 * left/right hip 11/12->23/24; left/right knee 13/14->25/26; left/right ankle 15/16->27/28.
 *
 * Slots with no COCO-17 source (face detail 1,3,4,6,9,10; fingers 17-22; heel/foot-index 29-32)
 * are left as invisible placeholders (visibility = 0f) — [SnapshotGeometry]'s
 * MIN_VISIBILITY = 0.5f gate skips them in rendering.
 */
object Coco17ToLandmark3D {

    private const val SLOT_COUNT = 33
    private val INVISIBLE = Landmark3D(x = 0f, y = 0f, z = 0f, visibility = 0f)

    // COCO-17 index -> MediaPipe-33 index.
    private val COCO_TO_MEDIAPIPE = mapOf(
        0 to 0, 1 to 2, 2 to 5, 3 to 7, 4 to 8,
        5 to 11, 6 to 12, 7 to 13, 8 to 14, 9 to 15, 10 to 16,
        11 to 23, 12 to 24, 13 to 25, 14 to 26, 15 to 27, 16 to 28
    )

    /** Always returns a 33-element list; unmapped slots are invisible placeholders. */
    fun map(coco17: List<Keypoint2D>): List<Landmark3D> {
        val out = MutableList(SLOT_COUNT) { INVISIBLE }
        for ((cocoIdx, mpIdx) in COCO_TO_MEDIAPIPE) {
            val kp = coco17.getOrNull(cocoIdx) ?: continue
            out[mpIdx] = Landmark3D(x = kp.x, y = kp.y, z = 0f, visibility = kp.score)
        }
        return out
    }
}
