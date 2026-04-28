package com.ttcoachai.shared.models

data class RegionOfInterest(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    companion object {
        fun createDefault(frameWidth: Int, frameHeight: Int): RegionOfInterest {
            val roiWidth = (frameWidth * 0.8f).toInt()
            val roiHeight = (frameHeight * 0.75f).toInt()
            val roiX = (frameWidth - roiWidth) / 2
            val roiY = frameHeight - roiHeight
            return RegionOfInterest(
                x = roiX,
                y = roiY,
                width = roiWidth,
                height = roiHeight
            )
        }
    }
}
