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
import com.ttcoachai.shared.feedback.RepClassifier
import com.ttcoachai.shared.feedback.RepVerdict
import com.ttcoachai.shared.feedback.StrokeSnapshotSelector
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Landmark3D
import com.ttcoachai.views.RepStripView

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
    private var verdicts: List<RepVerdict> = emptyList()
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
        this.verdicts = RepClassifier.classify(repLandmarks)
        this.correctionType = type

        // Per-rep four-state classification, mirroring the poses_viewer validity taxonomy:
        // NO_DATA when tracking never produced a usable pose; DISCARDED when the rep-validity
        // pipeline excluded the rep (locomotion/recovery-swing/speed-duration-outlier); else
        // FLAGGED/CLEAN from the recorded flag.
        val repMarks = flags.indices.map { i ->
            val frames = repLandmarks.getOrNull(i)
            val verdict = verdicts.getOrNull(i)
            when {
                frames.isNullOrEmpty() || !StrokeSnapshotSelector.hasUsablePose(frames) ->
                    RepStripView.RepMark.NO_DATA
                verdict == RepVerdict.NO_POSE -> RepStripView.RepMark.NO_DATA
                verdict == RepVerdict.LOCOMOTION ||
                    verdict == RepVerdict.RECOVERY_SWING ||
                    verdict == RepVerdict.SPEED_DURATION_OUTLIER -> RepStripView.RepMark.DISCARDED
                flags[i] -> RepStripView.RepMark.FLAGGED
                else -> RepStripView.RepMark.CLEAN
            }
        }

        var showRepStrip = false
        if (flags.size >= 2) {
            binding.repStrip.setRepMarks(repMarks)
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
            if (repMarks.getOrNull(i) != RepStripView.RepMark.NO_DATA) {
                showRep(i)
            }
        }

        // Default selected rep: last FLAGGED, else last CLEAN, else last DISCARDED, else no snapshot.
        val defaultIndex = repMarks.indices.lastOrNull { i -> repMarks[i] == RepStripView.RepMark.FLAGGED }
            ?: repMarks.indices.lastOrNull { i -> repMarks[i] == RepStripView.RepMark.CLEAN }
            ?: repMarks.indices.lastOrNull { i -> repMarks[i] == RepStripView.RepMark.DISCARDED }

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
        val frameIdx = StrokeSnapshotSelector.snapshotFrameFor(correctionType, frames)
        if (frameIdx < 0) return

        binding.poseSnapshot.setSnapshot(frames[frameIdx], correctionType)
        val verdict = verdicts.getOrNull(index)
        val statusRes = when (verdict) {
            RepVerdict.RECOVERY_SWING -> R.string.feedback_rep_discarded_recovery
            RepVerdict.SPEED_DURATION_OUTLIER -> R.string.feedback_rep_discarded_outlier
            RepVerdict.LOCOMOTION -> R.string.feedback_rep_discarded_locomotion
            else -> {
                val flagged = flags.getOrNull(index) == true
                if (flagged) R.string.feedback_snapshot_rep_flagged else R.string.feedback_snapshot_rep_clean
            }
        }
        // Caption names the rendered moment: contact frame for contact-anchored metrics,
        // follow-through frame for FOLLOW_THROUGH, wrist-speed peak otherwise
        // (must match StrokeSnapshotSelector.snapshotFrameFor's dispatch).
        val captionRes = when (correctionType) {
            CorrectionType.WRIST,
            CorrectionType.CONTACT_HEIGHT,
            CorrectionType.ELBOW_POSITION,
            CorrectionType.BODY_ROTATION -> R.string.feedback_snapshot_caption_rep_contact
            CorrectionType.FOLLOW_THROUGH -> R.string.feedback_snapshot_caption_rep_follow
            else -> R.string.feedback_snapshot_caption_rep
        }
        binding.tvSnapshotCaption.text =
            "${getString(captionRes, index + 1)} · ${getString(statusRes)}"
        binding.cardPoseSnapshot.visibility = View.VISIBLE
        binding.repStrip.setSelected(index)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
