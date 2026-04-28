package com.ttcoachai.calibration

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ttcoachai.R
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.db.BaselineConverters
import com.ttcoachai.db.PersonalBaselineDao
import com.ttcoachai.models.PersonalBaselineEntity
import com.ttcoachai.repository.PersonalBaselineRepository
import com.ttcoachai.shared.analysis.BaselineDeriver
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.DetectedStroke
import com.ttcoachai.shared.models.MetricStats
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.StrokePhase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented end-to-end coverage for Phase 1 of Stage 1.
 *
 * Two layers verified here:
 * 1. UI smoke test — launching `CalibrationActivity` renders the onboarding
 *    card with the Start Capture CTA (proves manifest + layout + fragment
 *    inflation + CameraFragment hosting don't crash on a real device).
 * 2. Persistence round-trip on a real Android SQLite instance — derive a
 *    baseline from synthetic pipeline output (mirroring what
 *    `PoseAnalysisProcessor` would produce in calibration mode), persist it
 *    via the repository, read it back through the Flow query, and assert
 *    field-level equality on the deserialized entity.
 *
 * Driving the full UI loop requires synthetic pose frames streamed into
 * CameraFragment's MediaPipe pipeline, which is out of scope for this phase
 * (see plan.md §Phase 3 — dev debug screen + ADB dump cover manual validation).
 */
@RunWith(AndroidJUnit4::class)
class CalibrationFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CalibrationActivity::class.java)

    @Test
    fun activity_launches_onboarding_card_with_start_cta() {
        onView(withText(R.string.calibration_onboarding_headline))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btn_start_capture))
            .check(matches(isDisplayed()))
    }

    @Test
    fun derive_and_persist_round_trips_through_room() = runBlocking {
        val (db, dao) = openInMemoryDb()
        try {
            val repository = PersonalBaselineRepository(dao)
            val strokes = List(12) { syntheticStroke(it) }
            val analyses = List(12) { syntheticAnalysis(it) }

            val baseline = BaselineDeriver.derive(
                strokes = strokes,
                analyses = analyses,
                frameIntervalMs = 33L,
                drillType = "forehand_shadow",
                createdAtMs = 1_700_000_000_000L,
                minRepCount = 5
            )

            repository.saveBaseline(baseline)
            val stored = repository.getActiveBaseline("forehand_shadow").first()

            assertNotNull(stored)
            assertEquals(baseline.drillType, stored!!.drillType)
            assertEquals(baseline.repCount, stored.repCount)
            assertEquals(baseline.qualityScore, stored.qualityScore, 1e-9)
            assertEquals(baseline.metricStats, stored.metricStats)
            assertEquals(baseline.phaseDurationsMs, stored.phaseDurationsMs)
        } finally {
            db.close()
        }
    }

    private fun openInMemoryDb(): Pair<AppDatabase, PersonalBaselineDao> {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return db to db.personalBaselineDao()
    }

    private fun syntheticStroke(index: Int): DetectedStroke = DetectedStroke(
        strokeIndex = index,
        preparationStartFrame = index * 30,
        preparationEndFrame = index * 30 + 8,
        forwardStartFrame = index * 30 + 8,
        contactFrame = index * 30 + 12,
        forwardEndFrame = index * 30 + 16,
        returnStartFrame = index * 30 + 16,
        returnEndFrame = index * 30 + 24,
        backswingMinValue = 0f,
        forwardPeakValue = 0f,
        peakVelocity = 0f,
        strokeDurationMs = 800L,
        forwardSwingDurationMs = 260L,
        isComplete = true
    )

    private fun syntheticAnalysis(index: Int): AnalysisResult {
        val jitter = (index % 3 - 1).toFloat()
        return AnalysisResult(
            overallScore = 80f,
            phase = StrokePhase.CONTACT,
            wristAngle = 110f + jitter,
            bodyRotation = 38f + jitter * 0.5f,
            followThroughAngle = 120f + jitter,
            contactHeight = 0.9f + jitter * 0.02f,
            elbowBodyDistance = 0.35f + jitter * 0.01f
        )
    }

    // Silences unused-import inspections for helpers the suite may grow into.
    @Suppress("unused")
    private fun unused(b: PersonalBaseline, c: MetricStats, e: PersonalBaselineEntity, k: BaselineConverters) = Unit
}
