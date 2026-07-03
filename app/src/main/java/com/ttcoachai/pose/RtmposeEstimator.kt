package com.ttcoachai.pose

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.res.AssetManager
import android.graphics.Bitmap
import java.nio.FloatBuffer

/**
 * Stage 2 of the RTMPose pipeline: top-down keypoint estimation via ONNX Runtime. Given a person
 * bounding box (image pixels, from [YoloxDetector]) and the camera frame, it produces 17 COCO
 * keypoints in ORIGINAL image-pixel coordinates plus a per-keypoint score, by running the RTMPose
 * ONNX model and decoding its SimCC output.
 *
 * All numeric parity math is delegated to [RtmposeMath] and all pixel sampling to [BitmapSampler]
 * — this file only wires preprocess -> inference -> postprocess. 1:1 Kotlin port of
 * `iosApp/TTCoach/Pose/RtmposeEstimator.swift`.
 *
 * MATRIX CONVENTION: [BitmapSampler.affineWarpToCHW] samples output->source (it applies the
 * matrix to each OUTPUT pixel to find the SOURCE read location). rtmlib builds the FORWARD warp
 * (`get_warp_matrix(inv=False)`) and lets `cv2.warpAffine` invert it internally for sampling. To
 * match that sampling, we pass the INVERSE matrix: `RtmposeMath.getWarpMatrix(..., inverse =
 * true)`.
 *
 * Execution provider: CPU EP for this slice (unlike iOS, which uses CoreML for pose — Android
 * parity/determinism comes first; NNAPI/ncnn is a later latency swap behind the same seam). Built
 * via [OrtSessionFactory]'s CPU path (`cpuOnly = true`), never NNAPI.
 */
class RtmposeEstimator(private val runner: SimccRunner) : AutoCloseable {

    /** Already-built session/runner (DI/tests). */
    constructor(session: OrtSession) : this(OrtSessionRunner(session))

    /** Android: build a CPU-only session from a bundled `.onnx` asset. */
    constructor(assetManager: AssetManager, assetName: String) : this(
        OrtSessionFactory.makeSession(assetManager, assetName, cpuOnly = true)
    )

    /** Desktop/CLI-style: build a CPU-only session from an absolute file path. */
    constructor(modelPath: String) : this(
        OrtSessionFactory.makeSession(modelPath, cpuOnly = true)
    )

    /**
     * Estimate 17 COCO keypoints for a single person box.
     *
     * Returns keypoints in ORIGINAL image-pixel coordinates and per-keypoint scores (the raw
     * SimCC `val`; NOT clamped here — the backend clamps to [0,1] at output, T5). Returns 17 zero
     * keypoints / zero scores on inference failure (logged) so callers can treat it like a
     * no-detection frame.
     */
    fun estimate(bitmap: Bitmap, bbox: BoundingBox, imageWidth: Int, imageHeight: Int): EstimateResult {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return estimate(pixels, bitmap.width, bitmap.height, bbox, imageWidth, imageHeight)
    }

    /**
     * Pure-array core of [estimate] — no `android.graphics.Bitmap`, so it is reachable from plain
     * JUnit tests via a stub [SimccRunner] with no ORT/Robolectric involved.
     */
    fun estimate(
        pixels: IntArray,
        pixelsWidth: Int,
        pixelsHeight: Int,
        bbox: BoundingBox,
        imageWidth: Int,
        imageHeight: Int
    ): EstimateResult {
        val empty = EstimateResult(
            keypoints = List(keypointCount) { Vec2(0f, 0f) },
            scores = FloatArray(keypointCount)
        )
        return try {
            // 1-2. center + aspect-fixed scale (reused for warp AND decode).
            val centerScale = RtmposeMath.bboxXyxyToCenterScale(
                x1 = bbox.x1, y1 = bbox.y1, x2 = bbox.x2, y2 = bbox.y2, padding = 1.25f
            )
            val scale = RtmposeMath.topDownAffineScale(
                scale = centerScale.scale, outputW = RtmposeMath.poseInputW, outputH = RtmposeMath.poseInputH
            )

            // 3. inverse (output->source sampling) warp matrix for the sampler.
            val warp = RtmposeMath.getWarpMatrix(
                center = centerScale.center, scale = scale, rotDeg = 0f,
                outputW = RtmposeMath.poseInputW, outputH = RtmposeMath.poseInputH, inverse = true
            )

            // 4. warp + normalize into the input tensor (BGR, (v-mean)/std).
            val tensor = BitmapSampler.affineWarpToCHW(
                pixels = pixels, width = pixelsWidth, height = pixelsHeight,
                warpMatrix = warp, outputW = RtmposeMath.poseInputW, outputH = RtmposeMath.poseInputH,
                meanBGR = RtmposeMath.meanBGR.toFloatArray(), stdBGR = RtmposeMath.stdBGR.toFloatArray()
            )

            // 5. run; read simcc_x / simcc_y by name into K rows each.
            val outputs = runner.run(tensor)
            val simccX = readSimccAxis(outputs, preferring = "simcc_x", otherAxisName = "simcc_y")
            val simccY = readSimccAxis(outputs, preferring = "simcc_y", otherAxisName = "simcc_x")

            // 6-7. argmax decode -> image pixels (aspect-fixed scale + center).
            val result = RtmposeMath.simccMaximum(simccX, simccY)
            val keypoints = RtmposeMath.decodeKeypoints(
                locs = result.locs, center = centerScale.center, scale = scale,
                modelInputW = RtmposeMath.poseInputW, modelInputH = RtmposeMath.poseInputH,
                simccSplitRatio = RtmposeMath.simccSplitRatio
            )
            EstimateResult(keypoints, result.vals)
        } catch (e: Exception) {
            logWarning("estimate failed: $e; returning zero keypoints.")
            empty
        }
    }

    /** A flat float32 SimCC output axis tensor, `shape = [1, K, W]` row-major. */
    data class SimccAxis(val shape: IntArray, val data: FloatArray)

    /** `keypoints` in ORIGINAL image-pixel coords, `scores` the raw SimCC `val` per keypoint. */
    data class EstimateResult(val keypoints: List<Vec2>, val scores: FloatArray)

    /**
     * The ORT-run seam: given the preprocessed input tensor, returns named SimCC axis outputs.
     * The real `OrtSession.run` cannot run in plain JUnit, so post-processing depends only on
     * this functional interface — tests inject a stub, production injects [OrtSessionRunner].
     */
    fun interface SimccRunner {
        fun run(inputTensor: FloatArray): Map<String, SimccAxis>
    }

    /** Production [SimccRunner]: wraps an [OrtSession], building the `[1,3,256,192]` input tensor. */
    class OrtSessionRunner(private val session: OrtSession) : SimccRunner, AutoCloseable {
        override fun close() {
            session.close()
        }

        override fun run(inputTensor: FloatArray): Map<String, SimccAxis> {
            val env = OrtEnvironment.getEnvironment()
            val inputName = session.inputNames.firstOrNull() ?: "input"
            val shape = longArrayOf(1L, 3L, RtmposeMath.poseInputH.toLong(), RtmposeMath.poseInputW.toLong())
            return OnnxTensor.createTensor(env, FloatBuffer.wrap(inputTensor), shape).use { tensor ->
                session.run(mapOf(inputName to tensor)).use { outputs ->
                    outputs.associate { entry ->
                        val onnxTensor = entry.value as OnnxTensor
                        val shapeInt = (onnxTensor.info as TensorInfo).shape.map { it.toInt() }.toIntArray()
                        val buffer = onnxTensor.floatBuffer
                        val flat = FloatArray(buffer.remaining())
                        buffer.get(flat)
                        entry.key to SimccAxis(shapeInt, flat)
                    }
                }
            }
        }
    }

    /** Closes the underlying runner's owned ONNX session when it is [AutoCloseable] (the
     *  production [OrtSessionRunner]); a no-op for stub runners used in tests. */
    override fun close() {
        (runner as? AutoCloseable)?.close()
    }

    companion object {
        /** COCO-17: number of keypoints the model produces. */
        const val keypointCount = 17

        private const val TAG = "RtmposeEstimator"

        /**
         * Log via `android.util.Log`, swallowing the "not mocked" [RuntimeException] plain JUnit
         * throws (no Android framework stub for `Log` there) so failure-path unit tests can run
         * without Robolectric.
         */
        private fun logWarning(message: String) {
            try {
                android.util.Log.w(TAG, message)
            } catch (_: RuntimeException) {
                // No-op under plain JUnit (Log not mocked); real device/Robolectric logs fine.
            }
        }

        /**
         * Read a SimCC axis output (`simcc_x` or `simcc_y`) into `K` rows of `FloatArray`.
         * Selects the value by exact name first, then by a name containing the axis suffix
         * (`_x`/`_y`) while excluding the other axis name. Slices rows using the tensor's own
         * shape `[1, K, W]` (does not hardcode W). Throws when `K != keypointCount`.
         */
        fun readSimccAxis(
            outputs: Map<String, SimccAxis>, preferring: String, otherAxisName: String
        ): Array<FloatArray> {
            val value = selectAxis(outputs, name = preferring, otherAxisName = otherAxisName)
                ?: throw RtmposeEstimatorError.MissingOutput(preferring)
            val shape = value.shape
            if (shape.size != 3) {
                throw RtmposeEstimatorError.UnexpectedShape(preferring, shape)
            }
            val k = shape[1]
            val w = shape[2]
            if (k != keypointCount) {
                throw RtmposeEstimatorError.UnexpectedKeypointCount(preferring, k)
            }
            val flat = value.data
            if (flat.size < k * w) {
                throw RtmposeEstimatorError.UnexpectedShape(preferring, shape)
            }
            return Array(k) { i ->
                val start = i * w
                flat.copyOfRange(start, start + w)
            }
        }

        /**
         * Pick the output for one SimCC axis: exact name match, else a name containing the axis
         * suffix but not the other axis's name (so `simcc_x` doesn't match `simcc_y`).
         */
        private fun selectAxis(
            outputs: Map<String, SimccAxis>, name: String, otherAxisName: String
        ): SimccAxis? {
            outputs[name]?.let { return it }
            val suffix = when {
                name.endsWith("_x") -> "_x"
                name.endsWith("_y") -> "_y"
                else -> name
            }
            val otherLower = otherAxisName.lowercase()
            return outputs.entries.firstOrNull { (key, _) ->
                val lower = key.lowercase()
                lower.contains(suffix) && !lower.contains(otherLower)
            }?.value
        }
    }
}

/** Errors surfaced when reading SimCC outputs. */
sealed class RtmposeEstimatorError(message: String) : Exception(message) {
    class MissingOutput(name: String) :
        RtmposeEstimatorError("RtmposeEstimator: required output '$name' not found.")

    class UnexpectedShape(name: String, shape: IntArray) :
        RtmposeEstimatorError(
            "RtmposeEstimator: output '$name' has unexpected shape ${shape.toList()} (want [1, 17, W])."
        )

    class UnexpectedKeypointCount(name: String, k: Int) :
        RtmposeEstimatorError("RtmposeEstimator: output '$name' has K=$k keypoints, expected 17.")
}
