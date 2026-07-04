package com.ttcoachai.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ttcoachai.R
import com.ttcoachai.databinding.SheetFeedbackExplanationBinding
import com.ttcoachai.managers.TrainingStateManager
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.feedback.FeedbackExplanationCatalog
import com.ttcoachai.shared.feedback.StrokeSnapshotSelector
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Landmark3D

/**
 * Tap-to-explain bottom sheet for a real-time feedback row: shows why a correction
 * type was flagged (catalog content from [FeedbackExplanationCatalog]) plus the
 * player's own recent violation messages for that type this session.
 */
class FeedbackExplanationSheet : BottomSheetDialogFragment() {

    private var _binding: SheetFeedbackExplanationBinding? = null
    private val binding get() = _binding!!

    // Per-rep pose/flag state for the tappable rep strip -> snapshot wiring (see [showRep]).
    private var repLandmarks: List<List<List<Landmark3D>>> = emptyList()
    private var flags: List<Boolean> = emptyList()
    private var correctionType: CorrectionType = CorrectionType.GENERAL

    companion object {
        const val TAG = "FeedbackExplanationSheet"
        private const val ARG_TYPE = "correction_type"
        private const val ARG_COUNT = "flagged_count"
        private const val ARG_LANG = "lang"
        private const val ARG_MESSAGES = "recent_messages"

        fun newInstance(
            type: CorrectionType,
            flaggedCount: Int,
            lang: FeedbackLang,
            recentMessages: List<String>
        ) = FeedbackExplanationSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_TYPE, type.name)
                putInt(ARG_COUNT, flaggedCount)
                putString(ARG_LANG, lang.name)
                putStringArrayList(ARG_MESSAGES, ArrayList(recentMessages))
            }
        }
    }

    override fun getTheme() = R.style.ThemeOverlay_TTC_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetFeedbackExplanationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val type = CorrectionType.valueOf(args.getString(ARG_TYPE)!!)
        val flaggedCount = args.getInt(ARG_COUNT)
        val lang = FeedbackLang.valueOf(args.getString(ARG_LANG)!!)
        val recentMessages = args.getStringArrayList(ARG_MESSAGES).orEmpty()

        val explanation = FeedbackExplanationCatalog.explain(type, lang)

        binding.tvExplanationTitle.text = explanation.title
        binding.tvExplanationFlaggedCount.text = getString(
            R.string.feedback_explanation_flagged_count,
            flaggedCount
        )
        binding.tvWhatItChecks.text = explanation.whatItChecks
        binding.tvWhyItMatters.text = explanation.whyItMatters
        binding.tvHowToFix.text = explanation.howToFix

        if (recentMessages.isEmpty()) {
            binding.groupRecentObservations.visibility = View.GONE
        } else {
            binding.groupRecentObservations.visibility = View.VISIBLE
            binding.tvRecentObservations.text = recentMessages.joinToString("\n") { "• $it" }
        }

        // Landmarks are per-process capture state (last 10 reps), not primitives suitable for a
        // Fragment Bundle — read them straight from the singleton here rather than threading them
        // through newInstance()/arguments.
        val stateManager = TrainingStateManager.getInstance(requireContext())
        this.repLandmarks = stateManager.getRepStrokeLandmarks()
        this.flags = stateManager.getRepFlagsFor(type)
        this.correctionType = type

        var showRepStrip = false
        if (flags.size >= 2) {
            binding.repStrip.setReps(flags)
            binding.tvRepStripLabel.text = getString(R.string.feedback_rep_strip_label)
            binding.tvRepStripLegend.text = getString(R.string.feedback_rep_strip_legend)
            binding.tvRepStripLabel.visibility = View.VISIBLE
            binding.repStrip.visibility = View.VISIBLE
            binding.tvRepStripLegend.visibility = View.VISIBLE
            showRepStrip = true
        } else {
            binding.tvRepStripLabel.visibility = View.GONE
            binding.repStrip.visibility = View.GONE
            binding.tvRepStripLegend.visibility = View.GONE
        }
        binding.repStrip.onRepClick = { i ->
            if (repLandmarks.getOrNull(i)?.isNotEmpty() == true) {
                showRep(i)
            }
        }

        // Default selected rep: last flagged rep with a captured pose, else last rep with any
        // captured pose, else no snapshot at all.
        val defaultIndex = flags.indices.lastOrNull { i -> flags[i] && repLandmarks.getOrNull(i)?.isNotEmpty() == true }
            ?: repLandmarks.indices.lastOrNull { i -> repLandmarks[i].isNotEmpty() }

        val showSnapshot = defaultIndex != null
        if (defaultIndex != null) {
            showRep(defaultIndex)
        } else {
            binding.cardPoseSnapshot.visibility = View.GONE
        }

        binding.groupStrokeVisual.visibility =
            if (showSnapshot || showRepStrip) View.VISIBLE else View.GONE

        binding.btnExplanationClose.setOnClickListener { dismiss() }
    }

    /**
     * Renders rep [index]'s captured pose in the snapshot card and reflects the selection on the
     * rep strip. Assumes `repLandmarks[index]` is non-empty (callers guard this).
     */
    private fun showRep(index: Int) {
        val frames = repLandmarks[index]
        val frameIdx = StrokeSnapshotSelector.bestSnapshotFrameIndex(frames)
        if (frameIdx < 0) return

        binding.poseSnapshot.setSnapshot(frames[frameIdx], correctionType)
        val flagged = flags.getOrNull(index) == true
        val statusRes = if (flagged) {
            R.string.feedback_snapshot_rep_flagged
        } else {
            R.string.feedback_snapshot_rep_clean
        }
        binding.tvSnapshotCaption.text =
            "${getString(R.string.feedback_snapshot_caption_rep, index + 1)} · ${getString(statusRes)}"
        binding.cardPoseSnapshot.visibility = View.VISIBLE
        binding.repStrip.setSelected(index)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
