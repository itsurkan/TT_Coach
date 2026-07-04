package com.ttcoachai.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ttcoachai.R
import com.ttcoachai.databinding.SheetFeedbackExplanationBinding
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.feedback.FeedbackExplanationCatalog
import com.ttcoachai.shared.models.CorrectionType

/**
 * Tap-to-explain bottom sheet for a real-time feedback row: shows why a correction
 * type was flagged (catalog content from [FeedbackExplanationCatalog]) plus the
 * player's own recent violation messages for that type this session.
 */
class FeedbackExplanationSheet : BottomSheetDialogFragment() {

    private var _binding: SheetFeedbackExplanationBinding? = null
    private val binding get() = _binding!!

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

        binding.btnExplanationClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
