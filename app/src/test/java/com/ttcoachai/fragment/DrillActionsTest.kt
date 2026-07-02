package com.ttcoachai.fragment

import com.ttcoachai.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillActionsTest {

    private fun ex(id: String) = Exercise(id = id, name = id, description = "", difficulty = "", duration = "")

    @Test
    fun builtIn_isNotCustom_cloneAndContinueOnly() {
        val e = ex("forehand_drive")
        assertFalse(DrillActions.isCustom(e))
        assertTrue(DrillActions.canClone(e))
        assertTrue(DrillActions.canContinue(e))
        assertFalse(DrillActions.canEdit(e))
        assertFalse(DrillActions.canRename(e))
        assertFalse(DrillActions.canDelete(e))
    }

    @Test
    fun custom_isCustom_allActionsAllowed() {
        val e = ex("custom_1720000000000")
        assertTrue(DrillActions.isCustom(e))
        assertTrue(DrillActions.canClone(e))
        assertTrue(DrillActions.canContinue(e))
        assertTrue(DrillActions.canEdit(e))
        assertTrue(DrillActions.canRename(e))
        assertTrue(DrillActions.canDelete(e))
    }

    @Test
    fun partition_pullsRecentOutAndKeepsOrder() {
        val all = listOf(ex("a"), ex("b"), ex("c"))
        val (recent, programs) = DrillActions.partition(all, "b")
        assertEquals("b", recent?.id)
        assertEquals(listOf("a", "c"), programs.map { it.id })
    }

    @Test
    fun partition_nullRecentId_returnsNullRecentAndFullList() {
        val all = listOf(ex("a"), ex("b"))
        val (recent, programs) = DrillActions.partition(all, null)
        assertNull(recent)
        assertEquals(listOf("a", "b"), programs.map { it.id })
    }

    @Test
    fun partition_unknownRecentId_returnsNullRecentAndFullList() {
        val all = listOf(ex("a"), ex("b"))
        val (recent, programs) = DrillActions.partition(all, "zzz")
        assertNull(recent)
        assertEquals(listOf("a", "b"), programs.map { it.id })
    }
}
