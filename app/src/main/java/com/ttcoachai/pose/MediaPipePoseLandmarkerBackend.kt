package com.ttcoachai.pose

// MediaPipePoseLandmarkerBackend.kt — THROWAWAY PROTOTYPE (FPS A/B bench only,
// PoseBenchmarkActivity). Wraps MediaPipe Tasks Vision's PoseLandmarker (BlazePose, 33
// landmarks) behind the PoseBackend seam so it can be swapped in for RtmposeBackend /
// MoveNetBackend behind RtmposeFrameProcessor unmodified. RunningMode.IMAGE (synchronous
// detect()) matches PoseBackend.estimatePose()'s existing blocking contract — no LIVE_STREAM
// callback bridging needed for a throwaway tool.
//
// This is NOT a restoration of the deleted MediaPipe production UI path (PoseLandmarkerHelper /
// PoseLandmarkerProcessor / PoseLandmarkerConfig, deleted 2026-07-24) — those are gone for good.
// This is fresh benchmark-only code with no callers outside PoseBenchmarkActivity.

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.ttcoachai.shared.models.Keypoint2D

class MediaPipePoseLandmarkerBackend(
    context: Context,
    modelAssetName: String,
    delegate: Delegate
) : PoseBackend, AutoCloseable {

    companion object {
        // COCO-17 index -> BlazePose-33 index. BlazePose-only points (face-mesh detail,
        // fingers, heels, foot index 29-32) have no COCO-17 counterpart and are dropped.
        private val COCO17_TO_BLAZEPOSE33 = intArrayOf(
            0,  // 0  nose            <- BlazePose 0  nose
            2,  // 1  left_eye        <- BlazePose 2  left_eye
            5,  // 2  right_eye       <- BlazePose 5  right_eye
            7,  // 3  left_ear        <- BlazePose 7  left_ear
            8,  // 4  right_ear       <- BlazePose 8  right_ear
            11, // 5  left_shoulder   <- BlazePose 11 left_shoulder
            12, // 6  right_shoulder  <- BlazePose 12 right_shoulder
            13, // 7  left_elbow      <- BlazePose 13 left_elbow
            14, // 8  right_elbow     <- BlazePose 14 right_elbow
            15, // 9  left_wrist      <- BlazePose 15 left_wrist
            16, // 10 right_wrist     <- BlazePose 16 right_wrist
            23, // 11 left_hip        <- BlazePose 23 left_hip
            24, // 12 right_hip       <- BlazePose 24 right_hip
            25, // 13 left_knee       <- BlazePose 25 left_knee
            26, // 14 right_knee      <- BlazePose 26 right_knee
            27, // 15 left_ankle      <- BlazePose 27 left_ankle
            28  // 16 right_ankle     <- BlazePose 28 right_ankle
        )
    }

    private val landmarker: PoseLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setDelegate(delegate)
            .setModelAssetPath(modelAssetName)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .build()
        // Throws on failure (bad model asset, unsupported GPU delegate, etc.) — intentionally
        // not caught here; PoseBenchmarkActivity.switchBackend() already Toasts + logs it.
        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    override fun estimatePose(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<Keypoint2D> {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = landmarker.detect(mpImage)
        val blazepose33: List<NormalizedLandmark> = result.landmarks().firstOrNull() ?: return emptyList()
        if (blazepose33.size < 29) return emptyList()

        return COCO17_TO_BLAZEPOSE33.map { blazeIndex ->
            val lm = blazepose33[blazeIndex]
            Keypoint2D(
                x = lm.x().coerceIn(0f, 1f),
                y = lm.y().coerceIn(0f, 1f),
                score = lm.visibility().orElse(0f).coerceIn(0f, 1f)
            )
        }
    }

    override fun close() {
        landmarker.close()
    }
}
