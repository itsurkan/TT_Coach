package com.ttcoachai.debug

import android.content.Context
import com.ttcoachai.shared.analysis.MeanStrokeBuilder
import com.ttcoachai.shared.detection.JsonStrokeDetector
import com.ttcoachai.shared.models.PoseFrame

/**
 * Extracts the canonical stroke for a drill from a bundled pose fixture.
 *
 * Per user direction (2026-04-19): calibration output isn't raw frames, it's
 * "one representative rep". Even with N=1 real strokes we build a mean stroke
 * via [MeanStrokeBuilder] so the path generalizes when real multi-rep
 * calibration lands — the editor just gets a time-normalized, averaged
 * sequence.
 *
 * Falls back to the untrimmed fixture if the detector doesn't find any stroke
 * (bad fixture / config mismatch) so the editor at least boots.
 */
object CanonicalStrokeLoader {

    data class Result(
        val frames: List<PoseFrame>,
        val intervalMs: Long,
        val strokeCount: Int,
        val meanStrokeLength: Int
    )

    fun loadForehandDrive(
        context: Context,
        targetLength: Int = MeanStrokeBuilder.DEFAULT_TARGET_LENGTH
    ): Result {
        val loaded = AssetPoseFrameLoader.load(context, "fixtures/forehand_drive.json")
        val detection = JsonStrokeDetector().detect(loaded.frames)
        if (detection.strokes.isEmpty()) {
            return Result(loaded.frames, loaded.intervalMs, strokeCount = 0, meanStrokeLength = loaded.frames.size)
        }
        val mean = MeanStrokeBuilder.build(
            strokes = detection.strokes,
            allFrames = loaded.frames,
            targetLength = targetLength,
            intervalMs = loaded.intervalMs
        )
        if (mean.isEmpty()) {
            return Result(loaded.frames, loaded.intervalMs, strokeCount = detection.strokes.size, meanStrokeLength = loaded.frames.size)
        }
        // Trim leading setup frames — the detector's stroke boundary includes a
        // brief stance before the backswing really begins.
        val trimmed = if (mean.size > FOREHAND_DRIVE_TRIM_LEADING) {
            mean.drop(FOREHAND_DRIVE_TRIM_LEADING)
        } else {
            mean
        }
        // Post-process the averaged stroke:
        //   1. Smooth upper body with a 5-frame centered moving average so the
        //      loop reads as continuous motion (rep-averaging alone leaves kinks
        //      where reps don't align frame-by-frame).
        //   2. Freeze legs to a canonical static stance with a 145° knee angle —
        //      MediaPipe knee/ankle noise is too high for useful averaging and
        //      the editor's job is to show the reference shape, not leg motion.
        val smoothed = UpperBodySmoother.smooth(trimmed)
        val cleaned = LegCanonicalizer.canonicalize(smoothed, targetKneeAngleDeg = 145f)
        return Result(
            frames = cleaned,
            intervalMs = loaded.intervalMs,
            strokeCount = detection.strokes.size,
            meanStrokeLength = cleaned.size
        )
    }

    private const val FOREHAND_DRIVE_TRIM_LEADING = 5
}
