package com.ttcoachai.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.db.PersonalBaselineDao
import com.ttcoachai.shared.analysis.BaselineDeriver
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.DetectedStroke
import com.ttcoachai.shared.models.StrokePhase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Persistence round-trip test salvaged from the deleted `CalibrationFlowTest`
 * (removed in the MediaPipe-legacy cleanup because it shared a file with a
 * `CalibrationActivity` UI smoke test). This specific test covers
 * `BaselineDeriver` + `PersonalBaselineRepository` + Room, none of which are
 * MediaPipe-related or deleted, so it was extracted here to preserve coverage.
 */
@RunWith(AndroidJUnit4::class)
class PersonalBaselineRoundTripTest {

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
}
