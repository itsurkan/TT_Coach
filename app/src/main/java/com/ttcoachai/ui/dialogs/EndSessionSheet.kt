package com.ttcoachai.ui.dialogs

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ttcoachai.R
import com.ttcoachai.databinding.SheetEndSessionBinding
import com.ttcoachai.util.SessionStatsFormatter

/**
 * Dialog 14b — end-session bottom sheet. Non-destructive dismiss: tapping outside or
 * back-press invokes onKeepTraining, never onDiscard/onFinishSave silently.
 */
class EndSessionSheet : BottomSheetDialogFragment() {

    private var _binding: SheetEndSessionBinding? = null
    private val binding get() = _binding!!

    private var hasHandledExplicitAction = false

    var onFinishSave: (() -> Unit)? = null
    var onKeepTraining: (() -> Unit)? = null
    var onDiscard: (() -> Unit)? = null

    companion object {
        const val TAG = "EndSessionSheet"
        private const val ARG_DURATION = "duration_seconds"
        private const val ARG_STROKES = "stroke_count"
        private const val ARG_ACCURACY = "accuracy_percent"

        fun newInstance(
            durationSeconds: Int,
            strokeCount: Int,
            accuracyPercent: Int
        ) = EndSessionSheet().apply {
            arguments = Bundle().apply {
                putInt(ARG_DURATION, durationSeconds)
                putInt(ARG_STROKES, strokeCount)
                putInt(ARG_ACCURACY, accuracyPercent)
            }
        }
    }

    override fun getTheme() = R.style.ThemeOverlay_TTC_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetEndSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val durationSeconds = args.getInt(ARG_DURATION)
        val strokeCount = args.getInt(ARG_STROKES)
        val accuracyPercent = args.getInt(ARG_ACCURACY)

        binding.tvEndSessionBody.text = getString(
            R.string.end_session_body,
            SessionStatsFormatter.formatDuration(durationSeconds)
        )

        StatTileBinder.bind(
            tileRoot = binding.tileDuration.root,
            iconRes = R.drawable.ic_activity,
            value = SessionStatsFormatter.formatDuration(durationSeconds),
            label = getString(R.string.stat_duration),
            valueTextSizeSp = 16f
        )
        StatTileBinder.bind(
            tileRoot = binding.tileStrokes.root,
            iconRes = R.drawable.ic_zap,
            value = strokeCount.toString(),
            label = getString(R.string.stat_strokes),
            valueTextSizeSp = 16f
        )
        StatTileBinder.bind(
            tileRoot = binding.tileAccuracy.root,
            iconRes = R.drawable.ic_target,
            value = "$accuracyPercent%",
            label = getString(R.string.stat_accuracy),
            valueColorRes = R.color.ttc_gold_bright,
            iconTintRes = R.color.ttc_gold_bright,
            valueTextSizeSp = 16f
        )

        binding.btnEndSessionFinishSave.setOnClickListener { handleExplicit { onFinishSave?.invoke() } }
        binding.btnEndSessionKeep.setOnClickListener { handleExplicit { onKeepTraining?.invoke() } }
        binding.btnEndSessionDiscard.setOnClickListener { handleExplicit { onDiscard?.invoke() } }
    }

    private fun handleExplicit(action: () -> Unit) {
        hasHandledExplicitAction = true
        dismiss()
        action()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Non-destructive dismiss (outside tap / back press) is treated as Keep Training.
        // Explicit button clicks route through handleExplicit(), which sets the flag before
        // calling dismiss(), so this branch only fires for outside-tap/back-press dismissal.
        if (!hasHandledExplicitAction) {
            onKeepTraining?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
