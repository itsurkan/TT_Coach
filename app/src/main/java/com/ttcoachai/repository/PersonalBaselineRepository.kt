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
        // Write path stays keyed by the literal drillType the caller calibrated against —
        // never remapped. Only read lookups below share baselines across drillTypes.
        return dao.archiveAndInsert(baseline.drillType, entity)
    }

    fun getActiveBaseline(drillType: String): Flow<PersonalBaseline?> =
        dao.getActiveByDrillType(baselineDrillType(drillType)).map { it?.toDomainBaseline() }

    suspend fun getBaselineHistory(drillType: String): List<PersonalBaseline> =
        dao.getAllForDrillType(baselineDrillType(drillType)).map { it.toDomainBaseline() }

    private fun PersonalBaselineEntity.toDomainBaseline(): PersonalBaseline = toDomain(
        metricStats = BaselineConverters.jsonToMetricStatsMap(metricStatsJson),
        phaseDurations = BaselineConverters.jsonToMetricStatsMap(phaseDurationsJson),
        excludedRepIndices = BaselineConverters.jsonToIntList(excludedRepIndicesJson)
    )

    companion object {
        /**
         * Maps a drill's baseline lookup key onto the drillType its [PersonalBaseline] is
         * actually stored under. "forehand_drive_general" (the movement-tolerant general-
         * practice profile, see [com.ttcoachai.shared.drill.movements.ForehandDriveGeneral])
         * shares the player's forehand technique baseline with "forehand_drive" — only rep
         * gating (locomotion tolerance) differs between the two, not the reference angles, so
         * there is no separate calibration pass for the General profile. Identity for every
         * other drillType. Read-only: calibration/save paths must keep using the literal
         * drillType, never this mapping.
         */
        fun baselineDrillType(drillType: String): String = when (drillType) {
            "forehand_drive_general" -> "forehand_drive"
            else -> drillType
        }
    }
}
