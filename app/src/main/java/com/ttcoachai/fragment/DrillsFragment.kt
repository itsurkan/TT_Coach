package com.ttcoachai.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import androidx.recyclerview.widget.RecyclerView
import com.ttcoachai.Exercise
import com.ttcoachai.ExerciseAdapter
import com.ttcoachai.R
import com.ttcoachai.TrainingActivity
import com.ttcoachai.databinding.FragmentDrillsBinding
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.models.CustomDrillEntity
import com.ttcoachai.repository.CustomDrillRepository
import com.ttcoachai.ui.ExerciseEditorActivity
import com.google.firebase.auth.FirebaseAuth
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

    private val communityDrillRepo by lazy { com.ttcoachai.repository.CommunityDrillRepository() }

    private val trainingDao by lazy {
        AppDatabase.getDatabase(requireContext()).trainingDao()
    }

    /** Merged built-in + custom list, reused by the drill-options menu and swipe actions. */
    var currentDrills: List<Exercise> = emptyList()
        private set

    /** Locked (not-yet-available) drills are hidden until the user expands the footer toggle. */
    private var lockedExpanded = false
    private var programsUnlocked: List<Exercise> = emptyList()
    private var programsLocked: List<Exercise> = emptyList()

    private val exerciseEditorLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                reloadCustomDrills()
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
                id = "forehand_drive_general",
                name = getString(R.string.exercise_forehand_general_name),
                description = getString(R.string.exercise_forehand_general_desc),
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
            exercises = emptyList(),
            onExerciseClick = { onExerciseSelected(it) },
            onExerciseLongClick = { showDrillOptions(it) },
            onCloneClick = { cloneDrill(it) },
            onDeleteClick = { deleteDrill(it) },
            onToggleLocked = { toggleLocked() }
        )
        binding.rvDrills.adapter = adapter

        // If custom drills were already loaded this session, render the COMPLETE list
        // (built-ins + customs + footer) synchronously on the first frame. Without a
        // cache we render nothing yet and let reloadCustomDrills() do a single combined
        // render after the DB read — so custom rows never pop in after the built-ins.
        cachedCustomExercises?.let { cached ->
            customExercises = cached
            bindRecentAndPrograms(builtInExercises + cached)
        }

        // Close any open swipe row when the list scrolls.
        binding.rvDrills.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0 || dx != 0) adapter.closeOpenRow()
            }
        })

        binding.fabAddDrill.setOnClickListener {
            exerciseEditorLauncher.launch(ExerciseEditorActivity.newIntentNew(requireContext()))
        }
    }

    /**
     * Renders the drill list ([allDrills], split into unlocked + locked for the footer
     * toggle) synchronously, then fills the RECENT card asynchronously from the most
     * recent [com.ttcoachai.models.TrainingSession]. The list render is deliberately not
     * gated on the recent-session query so items/footer don't pop in one pass later; the
     * async card block guards against a torn-down view.
     */
    private fun bindRecentAndPrograms(allDrills: List<Exercise>) {
        currentDrills = allDrills

        // Program list + locked footer render immediately — never gated on the
        // recent-session DB query, so the list doesn't pop in item-by-item.
        val (locked, unlocked) = allDrills.partition { it.isLocked }
        programsUnlocked = unlocked
        programsLocked = locked
        renderPrograms()

        // The RECENT card fills in asynchronously; its space is already reserved
        // (section_recent is INVISIBLE, not GONE) so this never shifts the list.
        viewLifecycleOwner.lifecycleScope.launch {
            val session = FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                withContext(Dispatchers.IO) { trainingDao.getMostRecentSessionForUser(uid) }
            }
            if (_binding == null) return@launch
            val recent = DrillActions.partition(allDrills, session?.exerciseId).first

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
        }
    }

    /** Rebuilds the visible drill list: unlocked always, locked only when expanded. */
    private fun renderPrograms() {
        val visible = if (lockedExpanded) programsUnlocked + programsLocked else programsUnlocked
        adapter.setData(visible, hasLocked = programsLocked.isNotEmpty(), expanded = lockedExpanded)
    }

    private fun toggleLocked() {
        lockedExpanded = !lockedExpanded
        adapter.closeOpenRow()
        renderPrograms()
    }

    private fun iconForDrill(id: String): Int = when (id) {
        "forehand_drive", "forehand_andrii", "forehand_drive_general" -> R.drawable.ic_skill_forehand
        "backhand_loop" -> R.drawable.ic_skill_backhand
        "serve_practice" -> R.drawable.ic_skill_topspin
        "footwork_drill" -> R.drawable.ic_skill_footwork
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
        val view = layoutInflater.inflate(R.layout.dialog_drill_menu, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(view).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val rowContinue = view.findViewById<View>(R.id.rowContinue)
        val rowEdit = view.findViewById<View>(R.id.rowEdit)
        val rowClone = view.findViewById<View>(R.id.rowClone)
        val rowRename = view.findViewById<View>(R.id.rowRename)
        val rowShare = view.findViewById<View>(R.id.rowShare)
        val rowUnshare = view.findViewById<View>(R.id.rowUnshare)
        val rowDelete = view.findViewById<View>(R.id.rowDelete)

        fun wire(row: View, enabled: Boolean, run: () -> Unit) {
            if (enabled) row.setOnClickListener { dialog.dismiss(); run() }
            else row.visibility = View.GONE
        }
        val isShared = exercise.sharedCommunityId != null
        wire(rowContinue, DrillActions.canContinue(exercise)) { onExerciseSelected(exercise) }
        wire(rowEdit, DrillActions.canEdit(exercise)) {
            exerciseEditorLauncher.launch(
                ExerciseEditorActivity.newIntentEdit(requireContext(), exercise.id)
            )
        }
        wire(rowClone, DrillActions.canClone(exercise)) { cloneDrill(exercise) }
        wire(rowRename, DrillActions.canRename(exercise)) { renameDrill(exercise) }
        wire(rowShare, DrillActions.canShareToCommunity(exercise, isShared)) { shareToCommunity(exercise) }
        wire(rowUnshare, DrillActions.canUnshare(exercise, isShared)) { unshareFromCommunity(exercise) }
        wire(rowDelete, DrillActions.canDelete(exercise)) { deleteDrill(exercise) }

        // Dividers only separate visible groups (continue | edit/clone/rename/share/unshare | delete).
        val midVisible = rowEdit.visibility == View.VISIBLE ||
            rowClone.visibility == View.VISIBLE ||
            rowRename.visibility == View.VISIBLE ||
            rowShare.visibility == View.VISIBLE ||
            rowUnshare.visibility == View.VISIBLE
        view.findViewById<View>(R.id.divider1).visibility =
            if (rowContinue.visibility == View.VISIBLE && midVisible) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.divider2).visibility =
            if (midVisible && rowDelete.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        dialog.show()
    }

    private fun cloneDrill(source: Exercise) {
        viewLifecycleOwner.lifecycleScope.launch {
            val baseTemplate = if (DrillActions.isCustom(source)) {
                withContext(Dispatchers.IO) { customDrillRepo.get(source.id) }?.baseTemplate ?: source.id
            } else source.id
            if (_binding == null) return@launch
            exerciseEditorLauncher.launch(
                ExerciseEditorActivity.newIntentClone(requireContext(), source.id, source.name, baseTemplate)
            )
        }
    }

    private fun renameDrill(exercise: Exercise) {
        val input = EditText(requireContext()).apply { setText(exercise.name) }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
        com.ttcoachai.ui.dialogs.ConfirmDialog.show(
            context = requireContext(),
            iconRes = R.drawable.ic_trash,
            title = getString(R.string.drill_delete_title),
            body = getString(R.string.drill_delete_message, exercise.name),
            confirmLabel = getString(R.string.drill_delete_confirm),
            cancelLabel = getString(R.string.drill_cancel),
            destructive = true
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { customDrillRepo.delete(exercise.id) }
                if (_binding == null) return@launch
                Toast.makeText(requireContext(),
                    getString(R.string.drill_delete_toast, exercise.name), Toast.LENGTH_SHORT).show()
                reloadCustomDrills()
            }
        }
    }

    private fun shareToCommunity(exercise: Exercise) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.community_sign_in_required_title)
                .setMessage(R.string.community_sign_in_required_message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val entity = withContext(Dispatchers.IO) { customDrillRepo.get(exercise.id) } ?: return@launch
            val drill = com.ttcoachai.models.CommunityDrillMapper.fromCustomDrill(
                entity,
                user.uid,
                user.displayName ?: getString(R.string.community_anonymous_creator),
                user.photoUrl?.toString() ?: "",
                System.currentTimeMillis()
            )
            val result = withContext(Dispatchers.IO) { communityDrillRepo.publish(drill) }
            if (_binding == null) return@launch
            if (result.isSuccess) {
                val id = result.getOrThrow()
                withContext(Dispatchers.IO) { customDrillRepo.save(entity.copy(sharedCommunityId = id)) }
                if (_binding == null) return@launch
                reloadCustomDrills()
                Toast.makeText(requireContext(), R.string.drill_share_success_toast, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.drill_share_error_toast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unshareFromCommunity(exercise: Exercise) {
        val communityId = exercise.sharedCommunityId ?: return
        com.ttcoachai.ui.dialogs.ConfirmDialog.show(
            context = requireContext(),
            iconRes = R.drawable.ic_trash,
            title = getString(R.string.drill_unshare_confirm_title),
            body = getString(R.string.drill_unshare_confirm_message, exercise.name),
            confirmLabel = getString(R.string.drill_unshare_confirm_button),
            cancelLabel = getString(R.string.drill_cancel),
            destructive = true
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val entity = withContext(Dispatchers.IO) { customDrillRepo.get(exercise.id) } ?: return@launch
                val result = withContext(Dispatchers.IO) { communityDrillRepo.unshare(communityId, uid) }
                if (_binding == null) return@launch
                if (result.isSuccess) {
                    withContext(Dispatchers.IO) { customDrillRepo.save(entity.copy(sharedCommunityId = null)) }
                    if (_binding == null) return@launch
                    reloadCustomDrills()
                    Toast.makeText(requireContext(), R.string.drill_unshare_success_toast, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.drill_unshare_error_toast, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun reloadCustomDrills() {
        if (_binding == null) return
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) { customDrillRepo.getAll() }
            customExercises = rows.map { it.toExercise() }
            cachedCustomExercises = customExercises
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
        category = getString(R.string.drills_custom_category),
        sharedCommunityId = sharedCommunityId
    )

    private fun onExerciseSelected(exercise: Exercise) {
        if (exercise.isLocked) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.exercise_locked_title)
                .setMessage(R.string.exercise_locked_message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
            return
        }

        if (exercise.id.startsWith(CUSTOM_DRILL_PREFIX)) {
            viewLifecycleOwner.lifecycleScope.launch {
                val entity = withContext(Dispatchers.IO) { customDrillRepo.get(exercise.id) }
                if (_binding == null) return@launch
                val intent = Intent(requireContext(), TrainingActivity::class.java).apply {
                    putExtra("EXERCISE_ID", exercise.id)
                    putExtra("EXERCISE_NAME", exercise.name)
                    putExtra("USE_VIDEO", exercise.useVideo)
                    putExtra("PER_PHASE_TARGETS_JSON", entity?.perPhaseTargetsJson ?: "")
                }
                startActivity(intent)
            }
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

        /**
         * Session-level cache of custom drills so a re-opened (recreated) fragment can
         * render the COMPLETE list — built-ins + customs + footer — on the first frame,
         * instead of showing built-ins first and popping the custom rows in after the DB
         * read. Refreshed on every [reloadCustomDrills].
         */
        private var cachedCustomExercises: List<Exercise>? = null
    }
}
