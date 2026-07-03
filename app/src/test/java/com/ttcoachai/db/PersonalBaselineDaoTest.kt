package com.ttcoachai.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ttcoachai.models.PersonalBaselineEntity
import com.ttcoachai.shared.models.MetricStats
import com.ttcoachai.shared.models.PersonalBaseline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class PersonalBaselineDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PersonalBaselineDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.personalBaselineDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_and_getActive_returns_deserialized_baseline() = runBlocking {
        val baseline = sampleBaseline(drillType = "forehand_shadow")
        dao.insert(entityFrom(baseline))

        val stored = dao.getActiveByDrillType("forehand_shadow").first()
        assertNotNull(stored)
        val roundTripped = stored!!.toDomain(
            metricStats = BaselineConverters.jsonToMetricStatsMap(stored.metricStatsJson),
            phaseDurations = BaselineConverters.jsonToMetricStatsMap(stored.phaseDurationsJson),
            excludedRepIndices = BaselineConverters.jsonToIntList(stored.excludedRepIndicesJson)
        )

        assertEquals(baseline.drillType, roundTripped.drillType)
        assertEquals(baseline.repCount, roundTripped.repCount)
        assertEquals(baseline.qualityScore, roundTripped.qualityScore, 1e-9)
        assertEquals(baseline.metricStats, roundTripped.metricStats)
        assertEquals(baseline.phaseDurationsMs, roundTripped.phaseDurationsMs)
        assertEquals(baseline.excludedRepIndices, roundTripped.excludedRepIndices)
    }

    @Test
    fun getActive_returns_null_when_none_inserted() = runBlocking {
        val stored = dao.getActiveByDrillType("backhand_shadow").first()
        assertNull(stored)
    }

    @Test
    fun archiveAndInsert_keeps_exactly_one_active_row() = runBlocking {
        val v1 = sampleBaseline(drillType = "forehand_shadow", createdAtMs = 1_000L)
        val v2 = sampleBaseline(drillType = "forehand_shadow", createdAtMs = 2_000L)

        dao.archiveAndInsert("forehand_shadow", entityFrom(v1))
        dao.archiveAndInsert("forehand_shadow", entityFrom(v2))

        val all = dao.getAllForDrillType("forehand_shadow")
        assertEquals(2, all.size)
        assertEquals(1, all.count { it.isActive })

        val active = dao.getActiveByDrillType("forehand_shadow").first()
        assertNotNull(active)
        assertEquals(2_000L, active!!.createdAtMs)

        val archived = all.single { !it.isActive }
        assertEquals(1_000L, archived.createdAtMs)
    }

    @Test
    fun archiveAndInsert_does_not_affect_other_drill_types() = runBlocking {
        dao.archiveAndInsert(
            "forehand_shadow",
            entityFrom(sampleBaseline(drillType = "forehand_shadow"))
        )
        dao.archiveAndInsert(
            "backhand_shadow",
            entityFrom(sampleBaseline(drillType = "backhand_shadow"))
        )

        assertTrue(dao.getAllForDrillType("forehand_shadow").all { it.isActive })
        assertTrue(dao.getAllForDrillType("backhand_shadow").all { it.isActive })
    }

    private fun entityFrom(baseline: PersonalBaseline): PersonalBaselineEntity =
        PersonalBaselineEntity.fromDomain(
            baseline = baseline,
            metricStatsJson = BaselineConverters.metricStatsMapToJson(baseline.metricStats),
            phaseDurationsJson = BaselineConverters.metricStatsMapToJson(baseline.phaseDurationsMs),
            excludedRepIndicesJson = BaselineConverters.intListToJson(baseline.excludedRepIndices),
            isActive = true
        )

    private fun sampleBaseline(
        drillType: String,
        createdAtMs: Long = 1_700_000_000_000L
    ): PersonalBaseline = PersonalBaseline(
        drillType = drillType,
        metricStats = mapOf(
            "wrist_angle" to MetricStats(mean = 110.0, std = 4.5, min = 102.0, max = 118.0, sampleCount = 14),
            "body_rotation" to MetricStats(mean = 38.0, std = 2.1, min = 34.0, max = 42.0, sampleCount = 14)
        ),
        phaseDurationsMs = mapOf(
            "backswing_ms" to MetricStats(mean = 240.0, std = 12.0, min = 220.0, max = 260.0, sampleCount = 14),
            "forward_swing_ms" to MetricStats(mean = 180.0, std = 8.0, min = 170.0, max = 195.0, sampleCount = 14)
        ),
        repCount = 14,
        excludedRepIndices = listOf(3, 9),
        qualityScore = 0.82,
        createdAtMs = createdAtMs,
        drillerHandedness = "right"
    )
}
