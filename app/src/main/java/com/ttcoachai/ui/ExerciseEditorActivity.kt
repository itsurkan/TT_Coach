package com.ttcoachai.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ttcoachai.BaseActivity
import com.ttcoachai.R
import com.ttcoachai.pose.LiveCalibrationActivity
import com.ttcoachai.databinding.ActivityExerciseEditorBinding
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.models.CustomDrillEntity
import com.ttcoachai.pose.LiveDrillActivity
import com.ttcoachai.repository.CustomDrillRepository
import com.ttcoachai.repository.PersonalBaselineRepository
import com.ttcoachai.shared.analysis.BaselineHintBand
import com.ttcoachai.shared.drill.DrillMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Exercise editor (screens 10c New / 10d Clone/Edit).
 *
 * Drives [com.ttcoachai.ui.ExerciseEditorLogic]'s pure [EditorMode]/[EditorState] over the
 * `activity_exercise_editor.xml` layout, persisting to the same [CustomDrillRepository] used by
 * `DrillsFragment`. Task 4 wires entry points (Drills list actions) via the factory methods below
 * — this Activity does not know about `DrillsFragment`.
 */
class ExerciseEditorActivity : BaseActivity() {

    private lateinit var binding: ActivityExerciseEditorBinding

    private val customDrillRepo by lazy {
        CustomDrillRepository(AppDatabase.getDatabase(this).customDrillDao())
    }

    private lateinit var mode: EditorMode
    private lateinit var workingDrillType: String
    private var sourceName: String = ""
    private var baseTemplateExtra: String = ""

    /** Preserved across mode resolution / calibration round-trip. */
    private var currentReferenceType: String = REFERENCE_STANDARD
    private var currentBaselineId: Long? = null
    private var currentFocusKeys: MutableSet<String> = mutableSetOf()

    /** Loaded entity for EDIT (and CLONE-of-custom), kept to preserve baseTemplate/createdAtMs. */
    private var loadedEntity: CustomDrillEntity? = null

    /** Guards the reference RadioGroup listener while we programmatically revert a selection. */
    private var suppressReferenceListener = false

    private val calibrationLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                currentReferenceType = REFERENCE_BASELINE
                setReferenceChecked(REFERENCE_BASELINE)
            } else {
                currentReferenceType = REFERENCE_STANDARD
                setReferenceChecked(REFERENCE_STANDARD)
            }
        }

    /** Ordered (metric, phase) rows driving both encode and decode of the advanced JSON. */
    private data class AdvancedRow(
        val fromId: Int,
        val toId: Int,
        val jsonKey: String,
    )

    private val advancedRows: List<AdvancedRow> by lazy {
        listOf(
            AdvancedRow(R.id.et_elbow_strike_from, R.id.et_elbow_strike_to, DrillMetrics.METRIC_ELBOW_ANGLE),
            AdvancedRow(R.id.et_shoulder_strike_from, R.id.et_shoulder_strike_to, DrillMetrics.METRIC_SHOULDER_ANGLE),
            AdvancedRow(R.id.et_knees_strike_from, R.id.et_knees_strike_to, DrillMetrics.METRIC_KNEE_BEND),
            AdvancedRow(R.id.et_torso_strike_from, R.id.et_torso_strike_to, DrillMetrics.METRIC_TORSO_LEAN),
            AdvancedRow(R.id.et_elbow_finish_from, R.id.et_elbow_finish_to, DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D),
            AdvancedRow(R.id.et_stroke_speed_from, R.id.et_stroke_speed_to, DrillMetrics.METRIC_STROKE_SPEED),
            AdvancedRow(R.id.et_body_rotation_from, R.id.et_body_rotation_to, DrillMetrics.METRIC_COIL_RATIO),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = runCatching {
            EditorMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: EditorMode.NEW.name)
        }.getOrDefault(EditorMode.NEW)
        sourceName = intent.getStringExtra(EXTRA_SOURCE_NAME) ?: ""
        baseTemplateExtra = intent.getStringExtra(EXTRA_BASE_TEMPLATE) ?: ""

        workingDrillType = savedInstanceState?.getString(STATE_WORKING_DRILL_TYPE)
            ?: when (mode) {
                EditorMode.EDIT -> intent.getStringExtra(EXTRA_SOURCE_DRILL_TYPE)
                    ?: "${CUSTOM_DRILL_PREFIX}${System.currentTimeMillis()}"
                EditorMode.NEW, EditorMode.CLONE -> "${CUSTOM_DRILL_PREFIX}${System.currentTimeMillis()}"
            }

        setupModeChrome()
        wireInteractions()
        loadInitialState()
        loadBaselineHints()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_WORKING_DRILL_TYPE, workingDrillType)
    }

    // region Mode chrome

    private fun setupModeChrome() {
        binding.tvEditorTitle.setText(
            when (mode) {
                EditorMode.NEW -> R.string.exercise_editor_title_new
                EditorMode.CLONE -> R.string.exercise_editor_title_clone
                EditorMode.EDIT -> R.string.exercise_editor_title_edit
            }
        )

        val isClone = mode == EditorMode.CLONE
        binding.tvEditorCopyBadge.visibility = if (isClone) View.VISIBLE else View.GONE
        binding.tvEditorSubtitle.apply {
            if (isClone) {
                text = getString(R.string.exercise_editor_clone_subtitle, sourceName)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        binding.btnEditorPrimary.setText(
            when (mode) {
                EditorMode.NEW -> R.string.exercise_editor_action_create
                EditorMode.CLONE -> R.string.exercise_editor_action_save_copy
                EditorMode.EDIT -> R.string.exercise_editor_action_save
            }
        )
    }

    // endregion

    // region Initial state load + bind

    private fun loadInitialState() {
        val copySuffix = getString(R.string.drill_clone_copy_suffix)
        lifecycleScope.launch {
            val resolved: EditorState = when (mode) {
                EditorMode.NEW -> EditorState.emptyNew()
                EditorMode.CLONE -> {
                    val sourceDrillType = intent.getStringExtra(EXTRA_SOURCE_DRILL_TYPE)
                    val entity = sourceDrillType?.let {
                        withContext(Dispatchers.IO) { customDrillRepo.get(it) }
                    }
                    loadedEntity = entity
                    val base = if (entity != null) {
                        EditorState(
                            name = entity.name,
                            focusKeys = parseFocusCsv(entity.focusCsv),
                            referenceType = entity.referenceType,
                            strictnessX = entity.strictnessX,
                            perPhaseTargetsJson = entity.perPhaseTargetsJson,
                            baselineId = entity.baselineId,
                        )
                    } else {
                        EditorState.emptyNew().copy(name = sourceName)
                    }
                    stateForMode(EditorMode.CLONE, base, copySuffix)
                }
                EditorMode.EDIT -> {
                    val entity = withContext(Dispatchers.IO) { customDrillRepo.get(workingDrillType) }
                    loadedEntity = entity
                    val base = if (entity != null) {
                        EditorState(
                            name = entity.name,
                            focusKeys = parseFocusCsv(entity.focusCsv),
                            referenceType = entity.referenceType,
                            strictnessX = entity.strictnessX,
                            perPhaseTargetsJson = entity.perPhaseTargetsJson,
                            baselineId = entity.baselineId,
                        )
                    } else {
                        EditorState.emptyNew()
                    }
                    stateForMode(EditorMode.EDIT, base, copySuffix)
                }
            }
            bindState(resolved)
        }
    }

    private fun bindState(state: EditorState) {
        binding.etEditorName.setText(state.name)

        currentFocusKeys = state.focusKeys.toMutableSet()
        setFocusChipsChecked(currentFocusKeys)
        updateActiveMetrics(currentFocusKeys)

        currentReferenceType = state.referenceType
        setReferenceChecked(state.referenceType)

        currentBaselineId = state.baselineId

        binding.sliderStrictness.value = state.strictnessX
        updateStrictnessLabel(state.strictnessX)

        decodeAdvancedTargets(state.perPhaseTargetsJson)
    }

    /**
     * Replaces the static XML hint on each row with a baseline-derived `mean ± kSigma·std` band,
     * so an empty field's placeholder reflects what actually applies (the derived-baseline
     * consistency check) rather than a hardcoded generic range. Loaded asynchronously — same
     * lookup TrainingActivity uses (active baseline for [LiveDrillActivity.DRILL_TYPE]) — and
     * left untouched (static hint stays) on any failure: no baseline, missing metric, or a
     * degenerate (std <= 0) stat.
     */
    private fun loadBaselineHints() {
        lifecycleScope.launch {
            val baseline = try {
                PersonalBaselineRepository(AppDatabase.getDatabase(this@ExerciseEditorActivity).personalBaselineDao())
                    .getActiveBaseline(LiveDrillActivity.DRILL_TYPE)
                    .first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: return@launch

            for (row in advancedRows) {
                val band = BaselineHintBand.compute(baseline.metricStats[row.jsonKey]) ?: continue
                findViewById<android.widget.EditText>(row.fromId).hint = band.first.toString()
                findViewById<android.widget.EditText>(row.toId).hint = band.second.toString()
            }
        }
    }

    // endregion

    // region Interactions

    private fun wireInteractions() {
        binding.btnEditorBack.setOnClickListener { finishCancelled() }
        binding.btnEditorCancel.setOnClickListener { finishCancelled() }

        binding.cgFocus.setOnCheckedStateChangeListener { _, _ ->
            currentFocusKeys = readCheckedFocusKeys()
            updateActiveMetrics(currentFocusKeys)
        }

        binding.sliderStrictness.addOnChangeListener { _, value, _ ->
            updateStrictnessLabel(value)
        }

        binding.rowAdvancedHeader.setOnClickListener { toggleAdvanced() }

        binding.rgReference.setOnCheckedChangeListener { _, checkedId ->
            if (suppressReferenceListener) return@setOnCheckedChangeListener
            when (checkedId) {
                R.id.rb_reference_baseline -> launchCalibration()
                R.id.rb_reference_standard -> currentReferenceType = REFERENCE_STANDARD
            }
        }

        binding.btnEditorPrimary.setOnClickListener { onPrimaryClicked() }
    }

    private fun readCheckedFocusKeys(): MutableSet<String> {
        val keys = mutableSetOf<String>()
        if (binding.chipFocusArm.isChecked) keys += FOCUS_ARM
        if (binding.chipFocusShoulders.isChecked) keys += FOCUS_SHOULDERS
        if (binding.chipFocusLegs.isChecked) keys += FOCUS_LEGS
        if (binding.chipFocusCore.isChecked) keys += FOCUS_CORE
        if (binding.chipFocusHips.isChecked) keys += FOCUS_HIPS
        return keys
    }

    private fun setFocusChipsChecked(keys: Set<String>) {
        binding.chipFocusArm.isChecked = FOCUS_ARM in keys
        binding.chipFocusShoulders.isChecked = FOCUS_SHOULDERS in keys
        binding.chipFocusLegs.isChecked = FOCUS_LEGS in keys
        binding.chipFocusCore.isChecked = FOCUS_CORE in keys
        binding.chipFocusHips.isChecked = FOCUS_HIPS in keys
    }

    private fun updateActiveMetrics(keys: Set<String>) {
        if (keys.isEmpty()) {
            binding.tvActiveMetrics.visibility = View.GONE
            return
        }
        binding.tvActiveMetrics.visibility = View.VISIBLE
        binding.tvActiveMetrics.text = getString(
            R.string.exercise_editor_active_metrics,
            activeMetricsFor(keys).joinToString(", ")
        )
    }

    private fun updateStrictnessLabel(value: Float) {
        binding.tvStrictnessValue.text = String.format(java.util.Locale.US, "×%.2f", value)
    }

    private fun toggleAdvanced() {
        val expanding = binding.containerAdvanced.visibility != View.VISIBLE
        binding.containerAdvanced.visibility = if (expanding) View.VISIBLE else View.GONE
        binding.ivAdvancedChevron.rotation = if (expanding) 90f else 0f
    }

    private fun setReferenceChecked(referenceType: String) {
        suppressReferenceListener = true
        if (referenceType == REFERENCE_BASELINE) {
            binding.rgReference.check(R.id.rb_reference_baseline)
        } else {
            binding.rgReference.check(R.id.rb_reference_standard)
        }
        suppressReferenceListener = false
    }

    private fun launchCalibration() {
        calibrationLauncher.launch(Intent(this, LiveCalibrationActivity::class.java))
    }

    private fun finishCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    // endregion

    // region Advanced per-phase JSON

    private fun encodeAdvancedTargets(): String {
        val bands = mutableMapOf<String, Pair<Float, Float>>()
        for (row in advancedRows) {
            val fromText = findViewById<android.widget.EditText>(row.fromId).text?.toString()?.trim().orEmpty()
            val toText = findViewById<android.widget.EditText>(row.toId).text?.toString()?.trim().orEmpty()
            if (fromText.isEmpty() || toText.isEmpty()) continue
            val from = fromText.toFloatOrNull() ?: continue
            val to = toText.toFloatOrNull() ?: continue
            bands[row.jsonKey] = from to to
        }
        return com.ttcoachai.util.PerPhaseTargetsCodec.encode(bands)
    }

    private fun decodeAdvancedTargets(perPhaseTargetsJson: String) {
        // Always clear first so re-binding (e.g. after rotation) doesn't leave stale values.
        for (row in advancedRows) {
            findViewById<android.widget.EditText>(row.fromId).setText("")
            findViewById<android.widget.EditText>(row.toId).setText("")
        }
        // Shared parser (com.ttcoachai.util.PerPhaseTargetsCodec) so this UI-bound decode and
        // TrainingActivity's live-path decode read the same JSON format one way, not two.
        val parsed = com.ttcoachai.util.PerPhaseTargetsCodec.parse(perPhaseTargetsJson)
        for (row in advancedRows) {
            val (from, to) = parsed[row.jsonKey] ?: continue
            findViewById<android.widget.EditText>(row.fromId).setText(formatBandValue(from))
            findViewById<android.widget.EditText>(row.toId).setText(formatBandValue(to))
        }
    }

    private fun formatBandValue(value: Float): String =
        if (value == kotlin.math.floor(value)) value.toInt().toString() else value.toString()

    // endregion

    // region Persist

    private fun onPrimaryClicked() {
        val name = binding.etEditorName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.exercise_editor_name_hint, Toast.LENGTH_SHORT).show()
            return
        }

        val baseTemplate = when (mode) {
            EditorMode.NEW -> workingDrillType
            EditorMode.CLONE -> baseTemplateExtra.ifEmpty { workingDrillType }
            EditorMode.EDIT -> loadedEntity?.baseTemplate ?: workingDrillType
        }
        val createdAtMs = when (mode) {
            EditorMode.NEW, EditorMode.CLONE -> System.currentTimeMillis()
            EditorMode.EDIT -> loadedEntity?.createdAtMs ?: System.currentTimeMillis()
        }

        val entity = CustomDrillEntity(
            drillType = workingDrillType,
            name = name,
            baseTemplate = baseTemplate,
            createdAtMs = createdAtMs,
            focusCsv = focusToCsv(currentFocusKeys),
            referenceType = currentReferenceType,
            baselineId = currentBaselineId,
            strictnessX = binding.sliderStrictness.value,
            perPhaseTargetsJson = encodeAdvancedTargets(),
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { customDrillRepo.save(entity) }
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    // endregion

    companion object {
        const val EXTRA_MODE = "exercise_editor_mode"
        const val EXTRA_SOURCE_DRILL_TYPE = "exercise_editor_source_drill_type"
        const val EXTRA_SOURCE_NAME = "exercise_editor_source_name"
        const val EXTRA_BASE_TEMPLATE = "exercise_editor_base_template"

        /** Must match `DrillsFragment.CUSTOM_DRILL_PREFIX` — custom drill types share this prefix. */
        private const val CUSTOM_DRILL_PREFIX = "custom_"

        private const val STATE_WORKING_DRILL_TYPE = "state_working_drill_type"

        fun newIntentNew(ctx: Context): Intent =
            Intent(ctx, ExerciseEditorActivity::class.java)
                .putExtra(EXTRA_MODE, EditorMode.NEW.name)

        fun newIntentClone(
            ctx: Context,
            sourceDrillType: String,
            sourceName: String,
            baseTemplate: String,
        ): Intent =
            Intent(ctx, ExerciseEditorActivity::class.java)
                .putExtra(EXTRA_MODE, EditorMode.CLONE.name)
                .putExtra(EXTRA_SOURCE_DRILL_TYPE, sourceDrillType)
                .putExtra(EXTRA_SOURCE_NAME, sourceName)
                .putExtra(EXTRA_BASE_TEMPLATE, baseTemplate)

        fun newIntentEdit(ctx: Context, drillType: String): Intent =
            Intent(ctx, ExerciseEditorActivity::class.java)
                .putExtra(EXTRA_MODE, EditorMode.EDIT.name)
                .putExtra(EXTRA_SOURCE_DRILL_TYPE, drillType)
    }
}
