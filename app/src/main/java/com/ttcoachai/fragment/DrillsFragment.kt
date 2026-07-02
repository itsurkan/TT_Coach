package com.ttcoachai.fragment

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ttcoachai.Exercise
import com.ttcoachai.ExerciseAdapter
import com.ttcoachai.R
import com.ttcoachai.TrainingActivity
import com.ttcoachai.calibration.CalibrationActivity
import com.ttcoachai.databinding.FragmentDrillsBinding
import com.ttcoachai.db.AppDatabase
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

    private val trainingDao by lazy {
        AppDatabase.getDatabase(requireContext()).trainingDao()
    }

    /** Merged built-in + custom list, reused by the drill-options menu and swipe actions. */
    var currentDrills: List<Exercise> = emptyList()
        private set

    /** Last list bound to the adapter (ALL PROGRAMS only), used to map a swipe position to an Exercise. */
    private var boundPrograms: List<Exercise> = emptyList()

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

        binding.btnAddCustomDrill.setOnClickListener { launchCustomDrillCalibration() }
        binding.fabAddDrill.setOnClickListener { launchCustomDrillCalibration() }

        attachSwipeActions()
    }

    /**
     * Swipe-left = Clone (gold), swipe-right = Delete (red), gated per-row by [DrillActions]:
     * built-ins allow Clone only, custom drills allow both.
     */
    private fun attachSwipeActions() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            private val goldPaint = Paint().apply { color = ContextCompat.getColor(requireContext(), R.color.ttc_gold_bright) }
            private val redPaint = Paint().apply { color = ContextCompat.getColor(requireContext(), R.color.ttc_error) }

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val pos = vh.bindingAdapterPosition
                val ex = boundPrograms.getOrNull(pos) ?: return 0
                // Built-ins: Clone (left) only. Custom: Clone (left) + Delete (right).
                return if (DrillActions.canDelete(ex)) ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                       else ItemTouchHelper.LEFT
            }

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val ex = boundPrograms.getOrNull(vh.bindingAdapterPosition) ?: run {
                    adapter.notifyDataSetChanged(); return
                }
                when (direction) {
                    ItemTouchHelper.LEFT -> cloneDrill(ex)                       // swipe left → Clone
                    ItemTouchHelper.RIGHT -> if (DrillActions.canDelete(ex)) deleteDrill(ex)
                                             else adapter.notifyDataSetChanged()
                }
                // reloadCustomDrills() inside clone/delete rebinds the list; snap the row back meanwhile
                adapter.notifyItemChanged(vh.bindingAdapterPosition)
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val item = vh.itemView
                if (dX < 0) {
                    c.drawRect(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat(), goldPaint)
                } else if (dX > 0) {
                    c.drawRect(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat(), redPaint)
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvDrills)
    }

    /**
     * Binds the RECENT card from the most recent [com.ttcoachai.models.TrainingSession]
     * and splits [allDrills] into (recent, programs) via [DrillActions.partition], updating
     * the adapter with the ALL PROGRAMS list only. Guards against a torn-down view since
     * this runs inside a coroutine.
     */
    private fun bindRecentAndPrograms(allDrills: List<Exercise>) {
        currentDrills = allDrills
        viewLifecycleOwner.lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { trainingDao.getMostRecentSession() }
            if (_binding == null) return@launch
            val (recent, programs) = DrillActions.partition(allDrills, session?.exerciseId)

            val sectionRecent = binding.sectionRecent
            if (recent == null || session == null) {
                sectionRecent.visibility = View.GONE
            } else {
                sectionRecent.visibility = View.VISIBLE
                binding.tvRecentName.text = recent.name
                binding.ivRecentIcon.setImageResource(iconForDrill(recent.id))
                binding.tvRecentDate.text = DateUtils.getRelativeTimeSpanString(
                    session.startTime, System.currentTimeMillis(),
                    DateUtils.DAY_IN_MILLIS
                ).toString().uppercase()

                val pct = if (session.accuracy <= 1f) (session.accuracy * 100).toInt()
                          else session.accuracy.toInt()
                binding.tvRecentAccuracy.text = getString(R.string.drills_recent_accuracy, pct)

                binding.btnRecentContinue.setOnClickListener { onExerciseSelected(recent) }
            }

            boundPrograms = programs
            adapter.updateList(programs)
        }
    }

    private fun iconForDrill(id: String): Int = when (id) {
        "backhand_loop" -> R.drawable.ic_trending_up
        "footwork_drill" -> R.drawable.ic_person
        "multiball_rally" -> R.drawable.ic_alert_circle
        "consistency_challenge" -> R.drawable.ic_check_circle_2
        else -> R.drawable.ic_target
    }

    /**
     * Long-press on a drill row opens the rule-gated action menu (Continue/Edit/
     * Clone/Rename/Delete), filtered per-exercise by [DrillActions]. Built-in
     * presets only offer Continue/Clone; custom drills offer all five.
     */
    private fun showDrillOptions(exercise: Exercise) {
        data class Action(val label: String, val run: () -> Unit)
        val actions = buildList {
            if (DrillActions.canContinue(exercise))
                add(Action(getString(R.string.drill_action_continue)) { onExerciseSelected(exercise) })
            if (DrillActions.canEdit(exercise))
                add(Action(getString(R.string.drill_action_edit)) { launchCustomDrillCalibration() })
            if (DrillActions.canClone(exercise))
                add(Action(getString(R.string.drill_action_clone)) { cloneDrill(exercise) })
            if (DrillActions.canRename(exercise))
                add(Action(getString(R.string.drill_action_rename)) { renameDrill(exercise) })
            if (DrillActions.canDelete(exercise))
                add(Action(getString(R.string.drill_action_delete)) { deleteDrill(exercise) })
        }
        val labels = actions.map { it.label }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(exercise.name)
            .setItems(labels) { _, which -> actions[which].run() }
            .show()
    }

    private fun cloneDrill(source: Exercise) {
        viewLifecycleOwner.lifecycleScope.launch {
            val newType = "${CUSTOM_DRILL_PREFIX}${System.currentTimeMillis()}"
            val copyName = "${source.name} ${getString(R.string.drill_clone_copy_suffix)}"
            val baseTemplate = if (DrillActions.isCustom(source)) {
                withContext(Dispatchers.IO) { customDrillRepo.get(source.id) }?.baseTemplate ?: source.id
            } else source.id
            val entity = CustomDrillEntity(
                drillType = newType,
                name = copyName,
                baseTemplate = baseTemplate,
                createdAtMs = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) { customDrillRepo.save(entity) }
            if (_binding == null) return@launch
            Toast.makeText(requireContext(),
                getString(R.string.drill_clone_toast, copyName), Toast.LENGTH_SHORT).show()
            reloadCustomDrills()
        }
    }

    private fun renameDrill(exercise: Exercise) {
        val input = EditText(requireContext()).apply { setText(exercise.name) }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.drill_rename_title)
            .setView(input)
            .setPositiveButton(R.string.drill_rename_save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val existing = withContext(Dispatchers.IO) { customDrillRepo.get(exercise.id) }
                        if (existing != null) {
                            withContext(Dispatchers.IO) {
                                customDrillRepo.save(existing.copy(name = newName))
                            }
                            if (_binding == null) return@launch
                            reloadCustomDrills()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.drill_cancel, null)
            .show()
    }

    private fun deleteDrill(exercise: Exercise) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.drill_delete_title)
            .setMessage(getString(R.string.drill_delete_message, exercise.name))
            .setPositiveButton(R.string.drill_delete_confirm) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { customDrillRepo.delete(exercise.id) }
                    if (_binding == null) return@launch
                    Toast.makeText(requireContext(),
                        getString(R.string.drill_delete_toast, exercise.name), Toast.LENGTH_SHORT).show()
                    reloadCustomDrills()
                }
            }
            .setNegativeButton(R.string.drill_cancel, null)
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
            if (_binding == null) return@launch
            bindRecentAndPrograms(builtInExercises + customExercises)
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
