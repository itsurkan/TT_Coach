package com.ttcoachai.pose

// RtmposeBackend.kt
//
// The in-app `PoseBackend` that runs the two-stage RTMPose pipeline (YOLOX person detection ->
// RTMPose top-down keypoints) per `RTMPOSE_PARITY.md`. It composes `YoloxDetector` (T3, CPU EP —
// its baked-in NMS breaks accelerated EPs) and `RtmposeEstimator` (T4, CPU EP for this slice),
// then selects the "best person" the way the desktop golden does: argmax over detections of MEAN
// keypoint score (NOT detection score), and normalizes that person's keypoints into the shared
// schema-v2 convention (per-axis normalize + clamp).
//
// Output contract (PoseBackend / docs/pose_json_schema_v2.md):
//   - 17 COCO keypoints in `Coco17` order (array index == joint index).
//   - x = clamp(px.x / frameWidth, 0, 1), y = clamp(px.y / frameHeight, 0, 1).
//   - score = clamp(rawScore, 0, 1).
//   - EMPTY list when no person is detected.
//
// The `normalize(...)` and `selectBest(...)` companions are PURE (no ORT, no Bitmap), so they are
// unit-tested directly; the per-frame `estimatePose` just wires the two inference stages to them.
// 1:1 Kotlin port of `iosApp/TTCoach/Pose/RTMPoseBackend.swift`.

import android.content.Context
import android.graphics.Bitmap
import com.ttcoachai.shared.models.Keypoint2D
import kotlin.math.max
import kotlin.math.min

class RtmposeBackend(
    private val detector: YoloxDetector,
    private val estimator: RtmposeEstimator
) : PoseBackend {

    // MARK: - Init

    /** Android: build both stages from bundled `.onnx` assets (CPU EP for both this slice). */
    constructor(
        context: Context,
        yoloxAssetName: String = DEFAULT_YOLOX_ASSET_NAME,
        rtmposeAssetName: String = DEFAULT_RTMPOSE_ASSET_NAME
    ) : this(
        detector = YoloxDetector(context.assets, yoloxAssetName),
        estimator = RtmposeEstimator(context.assets, rtmposeAssetName)
    )

    // MARK: - PoseBackend

    override fun estimatePose(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<Keypoint2D> {
        // Detect EVERY frame for parity with the desktop golden (which detects per frame).
        // A future latency slice could reuse the previous box on skipped frames — deliberately
        // NOT implemented here.
        val boxes = detector.detect(bitmap)
        if (boxes.isEmpty()) return emptyList()

        // Run RTMPose on EACH box; pick the detection with the highest mean score (matches the
        // export's `best_person`, which selects by pose score not det score).
        val candidates = boxes.map { box ->
            estimator.estimate(bitmap, box, frameWidth, frameHeight)
        }

        val bestIdx = selectBest(candidates) ?: return emptyList()
        val chosen = candidates[bestIdx]
        return normalize(chosen.keypoints, chosen.scores, frameWidth, frameHeight)
    }

    companion object {
        /** iOS default asset base-name (`RTMPoseBackend.swift`'s `yoloxResource` default). */
        const val DEFAULT_YOLOX_ASSET_NAME = "yolox_m_8xb8-300e_humanart-c2c7a14a"

        /** iOS default asset base-name (`RTMPoseBackend.swift`'s `rtmposeResource` default). */
        const val DEFAULT_RTMPOSE_ASSET_NAME =
            "rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504"

        /** COCO-17 keypoint count (one Keypoint2D per joint, in index order). */
        const val keypointCount = RtmposeEstimator.keypointCount

        // MARK: - Pure, unit-testable helpers

        /**
         * Per-axis normalize + clamp a person's pixel keypoints into the shared schema-v2
         * convention: `x = clamp(px.x / frameWidth, 0, 1)`, `y = clamp(px.y / frameHeight, 0,
         * 1)`, `score = clamp(scores[i], 0, 1)`. Emits exactly `keypoints.size` `Keypoint2D` in
         * order (array index == COCO joint index). Guards against a short `scores` array.
         */
        fun normalize(
            keypoints: List<Vec2>,
            scores: FloatArray,
            frameWidth: Int,
            frameHeight: Int
        ): List<Keypoint2D> {
            if (frameWidth <= 0 || frameHeight <= 0) return emptyList()
            val w = frameWidth.toFloat()
            val h = frameHeight.toFloat()
            return keypoints.mapIndexed { i, px ->
                val rawScore = scores.getOrElse(i) { 0f }
                Keypoint2D(
                    x = clamp01(px.x / w),
                    y = clamp01(px.y / h),
                    score = clamp01(rawScore)
                )
            }
        }

        /**
         * Argmax over candidates of MEAN keypoint score (the export's `best_person`). Returns the
         * index of the best candidate, or `null` when the list is empty. A candidate with no
         * scores contributes mean 0. Ties resolve to the first (lowest index) candidate — strict
         * `>` keeps the earlier one.
         */
        fun selectBest(candidates: List<RtmposeEstimator.EstimateResult>): Int? {
            if (candidates.isEmpty()) return null
            var bestIdx = 0
            var bestMean = meanScore(candidates[0].scores)
            for (i in 1 until candidates.size) {
                val m = meanScore(candidates[i].scores)
                if (m > bestMean) {
                    bestMean = m
                    bestIdx = i
                }
            }
            return bestIdx
        }

        // MARK: - Private

        private fun meanScore(scores: FloatArray): Float {
            if (scores.isEmpty()) return 0f
            var sum = 0f
            for (s in scores) sum += s
            return sum / scores.size
        }

        private fun clamp01(v: Float): Float = max(0f, min(1f, v))
    }
}
