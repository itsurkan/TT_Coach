package com.ttcoachai.fragment

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ttcoachai.Exercise
import com.ttcoachai.ExerciseAdapter
import com.ttcoachai.R
import com.ttcoachai.TrainingActivity
import com.ttcoachai.calibration.CalibrationActivity
import com.ttcoachai.databinding.FragmentDrillsBinding
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.debug.BaselinePreviewActivity
import com.ttcoachai.models.CustomDrillEntity
import com.ttcoachai.repository.CustomDrillRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DrillsFragment : Fragment() {

    private var _binding: FragmentDrillsBinding? = null
    private val binding get() = _binding!!

    private lateinit var builtInExercises: List<Exercise>
    private var customExercises: List<Exercise> = emptyList()
    private lateinit var adapter: ExerciseAdapter

    private val customDrillRepo by lazy {
        CustomDrillRepository(AppDatabase.getDatabase(requireContext()).customDrillDao())
    }

    private var pendingCustomDrillType: String? = null

    private val calibrationLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val drillType = pendingCustomDrillType ?: return@registerForActivityResult
            pendingCustomDrillType = null
            if (result.resultCode == Activity.RESULT_OK) {
                promptForCustomDrillName(drillType)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDrillsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupData()
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        reloadCustomDrills()
    }

    private fun setupData() {
        builtInExercises = listOf(
            Exercise(
                id = "forehand_drive",
                name = getString(R.string.exercise_forehand_name),
                description = getString(R.string.exercise_forehand_desc),
                difficulty = getString(R.string.difficulty_beginner),
                duration = getString(R.string.duration_10_15),
                category = getString(R.string.cat_technique)
            ),
            Exercise(
                id = "forehand_andrii",
                name = getString(R.string.exercise_forehand_andrii_name),
                description = getString(R.string.exercise_forehand_andrii_desc),
                difficulty = getString(R.string.difficulty_beginner),
                duration = getString(R.string.duration_10_15),
                category = getString(R.string.cat_technique)
            ),
            Exercise(
                id = "backhand_loop",
                name = getString(R.string.exercise_backhand_loop_name),
                description = getString(R.string.exercise_backhand_loop_desc),
                difficulty = getString(R.string.difficulty_intermediate),
                duration = getString(R.string.duration_20),
                category = getString(R.string.cat_spin),
                isLocked = true
            ),
            Exercise(
                id = "serve_practice",
                name = getString(R.string.exercise_service_name),
                description = getString(R.string.exercise_service_desc),
                difficulty = getString(R.string.difficulty_all_levels),
                duration = getString(R.string.duration_10),
                category = getString(R.string.cat_serving),
                isLocked = true
            ),
            Exercise(
                id = "footwork_drill",
                name = getString(R.string.exercise_footwork_name),
                description = getString(R.string.exercise_footwork_desc),
                difficulty = getString(R.string.difficulty_intermediate),
                duration = getString(R.string.duration_25),
                category = getString(R.string.cat_footwork),
                isLocked = true
            ),
            Exercise(
                id = "multiball_rally",
                name = getString(R.string.exercise_multiball_name),
                description = getString(R.string.exercise_multiball_desc),
                difficulty = getString(R.string.difficulty_advanced),
                duration = getString(R.string.duration_30),
                category = getString(R.string.cat_speed),
                isLocked = true
            ),
            Exercise(
                id = "consistency_challenge",
                name = getString(R.string.exercise_consistency_name),
                description = getString(R.string.exercise_consistency_desc),
                difficulty = getString(R.string.difficulty_beginner),
                duration = getString(R.string.duration_10_15),
                category = getString(R.string.cat_control),
                isLocked = true
            )
        )
    }

    private fun setupUI() {
        binding.rvDrills.layoutManager = LinearLayoutManager(context)
        adapter = ExerciseAdapter(
            exercises = builtInExercises,
            onExerciseClick = { onExerciseSelected(it) },
            onExerciseLongClick = { showDrillOptions(it) }
        )
        binding.rvDrills.adapter = adapter

        binding.btnStartFeatured.setOnClickListener {
            val forehand = builtInExercises.find { it.id == "forehand_drive" }
            if (forehand != null) onExerciseSelected(forehand)
        }

        binding.chipAll.setOnClickListener { filterExercises("All") }
        binding.chipBeginner.setOnClickListener { filterExercises("Beginner") }
        binding.chipIntermediate.setOnClickListener { filterExercises("Intermediate") }
        binding.chipAdvanced.setOnClickListener { filterExercises("Advanced") }

        binding.btnAddCustomDrill.setOnClickListener { launchCustomDrillCalibration() }
    }

    private fun filterExercises(difficulty: String) {
        val all = builtInExercises + customExercises
        val filtered = if (difficulty == "All") {
            all
        } else {
            all.filter { it.difficulty.equals(difficulty, ignoreCase = true) }
        }
        adapter.updateList(filtered)
    }

    /**
     * Long-press on a drill row opens the drill-action picker:
     *  - "Calibrate" launches CalibrationActivity. Custom drills pass their
     *    own `custom_<ts>` drillType; built-ins use the legacy
     *    forehand_shadow default until per-drill calibration lands.
     *  - "Edit in UI mode" opens the BaselinePreviewActivity parameter
     *    editor (Phase 7). Hidden on release builds via FLAG_DEBUGGABLE.
     */
    private fun showDrillOptions(exercise: Exercise) {
        val ctx = requireContext()
        val isDebuggable = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val labels = mutableListOf(getString(R.string.drill_menu_calibrate))
        if (isDebuggable) labels += getString(R.string.drill_menu_edit_ui_mode)

        AlertDialog.Builder(ctx)
            .setTitle(exercise.name)
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(ctx, CalibrationActivity::class.java).apply {
                            if (exercise.id.startsWith(CUSTOM_DRILL_PREFIX)) {
                                putExtra(CalibrationActivity.EXTRA_DRILL_TYPE, exercise.id)
                            }
                        }
                    )
                    1 -> startActivity(
                        Intent(ctx, BaselinePreviewActivity::class.java)
                            .putExtra(BaselinePreviewActivity.EXTRA_DRILL_TYPE, exercise.id)
                    )
                }
            }
            .show()
    }

    private fun launchCustomDrillCalibration() {
        val drillType = "$CUSTOM_DRILL_PREFIX${System.currentTimeMillis()}"
        pendingCustomDrillType = drillType
        val intent = Intent(requireContext(), CalibrationActivity::class.java)
            .putExtra(CalibrationActivity.EXTRA_DRILL_TYPE, drillType)
        calibrationLauncher.launch(intent)
    }

    private fun promptForCustomDrillName(drillType: String) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = getString(R.string.drills_custom_name_hint)
            setSingleLine(true)
        }
        lifecycleScope.launch {
            val nextIndex = withContext(Dispatchers.IO) { customDrillRepo.count() } + 1
            val defaultName = getString(R.string.drills_custom_default_name, nextIndex)
            input.setText(defaultName)
            input.setSelection(0, defaultName.length)

            AlertDialog.Builder(ctx)
                .setTitle(R.string.drills_custom_name_title)
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(R.string.drills_custom_name_save) { _, _ ->
                    val name = input.text.toString().trim().ifEmpty { defaultName }
                    persistCustomDrill(drillType, name)
                }
                .show()
        }
    }

    private fun persistCustomDrill(drillType: String, name: String) {
        val entity = CustomDrillEntity(
            drillType = drillType,
            name = name,
            baseTemplate = "forehand_drive",
            createdAtMs = System.currentTimeMillis()
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { customDrillRepo.save(entity) }
            Toast.makeText(
                requireContext(),
                getString(R.string.drills_custom_saved_toast, name),
                Toast.LENGTH_SHORT
            ).show()
            reloadCustomDrills()
        }
    }

    private fun reloadCustomDrills() {
        if (_binding == null) return
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) { customDrillRepo.getAll() }
            customExercises = rows.map { it.toExercise() }
            adapter.updateList(builtInExercises + customExercises)
        }
    }

    private fun CustomDrillEntity.toExercise(): Exercise = Exercise(
        id = drillType,
        name = name,
        description = getString(R.string.exercise_forehand_desc),
        difficulty = getString(R.string.difficulty_beginner),
        duration = getString(R.string.duration_10_15),
        category = getString(R.string.drills_custom_category)
    )

    private fun onExerciseSelected(exercise: Exercise) {
        if (exercise.isLocked) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.exercise_locked_title)
                .setMessage(R.string.exercise_locked_message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            return
        }

        val intent = Intent(requireContext(), TrainingActivity::class.java).apply {
            putExtra("EXERCISE_ID", exercise.id)
            putExtra("EXERCISE_NAME", exercise.name)
            putExtra("USE_VIDEO", exercise.useVideo)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CUSTOM_DRILL_PREFIX = "custom_"
    }
}
