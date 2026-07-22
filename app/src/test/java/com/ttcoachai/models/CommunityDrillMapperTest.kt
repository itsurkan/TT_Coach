package com.ttcoachai.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [CommunityDrillMapper] — no Firebase, no Android.
 */
class CommunityDrillMapperTest {

    private fun sampleEntity() = CustomDrillEntity(
        drillType = "custom_1111",
        name = "Forehand Drive",
        baseTemplate = "forehand_drive",
        createdAtMs = 1_000L,
        focusCsv = "elbow,knee",
        referenceType = "standard",
        baselineId = 42L,
        strictnessX = 1.2f,
        perPhaseTargetsJson = """{"knees · strike":[110,130]}""",
        sharedCommunityId = null,
    )

    @Test
    fun fromCustomDrillCopiesSharedFieldsVerbatimAndSetsCreatorAndZeroAggregates() {
        val entity = sampleEntity()

        val drill = CommunityDrillMapper.fromCustomDrill(
            entity = entity,
            creatorUid = "uid-1",
            creatorName = "Ivan",
            creatorPhotoUrl = "https://example.com/photo.jpg",
            nowMs = 5_000L,
        )

        assertEquals(entity.name, drill.name)
        assertEquals(entity.baseTemplate, drill.baseTemplate)
        assertEquals(entity.focusCsv, drill.focusCsv)
        assertEquals(entity.referenceType, drill.referenceType)
        assertEquals(entity.strictnessX, drill.strictnessX, 0f)
        assertEquals(entity.perPhaseTargetsJson, drill.perPhaseTargetsJson)

        assertEquals("uid-1", drill.creatorUid)
        assertEquals("Ivan", drill.creatorName)
        assertEquals("https://example.com/photo.jpg", drill.creatorPhotoUrl)
        assertEquals(5_000L, drill.sharedAtMs)

        assertEquals(0L, drill.ratingSum)
        assertEquals(0L, drill.ratingCount)
        assertEquals("", drill.id)
    }

    @Test
    fun toMapDoesNotLeakLocalOnlyFieldsAndContainsExactlyTwelveKeys() {
        val entity = sampleEntity()
        val drill = CommunityDrillMapper.fromCustomDrill(
            entity = entity,
            creatorUid = "uid-1",
            creatorName = "Ivan",
            creatorPhotoUrl = "",
            nowMs = 5_000L,
        )

        val map = CommunityDrillMapper.toMap(drill)

        assertFalse(map.containsKey("drillType"))
        assertFalse(map.containsKey("baselineId"))
        assertFalse(map.containsKey("id"))
        assertFalse(map.containsKey("averageRating"))

        val expectedKeys = setOf(
            "name", "baseTemplate", "focusCsv", "referenceType", "strictnessX",
            "perPhaseTargetsJson", "creatorUid", "creatorName", "creatorPhotoUrl",
            "sharedAtMs", "ratingSum", "ratingCount",
        )
        assertEquals(expectedKeys, map.keys)
        assertEquals(12, map.size)
    }

    @Test
    fun toCustomDrillEntitySetsBaselineAndSharedIdNullAndUsesSuppliedDrillTypeAndNowMs() {
        val drill = CommunityDrill(
            id = "doc-123",
            name = "Forehand Drive",
            baseTemplate = "forehand_drive",
            focusCsv = "elbow,knee",
            referenceType = "standard",
            strictnessX = 1.2f,
            perPhaseTargetsJson = """{"knees · strike":[110,130]}""",
            creatorUid = "uid-1",
            creatorName = "Ivan",
            creatorPhotoUrl = "",
            sharedAtMs = 5_000L,
            ratingSum = 17L,
            ratingCount = 5L,
        )

        val entity = CommunityDrillMapper.toCustomDrillEntity(
            drill = drill,
            newDrillType = "custom_9999",
            nowMs = 6_000L,
        )

        assertEquals("custom_9999", entity.drillType)
        assertEquals(6_000L, entity.createdAtMs)
        assertNull(entity.baselineId)
        assertNull(entity.sharedCommunityId)

        assertEquals(drill.name, entity.name)
        assertEquals(drill.baseTemplate, entity.baseTemplate)
        assertEquals(drill.focusCsv, entity.focusCsv)
        assertEquals(drill.referenceType, entity.referenceType)
        assertEquals(drill.strictnessX, entity.strictnessX, 0f)
        assertEquals(drill.perPhaseTargetsJson, entity.perPhaseTargetsJson)
    }

    @Test
    fun averageRatingIsZeroWhenCountIsZero() {
        val drill = CommunityDrill(
            name = "Forehand Drive",
            baseTemplate = "forehand_drive",
            focusCsv = "",
            referenceType = "standard",
            strictnessX = 1.0f,
            perPhaseTargetsJson = "",
            creatorUid = "uid-1",
            creatorName = "Ivan",
            creatorPhotoUrl = "",
            sharedAtMs = 0L,
            ratingSum = 0L,
            ratingCount = 0L,
        )

        assertEquals(0f, drill.averageRating, 0f)
    }

    @Test
    fun averageRatingComputesMeanWhenCountIsPositive() {
        val drill = CommunityDrill(
            name = "Forehand Drive",
            baseTemplate = "forehand_drive",
            focusCsv = "",
            referenceType = "standard",
            strictnessX = 1.0f,
            perPhaseTargetsJson = "",
            creatorUid = "uid-1",
            creatorName = "Ivan",
            creatorPhotoUrl = "",
            sharedAtMs = 0L,
            ratingSum = 17L,
            ratingCount = 5L,
        )

        assertEquals(3.4f, drill.averageRating, 0.0001f)
    }

    @Test
    fun collectionConstantIsCommunityDrills() {
        assertTrue(CommunityDrill.COLLECTION == "community_drills")
    }
}
