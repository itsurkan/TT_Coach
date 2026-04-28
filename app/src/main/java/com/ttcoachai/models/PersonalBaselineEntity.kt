package com.ttcoachai.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ttcoachai.shared.models.MetricStats
import com.ttcoachai.shared.models.PersonalBaseline

/**
 * Room persistence for [PersonalBaseline].
 *
 * `metricStatsJson`, `phaseDurationsJson`, and `excludedRepIndicesJson` are
 * serialized via [com.ttcoachai.db.BaselineConverters]. Active/archived versioning
 * is represented by [isActive]; the DAO keeps at most one active row per `drillType`.
 */
@Entity(tableName = "personal_baselines")
data class PersonalBaselineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drillType: String,
    val createdAtMs: Long,
    val repCount: Int,
    val qualityScore: Double,
    val handedness: String?,
    val metricStatsJson: String,
    val phaseDurationsJson: String,
    val excludedRepIndicesJson: String,
    val isActive: Boolean
) {
    fun toDomain(
        metricStats: Map<String, MetricStats>,
        phaseDurations: Map<String, MetricStats>,
        excludedRepIndices: List<Int>
    ): PersonalBaseline = PersonalBaseline(
        drillType = drillType,
        metricStats = metricStats,
        phaseDurationsMs = phaseDurations,
        repCount = repCount,
        excludedRepIndices = excludedRepIndices,
        qualityScore = qualityScore,
        createdAtMs = createdAtMs,
        drillerHandedness = handedness
    )

    companion object {
        fun fromDomain(
            baseline: PersonalBaseline,
            metricStatsJson: String,
            phaseDurationsJson: String,
            excludedRepIndicesJson: String,
            isActive: Boolean = true
        ): PersonalBaselineEntity = PersonalBaselineEntity(
            drillType = baseline.drillType,
            createdAtMs = baseline.createdAtMs,
            repCount = baseline.repCount,
            qualityScore = baseline.qualityScore,
            handedness = baseline.drillerHandedness,
            metricStatsJson = metricStatsJson,
            phaseDurationsJson = phaseDurationsJson,
            excludedRepIndicesJson = excludedRepIndicesJson,
            isActive = isActive
        )
    }
}
