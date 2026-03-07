package com.ttcoachai.tracking

import com.ttcoachai.shared.models.BallDetection
import com.ttcoachai.shared.models.RegionOfInterest

/**
 * Manages the Region of Interest used for ball detection.
 *
 * [createDefault] covers the lower 75% height and central 80% width of the frame —
 * a proportional ROI that captures the ball in standard camera placements (research R4).
 *
 * [adapt] expands or shifts the ROI based on recent ball positions so that the ROI
 * stays centred around the ball's movement area over time.
 */
class ROIManager {

    companion object {
        // Default ROI proportions (research R4)
        private const val HEIGHT_FRACTION = 0.75f  // Lower 75% of frame height
        private const val WIDTH_FRACTION  = 0.80f  // Central 80% of frame width

        // Adapt: how much padding to add around the bounding box of recent detections
        private const val ADAPT_PADDING_FRACTION = 0.10f  // 10% of frame dimension
        // Clamp adaptation: keep ROI within [MIN_FRACTION, 1.0] of original default size
        private const val MIN_ROI_WIDTH_FRACTION  = 0.30f
        private const val MIN_ROI_HEIGHT_FRACTION = 0.25f
    }

    /**
     * Create a default ROI for a given frame size.
     * Covers the lower 75% of height and central 80% of width.
     *
     * @param frameWidth  Full frame width in pixels
     * @param frameHeight Full frame height in pixels
     * @return ROI within frame bounds
     */
    fun createDefault(frameWidth: Int, frameHeight: Int): RegionOfInterest {
        val roiWidth  = (frameWidth  * WIDTH_FRACTION).toInt()
        val roiHeight = (frameHeight * HEIGHT_FRACTION).toInt()
        val roiX = (frameWidth  - roiWidth)  / 2
        val roiY = frameHeight - roiHeight
        return RegionOfInterest(x = roiX, y = roiY, width = roiWidth, height = roiHeight)
    }

    /**
     * Adapt the ROI based on recent ball detection positions.
     *
     * Computes the bounding box of detected positions (in pixel space derived from
     * normalised coordinates multiplied by an assumed frame size matching the current ROI)
     * and expands it with padding. Falls back to [currentRoi] if [recentDetections] is empty.
     *
     * @param currentRoi        Current ROI (defines the pixel frame reference)
     * @param recentDetections  Recent DETECTED ball positions (normalised 0-1 in full frame)
     * @return Adjusted ROI clamped to the bounds implied by the current ROI's frame
     */
    fun adapt(
        currentRoi: RegionOfInterest,
        recentDetections: List<BallDetection>
    ): RegionOfInterest {
        if (recentDetections.isEmpty()) return currentRoi

        // Estimate the full frame size from the current ROI extent
        // (ROI covers lower HEIGHT_FRACTION of frame, central WIDTH_FRACTION)
        val estimatedFrameWidth  = (currentRoi.width  / WIDTH_FRACTION).toInt()
        val estimatedFrameHeight = (currentRoi.height / HEIGHT_FRACTION).toInt()

        val paddingX = (estimatedFrameWidth  * ADAPT_PADDING_FRACTION).toInt()
        val paddingY = (estimatedFrameHeight * ADAPT_PADDING_FRACTION).toInt()

        // Convert normalised positions to pixel coords in the estimated full frame
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE

        for (det in recentDetections) {
            val px = (det.x * estimatedFrameWidth).toInt()
            val py = (det.y * estimatedFrameHeight).toInt()
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
        }

        val newX = (minX - paddingX).coerceAtLeast(0)
        val newY = (minY - paddingY).coerceAtLeast(0)
        val newRight  = (maxX + paddingX).coerceAtMost(estimatedFrameWidth)
        val newBottom = (maxY + paddingY).coerceAtMost(estimatedFrameHeight)

        val minWidth  = (estimatedFrameWidth  * MIN_ROI_WIDTH_FRACTION).toInt()
        val minHeight = (estimatedFrameHeight * MIN_ROI_HEIGHT_FRACTION).toInt()

        val newWidth  = (newRight  - newX).coerceAtLeast(minWidth)
        val newHeight = (newBottom - newY).coerceAtLeast(minHeight)

        // Clamp to frame
        val clampedX = newX.coerceAtMost(estimatedFrameWidth  - newWidth)
        val clampedY = newY.coerceAtMost(estimatedFrameHeight - newHeight)

        return RegionOfInterest(
            x = clampedX.coerceAtLeast(0),
            y = clampedY.coerceAtLeast(0),
            width  = newWidth,
            height = newHeight
        )
    }
}
