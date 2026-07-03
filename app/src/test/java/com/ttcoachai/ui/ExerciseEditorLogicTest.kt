package com.ttcoachai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseEditorLogicTest {

    // --- parseFocusCsv / focusToCsv round-trip ---

    @Test
    fun `parseFocusCsv splits trims and drops blanks`() {
        assertEquals(setOf("arm", "shoulders"), parseFocusCsv("arm, shoulders"))
        assertEquals(setOf("arm"), parseFocusCsv("arm,,"))
        assertEquals(emptySet<String>(), parseFocusCsv(""))
        assertEquals(emptySet<String>(), parseFocusCsv("   "))
        assertEquals(setOf("legs"), parseFocusCsv(" legs "))
    }

    @Test
    fun `focusToCsv orders canonically and drops unknown keys`() {
        assertEquals("arm,shoulders", focusToCsv(setOf("shoulders", "arm")))
        assertEquals(
            "arm,shoulders,legs,core,hips",
            focusToCsv(setOf("hips", "core", "legs", "shoulders", "arm")),
        )
        assertEquals("arm", focusToCsv(setOf("arm", "bogus")))
        assertEquals("", focusToCsv(emptySet()))
        assertEquals("", focusToCsv(setOf("bogus")))
    }

    @Test
    fun `focusToCsv has no trailing comma`() {
        val csv = focusToCsv(setOf("hips"))
        assertFalse(csv.endsWith(","))
        assertEquals("hips", csv)
    }

    @Test
    fun `parseFocusCsv then focusToCsv round trips canonical order`() {
        val csv = "legs,arm,core"
        val parsed = parseFocusCsv(csv)
        assertEquals("arm,legs,core", focusToCsv(parsed))
    }

    // --- activeMetricsFor ---

    @Test
    fun `activeMetricsFor empty set is empty list`() {
        assertEquals(emptyList<String>(), activeMetricsFor(emptySet()))
    }

    @Test
    fun `activeMetricsFor arm and shoulders`() {
        assertEquals(listOf("elbow", "shoulder"), activeMetricsFor(setOf("arm", "shoulders")))
    }

    @Test
    fun `activeMetricsFor core maps to torso and shoulder tilt`() {
        assertEquals(listOf("torso tilt", "shoulder tilt"), activeMetricsFor(setOf("core")))
    }

    @Test
    fun `activeMetricsFor all five in canonical metric order`() {
        val all = setOf("arm", "shoulders", "legs", "core", "hips")
        assertEquals(
            listOf("elbow", "shoulder", "knees", "torso tilt", "shoulder tilt", "hips"),
            activeMetricsFor(all),
        )
    }

    @Test
    fun `activeMetricsFor dedups and ignores order of input set`() {
        // shoulders contributes "shoulder"; core contributes "shoulder tilt" too - distinct metrics
        val result = activeMetricsFor(setOf("shoulders", "core", "shoulders"))
        assertEquals(listOf("shoulder", "torso tilt", "shoulder tilt"), result)
    }

    // --- nameForMode / cloneName ---

    @Test
    fun `cloneName appends suffix with single space`() {
        assertEquals("Forehand Drive Copy", cloneName("Forehand Drive", "Copy"))
    }

    @Test
    fun `nameForMode NEW is blank`() {
        assertEquals("", nameForMode(EditorMode.NEW, "Forehand Drive", "Copy"))
    }

    @Test
    fun `nameForMode CLONE uses cloneName`() {
        assertEquals("Forehand Drive Copy", nameForMode(EditorMode.CLONE, "Forehand Drive", "Copy"))
    }

    @Test
    fun `nameForMode EDIT returns source name unchanged`() {
        assertEquals("Forehand Drive", nameForMode(EditorMode.EDIT, "Forehand Drive", "Copy"))
    }

    // --- stateForMode ---

    @Test
    fun `stateForMode NEW returns emptyNew regardless of source`() {
        val state = stateForMode(EditorMode.NEW, source = null, copySuffix = "Copy")
        assertEquals(EditorState.emptyNew(), state)
    }

    @Test
    fun `stateForMode CLONE suffixes name resets reference and baseline preserves rest`() {
        val source = EditorState(
            name = "Forehand Drive",
            focusKeys = setOf("arm", "core"),
            referenceType = REFERENCE_BASELINE,
            strictnessX = 1.5f,
            perPhaseTargetsJson = """{"contact":{"elbow":90}}""",
            baselineId = 42L,
        )
        val cloned = stateForMode(EditorMode.CLONE, source, "Copy")

        assertEquals("Forehand Drive Copy", cloned.name)
        assertEquals(REFERENCE_STANDARD, cloned.referenceType)
        assertEquals(null, cloned.baselineId)
        // preserved fields
        assertEquals(setOf("arm", "core"), cloned.focusKeys)
        assertEquals(1.5f, cloned.strictnessX)
        assertEquals("""{"contact":{"elbow":90}}""", cloned.perPhaseTargetsJson)
    }

    @Test
    fun `stateForMode EDIT returns source unchanged`() {
        val source = EditorState(
            name = "Forehand Drive",
            focusKeys = setOf("legs"),
            referenceType = REFERENCE_BASELINE,
            strictnessX = 0.8f,
            perPhaseTargetsJson = "",
            baselineId = 7L,
        )
        val edited = stateForMode(EditorMode.EDIT, source, "Copy")
        assertEquals(source, edited)
    }

    @Test
    fun `stateForMode CLONE with null source throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            stateForMode(EditorMode.CLONE, null, "Copy")
        }
    }

    @Test
    fun `stateForMode EDIT with null source throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            stateForMode(EditorMode.EDIT, null, "Copy")
        }
    }

    // --- isCalibrationRequired ---

    @Test
    fun `isCalibrationRequired true for baseline`() {
        assertTrue(isCalibrationRequired(REFERENCE_BASELINE))
    }

    @Test
    fun `isCalibrationRequired false for standard`() {
        assertFalse(isCalibrationRequired(REFERENCE_STANDARD))
    }
}
