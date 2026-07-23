package com.ttcoachai.util

import com.ttcoachai.managers.RepPoseCapture
import com.ttcoachai.shared.models.CorrectionType

/**
 * Picks the ONE rep capture used to render the "stroke snapshot" skeleton (see
 * [com.ttcoachai.managers.SessionAnalyticsRecorder], `SessionReviewFragment`): the most recent
 * capture flagged with [topFocusType], else the most recent capture with usable keypoints.
 * Pure function — no Android/Room/DB dependency — so it's covered by a plain JVM unit test.
 */
object RepresentativeRepSelector {

    /**
     * [captures] is chronological (oldest -> newest), matching
     * [com.ttcoachai.managers.TrainingStateManager.getRepPoses]. A capture counts as "usable"
     * when BOTH its start and end keypoint lists are non-empty — a snapshot needs both poses to
     * render the two-skeleton view.
     */
    fun select(captures: List<RepPoseCapture>, topFocusType: CorrectionType?): RepPoseCapture? {
        val usable = captures.filter { it.start.isNotEmpty() && it.end.isNotEmpty() }
        if (topFocusType != null) {
            usable.lastOrNull { topFocusType in it.flaggedTypes }?.let { return it }
        }
        return usable.lastOrNull()
    }
}
