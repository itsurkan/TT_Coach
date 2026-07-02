package com.ttcoachai.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ttcoachai.R
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.databinding.FragmentSessionReviewBinding
import com.ttcoachai.databinding.ItemFocusAreaRowBinding
import com.ttcoachai.models.SessionAnalyticsEntity
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.shared.analysis.FocusArea
import com.ttcoachai.shared.analysis.SessionAnalyticsBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class SessionReviewFragment : Fragment() {

    private var _binding: FragmentSessionReviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSessionReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnOverview.setOnClickListener { findNavController().navigateUp() }
        binding.btnTrainAgain.setOnClickListener { findNavController().navigateUp() } // stub: routes to overview this slice
        binding.tvViewAllFocus.setOnClickListener { /* stub this slice */ }

        val sessionId = arguments?.getString("sessionId").orEmpty()
        val app = requireActivity().application as TTCoachApplication

        viewLifecycleOwner.lifecycleScope.launch {
            val session = app.database.trainingDao().getSessionById(sessionId)
            val analytics = app.database.sessionAnalyticsDao().getForSession(sessionId)
            bind(session, analytics)
        }
    }

    private fun bind(session: TrainingSession?, analytics: SessionAnalyticsEntity?) {
        if (session == null) {
            binding.tvDrillName.text = ""
            return
        }
        binding.tvDrillName.text = session.exerciseName
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startTime))
        binding.tvWhen.text = "$time · ${session.durationSeconds / 60} min"
        binding.tvKpiAccuracy.text = "${session.getAccuracyPercent()}%"
        binding.tvKpiStrokes.text = session.strokeCount.toString()
        binding.tvKpiDuration.text = "${session.durationSeconds / 60}m"

        val totalMin = session.durationSeconds / 60
        binding.tvXMid.text = "${totalMin / 2} min"
        binding.tvXEnd.text = "$totalMin min"

        if (analytics == null) {
            binding.chartAccuracy.visibility = View.INVISIBLE
            binding.barShotQuality.visibility = View.INVISIBLE
            binding.tvViewAllFocus.visibility = View.GONE
            binding.tvFocusEmpty.visibility = View.VISIBLE
            binding.tvPeakPill.text = getString(R.string.review_no_analytics)
            binding.tvSummary.text = ""
            return
        }

        val timeline = analytics.timeline()
        binding.chartAccuracy.setData(timeline, analytics.peakBucketIndex)
        binding.tvPeakPill.text = getString(R.string.review_peak_pill, analytics.peakAccuracy.roundToInt())

        val total = analytics.cleanCount + analytics.errorCount
        binding.tvShotQualityTitle.text = getString(R.string.review_shot_quality_title, total)
        binding.barShotQuality.setCounts(analytics.cleanCount, analytics.errorCount)
        binding.tvLegendClean.text = getString(R.string.review_legend_clean, analytics.cleanCount)
        binding.tvLegendError.text = getString(R.string.review_legend_error, analytics.errorCount)

        bindFocusAreas(analytics.focusAreas())
        binding.tvSummary.text = analytics.summaryText
    }

    private fun bindFocusAreas(areas: List<FocusArea>) {
        binding.focusContainer.removeAllViews()
        if (areas.isEmpty()) {
            binding.tvFocusEmpty.visibility = View.VISIBLE
            return
        }
        binding.tvFocusEmpty.visibility = View.GONE
        val maxCount = areas.first().count.coerceAtLeast(1)
        areas.take(3).forEachIndexed { index, area ->
            val row = ItemFocusAreaRowBinding.inflate(layoutInflater, binding.focusContainer, false)
            val key = SessionAnalyticsBuilder.displayNameKey(area.type)
            val nameRes = resources.getIdentifier(key, "string", requireContext().packageName)
            row.tvFocusName.text = if (nameRes != 0) getString(nameRes) else area.type.name
            row.tvFocusCount.text = getString(R.string.review_focus_count, area.count)
            row.barFocus.max = maxCount
            row.barFocus.progress = area.count
            row.chipTopFocus.visibility = if (index == 0) View.VISIBLE else View.GONE
            binding.focusContainer.addView(row.root)
        }
        if (areas.size > 3) {
            binding.tvViewAllFocus.visibility = View.VISIBLE
            binding.tvViewAllFocus.text = getString(R.string.review_view_all_focus, areas.size)
        } else {
            binding.tvViewAllFocus.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
