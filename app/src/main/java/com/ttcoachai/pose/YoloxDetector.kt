package com.ttcoachai.pose

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.res.AssetManager
import android.graphics.Bitmap
import java.nio.FloatBuffer

/**
 * Stage 1 of the RTMPose pipeline: YOLOX person detection via ONNX Runtime. The ONNX is an
 * mmdeploy export with NMS baked into the graph (see `iosApp/TTCoach/Pose/RTMPOSE_PARITY.md`).
 * Its `dets` output is `[1, N, 5]` float — rows `[x1, y1, x2, y2, score]` in 640-input pixel
 * space, already NMS-filtered and score-descending — and a `labels` `[1, N]` int64 output that
 * rtmlib (and we) IGNORE.
 *
 * Postprocess is just rtmlib's `shape[-1] == 5` branch: divide box coords by the letterbox
 * `ratio` (back to image pixels) and keep `score > 0.3`. No grid decode, no manual NMS.
 *
 * The session MUST be CPU-only: the baked-in NMS has a dynamic-shape node accelerated EPs
 * reject (hard-fails when N=0). Built via `OrtSessionFactory.makeSession(..., cpuOnly = true)`.
 *
 * 1:1 Kotlin port of `iosApp/TTCoach/Pose/YoloxDetector.swift`.
 */

/** A detected person box in ORIGINAL image pixel coordinates. */
data class BoundingBox(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val score: Float)

class YoloxDetector(
    private val session: OrtSession,
    private val scoreThreshold: Float = RtmposeMath.detScoreThreshold
) {

    /** Android: build a CPU-only session from a bundled `.onnx` asset. */
    constructor(
        assetManager: AssetManager,
        assetName: String,
        scoreThreshold: Float = RtmposeMath.detScoreThreshold
    ) : this(
        session = OrtSessionFactory.makeSession(assetManager, assetName, cpuOnly = true),
        scoreThreshold = scoreThreshold
    )

    /** Desktop/CLI-style: build a CPU-only session from an absolute file path. */
    constructor(
        modelPath: String,
        scoreThreshold: Float
    ) : this(
        session = OrtSessionFactory.makeSession(modelPath, cpuOnly = true),
        scoreThreshold = scoreThreshold
    )

    /**
     * Detect persons in a bitmap frame. Returns boxes in original image pixels, score-
     * descending. Returns `[]` when no person clears the score threshold, when the model
     * yields no detections, or on any inference error (logged).
     */
    fun detect(bitmap: Bitmap): List<BoundingBox> {
        return try {
            val (chw, ratio) = BitmapSampler.letterboxToCHW(bitmap, RtmposeMath.detInput)
            val env = OrtEnvironment.getEnvironment()
            val inputName = session.inputNames.firstOrNull() ?: "input"
            val shape = longArrayOf(1L, 3L, RtmposeMath.detInput.toLong(), RtmposeMath.detInput.toLong())
            OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape).use { tensor ->
                session.run(mapOf(inputName to tensor)).use { outputs ->
                    val detsValue = selectDetsValue(outputs) ?: run {
                        android.util.Log.i(TAG, "no float [..,5] 'dets' output found; returning [].")
                        return emptyList()
                    }
                    val detShape = (detsValue.info as TensorInfo).shape
                    val boxCount = if (detShape.size >= 2) detShape[1].toInt() else 0
                    if (boxCount <= 0) return emptyList()

                    val flatBuffer = (detsValue as OnnxTensor).floatBuffer
                    val flat = FloatArray(flatBuffer.remaining())
                    flatBuffer.get(flat)

                    parseDets(detsFlat = flat, boxCount = boxCount, ratio = ratio, scoreThreshold = scoreThreshold)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "detect failed: $e; returning [].")
            emptyList()
        }
    }

    /**
     * Identify the `dets` output by name/shape (do not assume index): the boxes tensor is the
     * float-typed output whose last dim is 5 ([x1,y1,x2,y2,score]). A name containing "det" is
     * only a tiebreaker — it must still pass the float+lastDim==5 check, so a renamed/extra
     * int64 output (e.g. "det_labels") can never be picked and reinterpreted as Float. `labels`
     * (int64) is ignored.
     */
    private fun selectDetsValue(outputs: OrtSession.Result): OnnxValue? {
        fun isBoxes(value: OnnxValue): Boolean {
            val info = value.info as? TensorInfo ?: return false
            return info.type == ai.onnxruntime.OnnxJavaType.FLOAT && info.shape.lastOrNull() == 5L
        }

        val entries = outputs.toList()
        entries.firstOrNull { it.key.lowercase().contains("det") && isBoxes(it.value) }
            ?.let { return it.value }
        entries.firstOrNull { isBoxes(it.value) }?.let { return it.value }
        return null
    }

    companion object {
        private const val TAG = "YoloxDetector"

        /**
         * rtmlib `YOLOX.postprocess` (`shape[-1] == 5` branch). For each of `boxCount` rows
         * `[x1, y1, x2, y2, score]`: divide the 4 coords by `ratio` (back to image pixels), keep
         * rows with `score > scoreThreshold`. Input is already score-descending, so order is
         * preserved. Tolerates `boxCount == 0` (returns []).
         */
        fun parseDets(
            detsFlat: FloatArray,
            boxCount: Int,
            ratio: Float,
            scoreThreshold: Float = RtmposeMath.detScoreThreshold
        ): List<BoundingBox> {
            if (boxCount <= 0 || detsFlat.size < boxCount * 5 || ratio <= 0f) return emptyList()
            val boxes = ArrayList<BoundingBox>(boxCount)
            for (i in 0 until boxCount) {
                val base = i * 5
                val score = detsFlat[base + 4]
                if (score <= scoreThreshold) continue
                boxes.add(
                    BoundingBox(
                        x1 = detsFlat[base] / ratio,
                        y1 = detsFlat[base + 1] / ratio,
                        x2 = detsFlat[base + 2] / ratio,
                        y2 = detsFlat[base + 3] / ratio,
                        score = score
                    )
                )
            }
            return boxes
        }
    }
}
