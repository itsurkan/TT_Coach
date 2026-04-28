package com.ttcoachai.repository

import com.ttcoachai.db.DrillConfigDao
import com.ttcoachai.debug.PoseTransformer
import com.ttcoachai.models.DrillConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local-only repository for per-drill shape overrides authored in the Phase 7
 * parameter editor. Mirrors [PersonalBaselineRepository]'s shape.
 */
class DrillConfigRepository(private val dao: DrillConfigDao) {

    suspend fun load(drillType: String): PoseTransformer.EditableParams {
        val entity = dao.getByDrillType(drillType) ?: return PoseTransformer.EditableParams()
        return entity.toParams()
    }

    fun observe(drillType: String): Flow<PoseTransformer.EditableParams> =
        dao.observeByDrillType(drillType).map { it?.toParams() ?: PoseTransformer.EditableParams() }

    suspend fun save(drillType: String, params: PoseTransformer.EditableParams) {
        dao.upsert(
            DrillConfigEntity(
                drillType = drillType,
                bodyRotationDeltaDeg = params.bodyRotationDeltaDeg,
                torsoTiltDeltaDeg = params.torsoTiltDeltaDeg,
                rightShoulderAngleDeltaDeg = params.rightShoulderAngleDeltaDeg,
                rightElbowAngleDeltaDeg = params.rightElbowAngleDeltaDeg,
                rightElbowXOffset = params.rightElbowXOffset,
                rightWristAngleDeltaDeg = params.rightWristAngleDeltaDeg,
                kneeBendDeltaDeg = params.kneeBendDeltaDeg,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun clear(drillType: String) = dao.clearForDrill(drillType)

    private fun DrillConfigEntity.toParams(): PoseTransformer.EditableParams =
        PoseTransformer.EditableParams(
            bodyRotationDeltaDeg = bodyRotationDeltaDeg,
            torsoTiltDeltaDeg = torsoTiltDeltaDeg,
            rightShoulderAngleDeltaDeg = rightShoulderAngleDeltaDeg,
            rightElbowAngleDeltaDeg = rightElbowAngleDeltaDeg,
            rightElbowXOffset = rightElbowXOffset,
            rightWristAngleDeltaDeg = rightWristAngleDeltaDeg,
            kneeBendDeltaDeg = kneeBendDeltaDeg
        )
}
