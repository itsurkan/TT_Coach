package com.ttcoachai.pose

// MoveNetBackend.kt — THROWAWAY PROTOTYPE (FPS A/B bench only). PoseBackend wrapper around
// MoveNetEstimator so it can be swapped in for other PoseBackend implementations behind
// RtmposeFrameProcessor unmodified.

import android.content.Context
import android.graphics.Bitmap
import com.ttcoachai.shared.models.Keypoint2D

class MoveNetBackend(context: Context) : PoseBackend, AutoCloseable {

    private val estimator = MoveNetEstimator(context)

    override fun estimatePose(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<Keypoint2D> =
        estimator.estimate(bitmap, frameWidth, frameHeight)

    override fun close() {
        estimator.close()
    }
}
