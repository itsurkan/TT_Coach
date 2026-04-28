package com.ttcoachai.shared.tracking

import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.BallDetectionStatus
import com.ttcoachai.shared.models.BallPosition2D
import com.ttcoachai.shared.models.DataSource
import com.ttcoachai.shared.models.ParabolicFit
import kotlin.math.sqrt

/**
 * Fits a decoupled parabolic trajectory model to detected ball positions.
 *
 * Model (research R7):
 *   x(t) = ax + bx*t          (linear horizontal — constant velocity)
 *   y(t) = ay + by*t + cy*t²  (quadratic vertical — gravity effect)
 *
 * All math is pure `kotlin.math` — no JVM-specific calls — for KMP commonMain (research R16).
 * Linear systems are solved analytically via Cramer's rule (2×2 and 3×3).
 */
object TrajectoryFilter {

    /**
     * Fit a parabolic (or linear if only 2 points) model to [detections].
     *
     * @return ParabolicFit coefficients, or null if fewer than 2 detections provided.
     */
    fun fit(detections: List<BallDetection>): ParabolicFit? {
        val detected = detections.filter { it.status == BallDetectionStatus.DETECTED }
        if (detected.size < 2) return null

        // Normalise time to frame indices relative to first frame (integer units)
        val t0 = detected.first().frameIndex.toDouble()
        val ts = detected.map { (it.frameIndex - t0) }
        val xs = detected.map { it.x.toDouble() }
        val ys = detected.map { it.y.toDouble() }

        // Fit x (linear): x = ax + bx*t — 2-parameter least squares (normal equations)
        val (ax, bx) = fitLinear(ts, xs)

        // Fit y:
        //   With 2 points → linear (cy = 0)
        //   With 3+ points → quadratic
        val (ay, by, cy) = if (detected.size == 2) {
            val (a, b) = fitLinear(ts, ys)
            Triple(a, b, 0.0)
        } else {
            fitQuadratic(ts, ys)
        }

        return ParabolicFit(ax = ax, bx = bx, ay = ay, by = by, cy = cy)
    }

    /**
     * Evaluate the fitted model at [timestampMs], using [referenceTimestampMs] as t=0.
     *
     * Time unit: the model uses frame-index units (i.e., t = (frameTs - refTs) / frameDurationMs).
     * For the purposes of this function we accept raw ms and convert internally using a nominal
     * frame duration consistent with the frame rate used during fitting.
     *
     * @return Pair(x, y) in the same normalised coordinate space as the fit input.
     */
    fun evaluate(
        fit: ParabolicFit,
        timestampMs: Long,
        referenceTimestampMs: Long
    ): Pair<Float, Float> {
        // Convert ms delta to frame-index units assuming the same nominal frame spacing
        // the fit was done in. Since fit uses integer frame indices, we approximate t
        // by dividing ms elapsed by the stored nominal frame duration.
        // If frameDurationMs is unknown here, we use 33ms (≈30FPS) as default.
        val frameDurationMs = 33.0
        val t = (timestampMs - referenceTimestampMs).toDouble() / frameDurationMs
        val x = (fit.ax + fit.bx * t).toFloat()
        val y = (fit.ay + fit.by * t + fit.cy * t * t).toFloat()
        return Pair(x, y)
    }

    /**
     * Compute RMS deviation of [detections] from the fitted model.
     *
     * Uses the detected positions' frame indices as t values (relative to the first detection).
     */
    fun rmsError(fit: ParabolicFit, detections: List<BallDetection>): Double {
        val detected = detections.filter { it.status == BallDetectionStatus.DETECTED }
        if (detected.isEmpty()) return 0.0

        val t0 = detected.first().frameIndex.toDouble()
        var sumSq = 0.0
        for (d in detected) {
            val t = d.frameIndex - t0
            val xPred = fit.ax + fit.bx * t
            val yPred = fit.ay + fit.by * t + fit.cy * t * t
            val dx = d.x - xPred
            val dy = d.y - yPred
            sumSq += dx * dx + dy * dy
        }
        return sqrt(sumSq / detected.size)
    }

    /**
     * Generate a [BallPosition2D] for every frame in [startFrameIndex]..[endFrameIndex].
     * Detected frames keep their original positions (tagged DETECTED);
     * gap frames are evaluated from the fitted model (tagged INTERPOLATED).
     *
     * @param frameDurationMs Duration of one frame in ms (used to compute frame timestamps)
     */
    fun fillGaps(
        fit: ParabolicFit,
        detections: List<BallDetection>,
        startFrameIndex: Int,
        endFrameIndex: Int,
        frameDurationMs: Long
    ): List<BallPosition2D> {
        val detectedByFrame = detections
            .filter { it.status == BallDetectionStatus.DETECTED }
            .associateBy { it.frameIndex }

        val t0 = startFrameIndex.toDouble()
        val result = mutableListOf<BallPosition2D>()

        for (frameIdx in startFrameIndex..endFrameIndex) {
            val det = detectedByFrame[frameIdx]
            if (det != null) {
                result.add(
                    BallPosition2D(
                        x = det.x, y = det.y,
                        frameIndex = frameIdx,
                        timestampMs = frameIdx * frameDurationMs,
                        source = DataSource.DETECTED
                    )
                )
            } else {
                val t = frameIdx - t0
                val x = (fit.ax + fit.bx * t).toFloat()
                val y = (fit.ay + fit.by * t + fit.cy * t * t).toFloat()
                result.add(
                    BallPosition2D(
                        x = x.coerceIn(0f, 1f),
                        y = y.coerceIn(0f, 1f),
                        frameIndex = frameIdx,
                        timestampMs = frameIdx * frameDurationMs,
                        source = DataSource.INTERPOLATED
                    )
                )
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Internal math — Cramer's rule (research R16)
    // -------------------------------------------------------------------------

    /**
     * Least-squares linear fit: y = a + b*t
     * Solves the 2×2 normal equations:
     *   [n,    Σt ] [a]   [Σy  ]
     *   [Σt,   Σt²] [b] = [Σty ]
     */
    private fun fitLinear(ts: List<Double>, ys: List<Double>): Pair<Double, Double> {
        val n = ts.size.toDouble()
        val st  = ts.sum()
        val st2 = ts.sumOf { it * it }
        val sy  = ys.sum()
        val sty = ts.zip(ys).sumOf { (t, y) -> t * y }

        // det = n*Σt² - (Σt)²
        val det = n * st2 - st * st
        if (det == 0.0) {
            // All t values equal → constant function
            return Pair(ys.average(), 0.0)
        }
        val a = (sy * st2 - sty * st) / det
        val b = (n * sty - st * sy) / det
        return Pair(a, b)
    }

    /**
     * Least-squares quadratic fit: y = a + b*t + c*t²
     * Solves the 3×3 normal equations via Cramer's rule:
     *   [n,    Σt,   Σt² ] [a]   [Σy  ]
     *   [Σt,   Σt²,  Σt³ ] [b] = [Σty ]
     *   [Σt²,  Σt³,  Σt⁴ ] [c]   [Σt²y]
     */
    private fun fitQuadratic(ts: List<Double>, ys: List<Double>): Triple<Double, Double, Double> {
        val n   = ts.size.toDouble()
        val st  = ts.sum()
        val st2 = ts.sumOf { it * it }
        val st3 = ts.sumOf { it * it * it }
        val st4 = ts.sumOf { it * it * it * it }
        val sy  = ys.sum()
        val sty = ts.zip(ys).sumOf { (t, y) -> t * y }
        val st2y = ts.zip(ys).sumOf { (t, y) -> t * t * y }

        // 3×3 determinant via cofactor expansion along first row
        val det = det3(
            n,   st,  st2,
            st,  st2, st3,
            st2, st3, st4
        )

        if (det == 0.0) {
            // Degenerate: fall back to linear
            val (a, b) = fitLinear(ts, ys)
            return Triple(a, b, 0.0)
        }

        // Replace each column with the RHS vector and compute sub-determinant (Cramer's rule)
        val a = det3(sy,  st,  st2, sty, st2, st3, st2y, st3, st4) / det
        val b = det3(n,   sy,  st2, st,  sty, st3, st2,  st2y,st4) / det
        val c = det3(n,   st,  sy,  st,  st2, sty, st2,  st3, st2y) / det

        return Triple(a, b, c)
    }

    /** Determinant of a 3×3 matrix supplied in row-major order. */
    private fun det3(
        a00: Double, a01: Double, a02: Double,
        a10: Double, a11: Double, a12: Double,
        a20: Double, a21: Double, a22: Double
    ): Double =
        a00 * (a11 * a22 - a12 * a21) -
        a01 * (a10 * a22 - a12 * a20) +
        a02 * (a10 * a21 - a11 * a20)
}
