package com.ttcoachai.models

/**
 * Pure translation between the local Room [CustomDrillEntity] and the cloud-facing
 * [CommunityDrill]. Deliberately drops local-only fields (`drillType`, `baselineId`) so
 * they never travel to the public Firestore collection. No Firebase, no Android.
 */
object CommunityDrillMapper {

    fun fromCustomDrill(
        entity: CustomDrillEntity,
        creatorUid: String,
        creatorName: String,
        creatorPhotoUrl: String,
        nowMs: Long,
    ): CommunityDrill = CommunityDrill(
        id = "",
        name = entity.name,
        baseTemplate = entity.baseTemplate,
        focusCsv = entity.focusCsv,
        referenceType = entity.referenceType,
        strictnessX = entity.strictnessX,
        perPhaseTargetsJson = entity.perPhaseTargetsJson,
        creatorUid = creatorUid,
        creatorName = creatorName,
        creatorPhotoUrl = creatorPhotoUrl,
        sharedAtMs = nowMs,
        ratingSum = 0L,
        ratingCount = 0L,
    )

    fun toCustomDrillEntity(
        drill: CommunityDrill,
        newDrillType: String,
        nowMs: Long,
    ): CustomDrillEntity = CustomDrillEntity(
        drillType = newDrillType,
        name = drill.name,
        baseTemplate = drill.baseTemplate,
        createdAtMs = nowMs,
        focusCsv = drill.focusCsv,
        referenceType = drill.referenceType,
        baselineId = null,
        strictnessX = drill.strictnessX,
        perPhaseTargetsJson = drill.perPhaseTargetsJson,
        sharedCommunityId = null,
    )

    fun toMap(drill: CommunityDrill): Map<String, Any> = mapOf(
        "name" to drill.name,
        "baseTemplate" to drill.baseTemplate,
        "focusCsv" to drill.focusCsv,
        "referenceType" to drill.referenceType,
        "strictnessX" to drill.strictnessX,
        "perPhaseTargetsJson" to drill.perPhaseTargetsJson,
        "creatorUid" to drill.creatorUid,
        "creatorName" to drill.creatorName,
        "creatorPhotoUrl" to drill.creatorPhotoUrl,
        "sharedAtMs" to drill.sharedAtMs,
        "ratingSum" to drill.ratingSum,
        "ratingCount" to drill.ratingCount,
    )
}
