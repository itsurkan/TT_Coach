package com.ttcoachai.util

import com.ttcoachai.db.CustomDrillDao
import com.ttcoachai.models.CommunityDrill
import com.ttcoachai.models.CustomDrillEntity
import com.ttcoachai.repository.CustomDrillRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CommunityDrillCopierTest {

    private class FakeCustomDrillDao : CustomDrillDao {
        var savedEntity: CustomDrillEntity? = null

        override suspend fun upsert(entity: CustomDrillEntity) {
            savedEntity = entity
        }

        override suspend fun getAll(): List<CustomDrillEntity> = throw NotImplementedError()

        override suspend fun getByDrillType(drillType: String): CustomDrillEntity? =
            throw NotImplementedError()

        override suspend fun count(): Int = throw NotImplementedError()

        override suspend fun deleteByDrillType(drillType: String): Unit =
            throw NotImplementedError()

        override suspend fun getBySharedCommunityId(communityId: String): CustomDrillEntity? =
            throw NotImplementedError()
    }

    private val sourceDrill = CommunityDrill(
        id = "community-drill-123",
        name = "Forehand Drive Focus",
        baseTemplate = "forehand_drive",
        focusCsv = "elbow,knee",
        referenceType = "standard",
        strictnessX = 1.5f,
        perPhaseTargetsJson = "{\"contact\":{\"elbow\":95}}",
        creatorUid = "creator-uid",
        creatorName = "Creator Name",
        creatorPhotoUrl = "https://example.com/photo.jpg",
        sharedAtMs = 1_000L,
        ratingSum = 40L,
        ratingCount = 10L,
    )

    @Test
    fun `copyToLocal saves a fresh unlinked entity and returns it`() {
        val fakeDao = FakeCustomDrillDao()
        val repo = CustomDrillRepository(fakeDao)
        val nowMs = 1_753_000_000_000L

        val result = runBlocking { CommunityDrillCopier.copyToLocal(sourceDrill, repo, nowMs) }

        val saved = fakeDao.savedEntity
        assertEquals("custom_$nowMs", saved?.drillType)
        assertNull(saved?.baselineId)
        assertNull(saved?.sharedCommunityId)
        assertEquals(nowMs, saved?.createdAtMs)
        assertEquals(sourceDrill.name, saved?.name)
        assertEquals(sourceDrill.baseTemplate, saved?.baseTemplate)
        assertEquals(sourceDrill.focusCsv, saved?.focusCsv)
        assertEquals(sourceDrill.referenceType, saved?.referenceType)
        assertEquals(sourceDrill.strictnessX, saved?.strictnessX)
        assertEquals(sourceDrill.perPhaseTargetsJson, saved?.perPhaseTargetsJson)
        assertSame(saved, result)
    }
}
