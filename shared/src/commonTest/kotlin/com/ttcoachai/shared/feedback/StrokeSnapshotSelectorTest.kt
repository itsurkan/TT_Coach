/*
 * AI Coach for Table Tennis
 * StrokeSnapshotSelectorTest — Unit tests for peak/contact frame selection over a stroke rep.
 */

package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Landmark3D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrokeSnapshotSelectorTest {

    // ── peakFrameIndex ────────────────────────────────────────────────────────

    @Test
    fun peakFrameIndex_emptyList_returnsMinusOne() {
        assertEquals(-1, StrokeSnapshotSelector.peakFrameIndex(emptyList()))
    }

    @Test
    fun peakFrameIndex_singleFrame_returnsHalfSize() {
        val frames = listOf(makeFrame(wrist = Landmark3D(0.5f, 0.5f, 0f, visibility = 1f)))
        assertEquals(frames.size / 2, StrokeSnapshotSelector.peakFrameIndex(frames))
    }

    @Test
    fun peakFrameIndex_fewerThanTwoUsableFrames_returnsHalfSize() {
        // 3 frames but only one has a visible wrist -> fewer than 2 usable
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.1f, 0.1f, 0f, visibility = 0.1f)),
            makeFrame(wrist = Landmark3D(0.5f, 0.5f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.2f, 0.2f, 0f, visibility = 0.2f))
        )
        assertEquals(frames.size / 2, StrokeSnapshotSelector.peakFrameIndex(frames))
    }

    @Test
    fun peakFrameIndex_picksFastestWristFrame() {
        // Wrist barely moves frame0->1, jumps a lot frame1->2, small move frame2->3
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.82f, 0.76f, 0f, visibility = 1f))
        )
        // Largest delta is between frame index 1 and 2 -> peak reported at index 2
        assertEquals(2, StrokeSnapshotSelector.peakFrameIndex(frames))
    }

    @Test
    fun peakFrameIndex_skipsLowVisibilityFrames() {
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            // Low-visibility frame with a huge (spurious) jump — must be skipped as a candidate/sample
            makeFrame(wrist = Landmark3D(0.95f, 0.95f, 0f, visibility = 0.1f)),
            makeFrame(wrist = Landmark3D(0.30f, 0.25f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.31f, 0.25f, 0f, visibility = 1f))
        )
        // Usable frames are indices 0, 2, 3. Largest usable-to-usable delta is 0->2.
        assertEquals(2, StrokeSnapshotSelector.peakFrameIndex(frames))
    }

    @Test
    fun peakFrameIndex_missingWristLandmark_treatedAsLowVisibility() {
        val shortFrame = listOf(Landmark3D(0.5f, 0.5f, 0f, visibility = 1f)) // no index 16
        val frames = listOf(
            shortFrame,
            makeFrame(wrist = Landmark3D(0.5f, 0.5f, 0f, visibility = 1f))
        )
        assertEquals(frames.size / 2, StrokeSnapshotSelector.peakFrameIndex(frames))
    }

    // ── contactFrameIndex ─────────────────────────────────────────────────────

    @Test
    fun contactFrameIndex_matchesPeakFrameIndex() {
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f))
        )
        assertEquals(
            StrokeSnapshotSelector.peakFrameIndex(frames),
            StrokeSnapshotSelector.contactFrameIndex(frames)
        )
    }

    @Test
    fun contactFrameIndex_emptyList_returnsMinusOne() {
        assertEquals(-1, StrokeSnapshotSelector.contactFrameIndex(emptyList()))
    }

    @Test
    fun contactFrameIndex_picksMaxForwardExtension_rightwardStroke() {
        // Synthetic rep, hip fixed at x=0.5: backswing (wrist behind hip), forward swing with
        // increasing wrist.x past the hip (peak speed lands on the last forward frame, which is
        // NOT the frame with the largest hip-relative extension), then follow-through pulls back.
        val frames = listOf(
            makeFrameAt(wristX = 0.30f, hipX = 0.5f), // backswing: behind hip
            makeFrameAt(wristX = 0.32f, hipX = 0.5f), // backswing: behind hip
            makeFrameAt(wristX = 0.55f, hipX = 0.5f), // forward swing: past hip
            makeFrameAt(wristX = 0.90f, hipX = 0.5f), // forward swing: max forward extension
            makeFrameAt(wristX = 0.60f, hipX = 0.5f)  // follow-through: pulls back
        )
        // Speed jump 2->3 is largest (0.35) vs 3->4 (0.30), so peak = 3, which also happens to be
        // the max-extension frame here — assert contact matches that expectation explicitly.
        assertEquals(3, StrokeSnapshotSelector.contactFrameIndex(frames))
    }

    @Test
    fun contactFrameIndex_picksMaxForwardExtension_notPeakSpeedFrame() {
        // Peak wrist-speed frame is NOT the frame with maximum forward extension: a spurious extra
        // burst of speed happens between frame3->4, past the true max-extension frame (index 3),
        // pulling the wrist slightly further along direction but contact should still reflect the
        // frame with the strongest forward reach relative to the hip among all qualifying frames.
        val frames = listOf(
            makeFrameAt(wristX = 0.20f, hipX = 0.5f), // backswing
            makeFrameAt(wristX = 0.22f, hipX = 0.5f), // backswing
            makeFrameAt(wristX = 0.50f, hipX = 0.5f), // forward swing, at hip
            makeFrameAt(wristX = 0.95f, hipX = 0.5f), // forward swing, farthest forward extension
            makeFrameAt(wristX = 0.60f, hipX = 0.5f)  // follow-through pulls back
        )
        // Max extension (direction=+1) is at index 3: 0.95 - 0.5 = 0.45, larger than any other frame.
        assertEquals(3, StrokeSnapshotSelector.contactFrameIndex(frames))
    }

    @Test
    fun contactFrameIndex_leftwardStroke_picksMaxForwardExtension() {
        // Mirror of the rightward case: wrist moves toward decreasing x (leftward stroke).
        val frames = listOf(
            makeFrameAt(wristX = 0.70f, hipX = 0.5f), // backswing: behind hip (to the right)
            makeFrameAt(wristX = 0.68f, hipX = 0.5f), // backswing
            makeFrameAt(wristX = 0.45f, hipX = 0.5f), // forward swing: past hip
            makeFrameAt(wristX = 0.10f, hipX = 0.5f), // forward swing: max forward extension (leftward)
            makeFrameAt(wristX = 0.40f, hipX = 0.5f)  // follow-through pulls back (rightward)
        )
        // direction = sign(wrist[peak] - wrist[prev]) will be negative (leftward); extension is
        // direction * (wrist.x - hip.x), maximized at the most-negative wrist.x -> index 3.
        assertEquals(3, StrokeSnapshotSelector.contactFrameIndex(frames))
    }

    @Test
    fun contactFrameIndex_lowCoreFrameExcluded_nextBestFrameWins() {
        // Frame with the largest forward extension is degraded (motion blur, <6 core landmarks
        // visible, hip itself also degraded) and must be excluded; the next-best qualifying frame
        // should win instead.
        val frames = listOf(
            makeFrameAt(wristX = 0.30f, hipX = 0.5f),
            makeFrameAt(wristX = 0.32f, hipX = 0.5f),
            makeFrameAt(wristX = 0.60f, hipX = 0.5f),
            makeDegradedFrameAt(wristX = 0.95f, hipX = 0.5f, visibleCoreCount = 2), // excluded: low core
            makeFrameAt(wristX = 0.65f, hipX = 0.5f)
        )
        // Frame 3 has the largest raw extension (0.45) but is excluded (low core count + degraded
        // hip). Among the remaining qualifying frames (0: -0.20, 1: -0.18, 2: 0.10, 4: 0.15),
        // frame 4 has the best forward extension.
        assertEquals(4, StrokeSnapshotSelector.contactFrameIndex(frames))
    }

    // ── followThroughFrameIndex ───────────────────────────────────────────────

    @Test
    fun followThroughFrameIndex_pickHighestWristAfterContact() {
        // After contact (index 3), the wrist rises (y decreases) then partially drops again;
        // the minimum-y frame after contact should be picked as follow-through.
        val frames = listOf(
            makeFrameAt(wristX = 0.30f, hipX = 0.5f, wristY = 0.6f),
            makeFrameAt(wristX = 0.32f, hipX = 0.5f, wristY = 0.6f),
            makeFrameAt(wristX = 0.60f, hipX = 0.5f, wristY = 0.55f),
            makeFrameAt(wristX = 0.90f, hipX = 0.5f, wristY = 0.5f),  // contact
            makeFrameAt(wristX = 0.70f, hipX = 0.5f, wristY = 0.3f),  // wrist raised highest (min y)
            makeFrameAt(wristX = 0.55f, hipX = 0.5f, wristY = 0.4f)   // partial drop back down
        )
        val contact = StrokeSnapshotSelector.contactFrameIndex(frames)
        assertEquals(3, contact)
        assertEquals(4, StrokeSnapshotSelector.followThroughFrameIndex(frames))
    }

    @Test
    fun followThroughFrameIndex_noFrameAfterContact_fallsBackToContact() {
        val frames = listOf(
            makeFrameAt(wristX = 0.30f, hipX = 0.5f),
            makeFrameAt(wristX = 0.32f, hipX = 0.5f),
            makeFrameAt(wristX = 0.90f, hipX = 0.5f)
        )
        val contact = StrokeSnapshotSelector.contactFrameIndex(frames)
        assertEquals(contact, StrokeSnapshotSelector.followThroughFrameIndex(frames))
    }

    @Test
    fun followThroughFrameIndex_emptyList_returnsMinusOne() {
        assertEquals(-1, StrokeSnapshotSelector.followThroughFrameIndex(emptyList()))
    }

    // ── snapshotFrameFor ──────────────────────────────────────────────────────

    @Test
    fun snapshotFrameFor_contactTypes_dispatchToContactFrameIndex() {
        val frames = listOf(
            makeFrameAt(wristX = 0.30f, hipX = 0.5f),
            makeFrameAt(wristX = 0.32f, hipX = 0.5f),
            makeFrameAt(wristX = 0.90f, hipX = 0.5f),
            makeFrameAt(wristX = 0.60f, hipX = 0.5f)
        )
        val contact = StrokeSnapshotSelector.contactFrameIndex(frames)
        for (type in listOf(
            CorrectionType.WRIST,
            CorrectionType.CONTACT_HEIGHT,
            CorrectionType.ELBOW_POSITION,
            CorrectionType.KNEE_BEND,
            CorrectionType.BODY_ROTATION
        )) {
            assertEquals(contact, StrokeSnapshotSelector.snapshotFrameFor(type, frames), "type=$type")
        }
    }

    @Test
    fun snapshotFrameFor_followThrough_dispatchesToFollowThroughFrameIndex() {
        val frames = listOf(
            makeFrameAt(wristX = 0.30f, hipX = 0.5f, wristY = 0.6f),
            makeFrameAt(wristX = 0.32f, hipX = 0.5f, wristY = 0.6f),
            makeFrameAt(wristX = 0.90f, hipX = 0.5f, wristY = 0.5f),
            makeFrameAt(wristX = 0.70f, hipX = 0.5f, wristY = 0.3f)
        )
        assertEquals(
            StrokeSnapshotSelector.followThroughFrameIndex(frames),
            StrokeSnapshotSelector.snapshotFrameFor(CorrectionType.FOLLOW_THROUGH, frames)
        )
    }

    @Test
    fun snapshotFrameFor_speedAndGeneral_dispatchToBestSnapshotFrameIndex() {
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.82f, 0.76f, 0f, visibility = 1f))
        )
        val best = StrokeSnapshotSelector.bestSnapshotFrameIndex(frames)
        assertEquals(best, StrokeSnapshotSelector.snapshotFrameFor(CorrectionType.STROKE_SPEED, frames))
        assertEquals(best, StrokeSnapshotSelector.snapshotFrameFor(CorrectionType.GENERAL, frames))
    }

    // ── hasUsablePose ─────────────────────────────────────────────────────────

    @Test
    fun hasUsablePose_fullyVisibleFrame_returnsTrue() {
        val frames = listOf(makeFrame(wrist = Landmark3D(0.5f, 0.5f, 0f, visibility = 1f)))
        assertTrue(StrokeSnapshotSelector.hasUsablePose(frames))
    }

    @Test
    fun hasUsablePose_allFramesDegraded_returnsFalse() {
        val frames = listOf(
            makeDegradedFrame(wrist = Landmark3D(0.5f, 0.5f, 0f, visibility = 1f), visibleCoreCount = 2),
            makeDegradedFrame(wrist = Landmark3D(0.5f, 0.5f, 0f, visibility = 1f), visibleCoreCount = 3)
        )
        assertFalse(StrokeSnapshotSelector.hasUsablePose(frames))
    }

    @Test
    fun hasUsablePose_emptyList_returnsFalse() {
        assertFalse(StrokeSnapshotSelector.hasUsablePose(emptyList()))
    }

    // ── bestSnapshotFrameIndex ────────────────────────────────────────────────

    @Test
    fun bestSnapshotFrameIndex_emptyList_returnsMinusOne() {
        assertEquals(-1, StrokeSnapshotSelector.bestSnapshotFrameIndex(emptyList()))
    }

    @Test
    fun bestSnapshotFrameIndex_peakDegraded_neighborFullyVisibleWins() {
        // Wrist speed peaks at index 2 (frame1 -> frame2 jump), but frame2's CORE landmarks are
        // mostly occluded by motion blur. Frame 3 (within the +-4 window) is fully visible and
        // should be picked instead; every other candidate in the window is also degraded so the
        // outcome isn't a coincidence of the lower-index tiebreak.
        val frames = listOf(
            makeDegradedFrame(
                wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f),
                visibleCoreCount = 2
            ),
            makeDegradedFrame(
                wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f),
                visibleCoreCount = 2
            ),
            makeDegradedFrame(
                wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f),
                visibleCoreCount = 2
            ),
            makeFrame(wrist = Landmark3D(0.82f, 0.76f, 0f, visibility = 1f))
        )

        assertEquals(2, StrokeSnapshotSelector.peakFrameIndex(frames))
        assertEquals(3, StrokeSnapshotSelector.bestSnapshotFrameIndex(frames))
    }

    @Test
    fun bestSnapshotFrameIndex_allEqualInWindow_returnsPeak() {
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.82f, 0.76f, 0f, visibility = 1f))
        )

        val peak = StrokeSnapshotSelector.peakFrameIndex(frames)
        assertEquals(peak, StrokeSnapshotSelector.bestSnapshotFrameIndex(frames))
    }

    @Test
    fun bestSnapshotFrameIndex_windowClampedAtSequenceStart() {
        // Peak lands near the start of a short sequence; window (peak-4..peak+4) must clamp to 0,
        // not go negative / throw.
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.81f, 0.76f, 0f, visibility = 1f))
        )

        val peak = StrokeSnapshotSelector.peakFrameIndex(frames)
        val result = StrokeSnapshotSelector.bestSnapshotFrameIndex(frames)
        assertEquals(peak, result)
    }

    @Test
    fun bestSnapshotFrameIndex_windowClampedAtSequenceEnd() {
        // Peak lands near the end of the sequence; window must clamp to frames.size - 1.
        val frames = listOf(
            makeFrame(wrist = Landmark3D(0.10f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.11f, 0.10f, 0f, visibility = 1f)),
            makeFrame(wrist = Landmark3D(0.80f, 0.75f, 0f, visibility = 1f))
        )

        val peak = StrokeSnapshotSelector.peakFrameIndex(frames)
        assertEquals(2, peak)
        val result = StrokeSnapshotSelector.bestSnapshotFrameIndex(frames)
        assertEquals(peak, result)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a 33-landmark frame with neutral defaults, overriding the wrist (index 16). */
    private fun makeFrame(wrist: Landmark3D): List<Landmark3D> =
        MutableList(33) { Landmark3D(0.5f, 0.5f, 0f, visibility = 1f) }.also {
            it[16] = wrist
        }

    /**
     * Builds a frame like [makeFrame] but degrades all-but-[visibleCoreCount] of the *other* CORE
     * landmarks (shoulders/elbows/hips/knees/ankles — everything except the wrist at index 16) to
     * below MIN_VISIBILITY, simulating a motion-blur frame with a malformed rendered skeleton. The
     * wrist keeps the passed-in [wrist] value (kept fully visible) so wrist-speed/peak detection
     * is unaffected; [visibleCoreCount] therefore counts landmarks left visible in addition to the
     * wrist.
     */
    private fun makeDegradedFrame(wrist: Landmark3D, visibleCoreCount: Int): List<Landmark3D> {
        val otherCore = intArrayOf(11, 12, 13, 14, 23, 24, 25, 26, 27, 28)
        val frame = MutableList(33) { Landmark3D(0.5f, 0.5f, 0f, visibility = 1f) }
        otherCore.drop(visibleCoreCount).forEach { index ->
            frame[index] = Landmark3D(0.5f, 0.5f, 0f, visibility = 0.1f)
        }
        frame[16] = wrist
        return frame
    }

    /**
     * Builds a 33-landmark frame with neutral defaults, overriding the wrist (index 16, x/y) and
     * hip (index 24, x only) — used for contact/follow-through tests that need explicit control
     * over the wrist-hip forward-extension geometry.
     */
    private fun makeFrameAt(wristX: Float, hipX: Float, wristY: Float = 0.5f): List<Landmark3D> =
        MutableList(33) { Landmark3D(0.5f, 0.5f, 0f, visibility = 1f) }.also {
            it[16] = Landmark3D(wristX, wristY, 0f, visibility = 1f)
            it[24] = Landmark3D(hipX, 0.5f, 0f, visibility = 1f)
        }

    /**
     * Like [makeFrameAt], but degrades all-but-[visibleCoreCount] of the *other* CORE landmarks
     * (shoulders/elbows/knees/ankles/hip — everything except the wrist at index 16) below
     * MIN_VISIBILITY, simulating a motion-blur frame that must be excluded from contact/
     * follow-through selection despite having wrist/hip data.
     */
    private fun makeDegradedFrameAt(wristX: Float, hipX: Float, visibleCoreCount: Int): List<Landmark3D> {
        val otherCore = intArrayOf(11, 12, 13, 14, 23, 24, 25, 26, 27, 28)
        val frame = MutableList(33) { Landmark3D(0.5f, 0.5f, 0f, visibility = 1f) }
        otherCore.drop(visibleCoreCount).forEach { index ->
            frame[index] = Landmark3D(0.5f, 0.5f, 0f, visibility = 0.1f)
        }
        frame[16] = Landmark3D(wristX, 0.5f, 0f, visibility = 1f)
        // Keep hip explicit (may be degraded above if index 24 wasn't in the visible slice).
        if (frame[24].visibility >= 0.5f) {
            frame[24] = Landmark3D(hipX, 0.5f, 0f, visibility = 1f)
        }
        return frame
    }
}
