package com.ttcoachai.ui

/**
 * Pure, Android-free helper logic for the Exercise editor screens (10c New / 10d Clone/Edit).
 * Kept in its own file with no Android / org.json imports so it is unit-testable on the JVM
 * without Robolectric, mirroring [SwipeSettle.kt].
 */

enum class EditorMode { NEW, CLONE, EDIT }

const val REFERENCE_STANDARD: String = "standard"
const val REFERENCE_BASELINE: String = "baseline"

const val FOCUS_ARM: String = "arm"
const val FOCUS_SHOULDERS: String = "shoulders"
const val FOCUS_LEGS: String = "legs"
const val FOCUS_CORE: String = "core"
const val FOCUS_HIPS: String = "hips"

/** Canonical ordering used whenever focus keys are serialized or listed. */
val FOCUS_ORDER: List<String> = listOf(FOCUS_ARM, FOCUS_SHOULDERS, FOCUS_LEGS, FOCUS_CORE, FOCUS_HIPS)

/** Focus key -> contributed metric name(s), applied in [FOCUS_ORDER] to derive canonical metric order. */
private val FOCUS_TO_METRICS: Map<String, List<String>> = mapOf(
    FOCUS_ARM to listOf("elbow"),
    FOCUS_SHOULDERS to listOf("shoulder"),
    FOCUS_LEGS to listOf("knees"),
    FOCUS_CORE to listOf("torso tilt", "shoulder tilt"),
    FOCUS_HIPS to listOf("hips"),
)

data class EditorState(
    val name: String,
    val focusKeys: Set<String>,
    val referenceType: String,
    val strictnessX: Float,
    val perPhaseTargetsJson: String,
    val baselineId: Long?,
) {
    companion object {
        fun emptyNew() = EditorState(
            name = "",
            focusKeys = emptySet(),
            referenceType = REFERENCE_STANDARD,
            strictnessX = 1.0f,
            perPhaseTargetsJson = "",
            baselineId = null,
        )
    }
}

/** Split a CSV of focus keys, trimming whitespace and dropping blank entries. */
fun parseFocusCsv(csv: String): Set<String> =
    csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

/** Serialize focus keys in canonical [FOCUS_ORDER], dropping any unrecognized keys. */
fun focusToCsv(keys: Set<String>): String =
    FOCUS_ORDER.filter { it in keys }.joinToString(",")

/**
 * Map focus keys to the metrics they activate, deduplicated and returned in canonical metric
 * order (derived by walking [FOCUS_ORDER] and appending each focus's metrics if not already seen).
 */
fun activeMetricsFor(keys: Set<String>): List<String> {
    val result = mutableListOf<String>()
    for (focus in FOCUS_ORDER) {
        if (focus !in keys) continue
        for (metric in FOCUS_TO_METRICS.getValue(focus)) {
            if (metric !in result) result.add(metric)
        }
    }
    return result
}

/** Build a clone display name by appending [copySuffix] to [sourceName], space-separated. */
fun cloneName(sourceName: String, copySuffix: String): String = "$sourceName $copySuffix"

/** Resolve the editor's initial name field for a given [mode]. */
fun nameForMode(mode: EditorMode, sourceName: String, copySuffix: String): String =
    when (mode) {
        EditorMode.NEW -> ""
        EditorMode.CLONE -> cloneName(sourceName, copySuffix)
        EditorMode.EDIT -> sourceName
    }

/**
 * Resolve the editor's initial [EditorState] for a given [mode].
 *
 * - NEW: a blank [EditorState.emptyNew], [source] is ignored.
 * - CLONE: requires non-null [source]; the clone keeps focus/strictness/per-phase targets but
 *   gets a suffixed name, resets reference type to [REFERENCE_STANDARD], and clears baselineId
 *   (a clone is not yet calibrated).
 * - EDIT: requires non-null [source]; returned unchanged.
 */
fun stateForMode(mode: EditorMode, source: EditorState?, copySuffix: String): EditorState =
    when (mode) {
        EditorMode.NEW -> EditorState.emptyNew()
        EditorMode.CLONE -> {
            val src = requireNotNull(source) { "source must not be null for CLONE mode" }
            src.copy(
                name = cloneName(src.name, copySuffix),
                referenceType = REFERENCE_STANDARD,
                baselineId = null,
            )
        }
        EditorMode.EDIT -> requireNotNull(source) { "source must not be null for EDIT mode" }
    }

/** Whether the given reference type requires a personal-baseline calibration before use. */
fun isCalibrationRequired(referenceType: String): Boolean = referenceType == REFERENCE_BASELINE
