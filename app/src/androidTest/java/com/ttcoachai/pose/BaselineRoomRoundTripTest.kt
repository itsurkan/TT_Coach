package com.ttcoachai.pose

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.repository.PersonalBaselineRepository
import com.ttcoachai.shared.drill.CalibrationOutcome
import com.ttcoachai.shared.drill.DrillCalibrator
import com.ttcoachai.shared.drill.DrillMetrics
import com.ttcoachai.shared.io.PoseJsonV2Parser
import com.ttcoachai.shared.models.Handedness
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Instrumented Room round-trip for a REAL 2D baseline (Phase 3, task B1).
 *
 * Proves the full chain end-to-end on real Android SQLite:
 * bundled schema-v2 fixture -> [PoseJsonV2Parser] -> [DrillCalibrator.calibrateChecked]
 * -> [PersonalBaselineRepository.saveBaseline] -> read back through the Flow query ->
 * the 2D metric keys (elbow/shoulder/knee/torso-lean/shoulder-tilt) survived the
 * `BaselineConverters` org.json round-trip (Part 3 decision: 2D baselines persist
 * through the same entity/converters as the legacy 3D ones).
 *
 * The fixture (`andrii_1_rtm.json`, bundled as an androidTest asset) is the same
 * full-fps recording already proven in shared/jvmTest to yield 15 forward reps —
 * comfortably above `minRepCount = 3` used here.
 */
@RunWith(AndroidJUnit4::class)
class BaselineRoomRoundTripTest {

    private val drillType = "forehand_drive_rtm"

    @Test
    fun calibrate_from_bundled_fixture_and_round_trip_through_room() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val json = context.assets.open("andrii_1_rtm.json").use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }
        val sequence = PoseJsonV2Parser.parse(json)

        val outcome = DrillCalibrator.calibrateChecked(
            sequence = sequence,
            drillType = drillType,
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3,
            cameraYawDeg = 0f
        )
        assertTrue(
            "expected CalibrationOutcome.Success but got $outcome",
            outcome is CalibrationOutcome.Success
        )
        val baseline = (outcome as CalibrationOutcome.Success).baseline

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = PersonalBaselineRepository(db.personalBaselineDao())
            repository.saveBaseline(baseline)

            val stored = repository.getActiveBaseline(drillType).first()

            assertNotNull(stored)
            assertEquals(drillType, stored!!.drillType)
            assertTrue(
                "metricStats keys must be non-empty and a subset of DrillMetrics.ALL_KEYS, got ${stored.metricStats.keys}",
                stored.metricStats.isNotEmpty() && DrillMetrics.ALL_KEYS.containsAll(stored.metricStats.keys)
            )
        } finally {
            db.close()
        }
    }
}
