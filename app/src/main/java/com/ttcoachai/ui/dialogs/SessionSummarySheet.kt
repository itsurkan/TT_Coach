package com.ttcoachai.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ttcoachai.R
import com.ttcoachai.databinding.SheetSessionSummaryBinding
import com.ttcoachai.util.SessionStatsFormatter

/** Dialog 14c — session-summary bottom sheet. No AI-coach note, stats only (per spec). */
class SessionSummarySheet : BottomSheetDialogFragment() {

    private var _binding: SheetSessionSummaryBinding? = null
    private val binding get() = _binding!!

    var onContinue: (() -> Unit)? = null
    var onFinish: (() -> Unit)? = null

    companion object {
        const val TAG = "SessionSummarySheet"
        private const val ARG_DRILL_NAME = "drill_name"
        private const val ARG_DURATION = "duration_seconds"
        private const val ARG_STROKES = "stroke_count"
        private const val ARG_CLEAN_COUNT = "clean_count"
        private const val ARG_ACCURACY = "accuracy_percent"

        fun newInstance(
            drillName: String,
            durationSeconds: Int,
            strokeCount: Int,
            cleanCount: Int,
            accuracyPercent: Int
        ) = SessionSummarySheet().apply {
            arguments = Bundle().apply {
                putString(ARG_DRILL_NAME, drillName)
                putInt(ARG_DURATION, durationSeconds)
                putInt(ARG_STROKES, strokeCount)
                putInt(ARG_CLEAN_COUNT, cleanCount)
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
        _binding = SheetSessionSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val drillName = args.getString(ARG_DRILL_NAME).orEmpty()
        val durationSeconds = args.getInt(ARG_DURATION)
        val strokeCount = args.getInt(ARG_STROKES)
        val cleanCount = args.getInt(ARG_CLEAN_COUNT)
        val accuracyPercent = args.getInt(ARG_ACCURACY)
        val cleanPercent = SessionStatsFormatter.cleanPercent(cleanCount, strokeCount)

        binding.tvSummaryMeta.text = getString(
            R.string.session_summary_meta,
            drillName,
            SessionStatsFormatter.formatDuration(durationSeconds)
        )

        StatTileBinder.bind(
            tileRoot = binding.tileStrokes.root,
            iconRes = R.drawable.ic_zap,
            value = strokeCount.toString(),
            label = getString(R.string.stat_strokes),
            valueTextSizeSp = 20f
        )
        StatTileBinder.bind(
            tileRoot = binding.tileClean.root,
            iconRes = R.drawable.ic_check_circle_2,
            value = cleanCount.toString(),
            label = getString(R.string.stat_clean, cleanPercent),
            valueColorRes = R.color.ttc_success,
            iconTintRes = R.color.ttc_success,
            valueTextSizeSp = 20f
        )
        StatTileBinder.bind(
            tileRoot = binding.tileAccuracy.root,
            iconRes = R.drawable.ic_target,
            value = "$accuracyPercent%",
            label = getString(R.string.stat_accuracy),
            valueColorRes = R.color.ttc_gold_bright,
            iconTintRes = R.color.ttc_gold_bright,
            valueTextSizeSp = 20f
        )

        binding.btnSummaryContinue.setOnClickListener {
            dismiss()
            onContinue?.invoke()
        }
        binding.btnSummaryFinish.setOnClickListener {
            dismiss()
            onFinish?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
