package com.ttcoachai.util

import com.ttcoachai.models.CommunityDrill
import com.ttcoachai.models.CommunityDrillMapper
import com.ttcoachai.models.CustomDrillEntity
import com.ttcoachai.repository.CustomDrillRepository

object CommunityDrillCopier {
    /**
     * Copies a community drill into the user's local drills as a fresh, independent drill.
     * New id is "custom_<nowMs>" (the CUSTOM_ prefix DrillsFragment/DrillActions recognize).
     * The copy is unlinked from its source (sharedCommunityId=null) and has no baseline.
     * Returns the saved entity.
     */
    suspend fun copyToLocal(
        drill: CommunityDrill,
        customDrillRepo: CustomDrillRepository,
        nowMs: Long
    ): CustomDrillEntity {
        val drillType = "custom_$nowMs"
        val entity = CommunityDrillMapper.toCustomDrillEntity(drill, drillType, nowMs)
        customDrillRepo.save(entity)
        return entity
    }
}
