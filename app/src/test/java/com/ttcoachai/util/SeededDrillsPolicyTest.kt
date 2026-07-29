package com.ttcoachai.util

import com.ttcoachai.db.CustomDrillDao
import com.ttcoachai.models.CustomDrillEntity
import com.ttcoachai.repository.CustomDrillRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeededDrillsPolicyTest {

    private class FakeCustomDrillDao : CustomDrillDao {
        val rows = mutableMapOf<String, CustomDrillEntity>()

        override suspend fun upsert(entity: CustomDrillEntity) {
            rows[entity.drillType] = entity
        }

        override suspend fun getAll(): List<CustomDrillEntity> = rows.values.toList()

        override suspend fun getByDrillType(drillType: String): CustomDrillEntity? = rows[drillType]

        override suspend fun count(): Int = rows.size

        override suspend fun deleteByDrillType(drillType: String) {
            rows.remove(drillType)
        }

        override suspend fun getBySharedCommunityId(communityId: String): CustomDrillEntity? =
            rows.values.firstOrNull { it.sharedCommunityId == communityId }
    }

    private val names = SeededDrillsPolicy.SeedNames(
        andriiName = "Forehand Andrii",
        generalName = "Forehand Drive General"
    )

    @Test
    fun shouldSeedWhenFlagUnsetAndTableEmpty() {
        assertTrue(SeededDrillsPolicy.shouldSeed(flagAlreadySet = false, existingDrillCount = 0))
    }

    @Test
    fun shouldNotSeedWhenFlagSetAndDrillsPresent() {
        assertFalse(SeededDrillsPolicy.shouldSeed(flagAlreadySet = true, existingDrillCount = 2))
    }

    @Test
    fun shouldReSeedWhenFlagSetButTableWiped() {
        assertTrue(SeededDrillsPolicy.shouldSeed(flagAlreadySet = true, existingDrillCount = 0))
    }

    @Test
    fun buildSeedEntitiesReturnsBothRowsWithExpectedIdsAndMovementProfile() {
        val entities = SeededDrillsPolicy.buildSeedEntities(names, nowMs = 1000L, perPhaseTargetsJson = "{}")
        assertEquals(2, entities.size)
        val andrii = entities.first { it.drillType == SeededDrillsPolicy.SEED_ANDRII_ID }
        val general = entities.first { it.drillType == SeededDrillsPolicy.SEED_GENERAL_ID }
        assertEquals("Forehand Andrii", andrii.name)
        assertEquals("standard", andrii.referenceType)
        assertNull(andrii.movementProfile)
        assertEquals("Forehand Drive General", general.name)
        assertEquals("standard", general.referenceType)
        assertEquals("general", general.movementProfile)
        assertEquals("{}", andrii.perPhaseTargetsJson)
        assertEquals("{}", general.perPhaseTargetsJson)
    }

    @Test
    fun seedMissingInsertsOnlyAbsentRows() = runBlocking {
        val dao = FakeCustomDrillDao()
        val repo = CustomDrillRepository(dao)
        val editedAndrii = CustomDrillEntity(
            drillType = SeededDrillsPolicy.SEED_ANDRII_ID,
            name = "My renamed drill",
            baseTemplate = SeededDrillsPolicy.SEED_ANDRII_ID,
            createdAtMs = 1L,
            referenceType = "standard",
            perPhaseTargetsJson = "custom edits"
        )
        dao.upsert(editedAndrii)

        val entities = SeededDrillsPolicy.buildSeedEntities(names, nowMs = 2000L, perPhaseTargetsJson = "{}")
        SeededDrillsPolicy.seedMissing(repo, entities)

        // Edited row must survive untouched.
        assertEquals("My renamed drill", dao.rows.getValue(SeededDrillsPolicy.SEED_ANDRII_ID).name)
        // Missing row must have been inserted.
        assertTrue(SeededDrillsPolicy.SEED_GENERAL_ID in dao.rows)
    }
}
