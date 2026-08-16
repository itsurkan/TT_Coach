package com.ttcoachai.util

import com.ttcoachai.managers.RepPoseCapture
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Keypoint2D

/**
 * Resolves WHICH rep captures the last-10-strokes carousel (`RepCarouselView`) should render for
 * Session Review, in priority order. Pure function — no Android/Room dependency — so it's covered
 * by a plain JVM unit test. A capture is "usable" when both its start and end keypoint lists are
 * non-empty, mirroring [RepresentativeRepSelector]'s rule.
 *
 * Priority:
 * 1. [persistedCaptures] — the new `SessionAnalyticsEntity.repCapturesJson` column (up to 10,
 *    written by `SessionAnalyticsRecorder.record`). Works when the report is reopened later from
 *    History, not just right after the session.
 * 2. [inMemoryCaptures] — `TrainingStateManager.getRepPoses()`, available only right after a
 *    session (in-memory singleton), used when the session predates the persisted column or the
 *    save raced ahead of persistence.
 * 3. [legacyStart]/[legacyEnd] — the older single persisted representative pair
 *    (`repStartPoseJson`/`repEndPoseJson`), rendered as a one-page carousel. Rows written before
 *    this feature carry no flagged-types info, so the synthetic capture's `flaggedTypes` is
 *    approximated from [topFocusType] (present -> treat as flagged, matching
 *    `RepresentativeRepSelector`'s preference for a flagged rep when choosing this pair originally).
 * 4. Nothing usable anywhere -> empty list; the caller hides the card.
 */
object RepCarouselDataSource {

    private fun isUsable(capture: RepPoseCapture) = capture.start.isNotEmpty() && capture.end.isNotEmpty()

    fun resolve(
        persistedCaptures: List<RepPoseCapture>,
        inMemoryCaptures: List<RepPoseCapture>,
        legacyStart: List<Keypoint2D>,
        legacyEnd: List<Keypoint2D>,
        topFocusType: CorrectionType?,
    ): List<RepPoseCapture> {
        persistedCaptures.filter(::isUsable).takeIf { it.isNotEmpty() }?.let { return it }
        inMemoryCaptures.filter(::isUsable).takeIf { it.isNotEmpty() }?.let { return it }
        if (legacyStart.isNotEmpty() && legacyEnd.isNotEmpty()) {
            val flaggedTypes = if (topFocusType != null) setOf(topFocusType) else emptySet()
            return listOf(RepPoseCapture(atMs = 0L, start = legacyStart, end = legacyEnd, flaggedTypes = flaggedTypes))
        }
        return emptyList()
    }
}
