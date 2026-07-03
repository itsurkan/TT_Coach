package com.ttcoachai.repository

import com.ttcoachai.db.BaselineConverters
import com.ttcoachai.db.PersonalBaselineDao
import com.ttcoachai.models.PersonalBaselineEntity
import com.ttcoachai.shared.models.PersonalBaseline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local-only (Room) repository for PersonalBaseline.
 *
 * Stage 1 has no cloud sync — the repository mirrors the shape of
 * [TrainingRepository] (domain↔entity mapping + Flow-based queries) but
 * drops the Firestore branch entirely. Writes go through `archiveAndInsert`
 * so the invariant "≤ 1 active row per drill type" is preserved from day one;
 * this keeps recalibration (US2) a pure UI change.
 */
class PersonalBaselineRepository(
    private val dao: PersonalBaselineDao
) {
    suspend fun saveBaseline(baseline: PersonalBaseline): Long {
        val entity = PersonalBaselineEntity.fromDomain(
            baseline = baseline,
            metricStatsJson = BaselineConverters.metricStatsMapToJson(baseline.metricStats),
            phaseDurationsJson = BaselineConverters.metricStatsMapToJson(baseline.phaseDurationsMs),
            excludedRepIndicesJson = BaselineConverters.intListToJson(baseline.excludedRepIndices),
            isActive = true
        )
        return dao.archiveAndInsert(baseline.drillType, entity)
    }

    fun getActiveBaseline(drillType: String): Flow<PersonalBaseline?> =
        dao.getActiveByDrillType(drillType).map { it?.toDomainBaseline() }

    suspend fun getBaselineHistory(drillType: String): List<PersonalBaseline> =
        dao.getAllForDrillType(drillType).map { it.toDomainBaseline() }

    private fun PersonalBaselineEntity.toDomainBaseline(): PersonalBaseline = toDomain(
        metricStats = BaselineConverters.jsonToMetricStatsMap(metricStatsJson),
        phaseDurations = BaselineConverters.jsonToMetricStatsMap(phaseDurationsJson),
        excludedRepIndices = BaselineConverters.jsonToIntList(excludedRepIndicesJson)
    )
}
