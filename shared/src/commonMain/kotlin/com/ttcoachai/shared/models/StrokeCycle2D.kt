package com.ttcoachai.shared.models

/**
 * A full stroke cycle = optional backswing + forward drive.
 *
 * [startFrame] = backswing.startFrame when paired, drive.startFrame when unpaired.
 * [endFrame]   = drive.endFrame always.
 * [peakFrame] and [peakSpeed] forward to [drive].
 *
 * Construct via [StrokeCycle2D.of] — do not call the constructor directly from
 * pairing logic (the factory encodes the start/end convention).
 */
data class StrokeCycle2D(
    val backswing: Stroke2D?,
    val drive: Stroke2D,
    val startFrame: Int,
    val endFrame: Int,
) {
    val peakFrame: Int get() = drive.peakFrame
    val peakSpeed: Float get() = drive.peakSpeed

    companion object {
        /** Build a cycle applying the start/end convention:
         *  start = backswing.startFrame ?? drive.startFrame; end = drive.endFrame. */
        fun of(backswing: Stroke2D?, drive: Stroke2D): StrokeCycle2D = StrokeCycle2D(
            backswing = backswing,
            drive = drive,
            startFrame = backswing?.startFrame ?: drive.startFrame,
            endFrame = drive.endFrame,
        )
    }
}
