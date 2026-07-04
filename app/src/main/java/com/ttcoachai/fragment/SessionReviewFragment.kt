package com.ttcoachai.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ttcoachai.LocaleHelper
import com.ttcoachai.R
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.TrainingActivity
import com.ttcoachai.databinding.FragmentSessionReviewBinding
import com.ttcoachai.databinding.ItemFocusAreaRowBinding
import com.ttcoachai.managers.TrainingStateManager
import com.ttcoachai.models.SessionAnalyticsEntity
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.shared.analysis.FocusArea
import com.ttcoachai.shared.analysis.SessionAnalyticsBuilder
import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.feedback.LiveFeedbackCatalog
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.ui.dialogs.FeedbackExplanationSheet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class SessionReviewFragment : Fragment() {

    private var _binding: FragmentSessionReviewBinding? = null
    private val binding get() = _binding!!

    /** Cached once the session loads, so btnTrainAgain can re-launch the same drill. */
    private var loadedSession: TrainingSession? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSessionReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener {
            // After the training-finish handoff this screen can be the only back-stack entry;
            // navigateUp() would then be a no-op, so fall back to the sessions overview.
            if (!findNavController().navigateUp()) navigateToOverview()
        }
        binding.btnOverview.setOnClickListener { navigateToOverview() }
        binding.btnTrainAgain.setOnClickListener { onTrainAgain() }
        // No dedicated "all focus areas" screen exists; the feedback/corrections screen is
        // the closest existing destination that surfaces per-correction focus data, so send
        // the user there rather than leaving the affordance dead.
        binding.tvViewAllFocus.setOnClickListener {
            findNavController().navigate(R.id.action_review_to_feedback)
        }

        val sessionId = arguments?.getString("sessionId").orEmpty()
        val app = requireActivity().application as TTCoachApplication

        viewLifecycleOwner.lifecycleScope.launch {
            val session = app.database.trainingDao().getSessionById(sessionId)
            val analytics = app.database.sessionAnalyticsDao().getForSession(sessionId)
            loadedSession = session
            bind(session, analytics)
        }
    }

    /**
     * Re-launches the reviewed session's drill via [TrainingActivity], mirroring
     * DrillsFragment.onExerciseSelected. TrainingSession doesn't persist the `useVideo`
     * flag, so it's omitted — TrainingActivity defaults USE_VIDEO to false, matching the
     * common (non-video) drill case. Falls back to the Drills tab if the session hasn't
     * loaded yet (e.g. tapped before the DB query completes).
     */
    private fun onTrainAgain() {
        val session = loadedSession
        if (session == null || session.exerciseId.isBlank()) {
            findNavController().navigate(R.id.navigation_drills)
            return
        }
        val intent = Intent(requireContext(), TrainingActivity::class.java).apply {
            putExtra("EXERCISE_ID", session.exerciseId)
            putExtra("EXERCISE_NAME", session.exerciseName)
        }
        startActivity(intent)
    }

    private fun bind(session: TrainingSession?, analytics: SessionAnalyticsEntity?) {
        if (session == null) {
            binding.tvDrillName.text = ""
            return
        }
        binding.tvDrillName.text = session.exerciseName
        val locale = resources.configuration.locales[0]
        val time = SimpleDateFormat("HH:mm", locale).format(Date(session.startTime))
        val zone = ZoneId.systemDefault()
        val sessionDate = Instant.ofEpochMilli(session.startTime).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val relativeDatePrefix = when {
            sessionDate.isEqual(today) -> getString(R.string.date_today)
            sessionDate.isEqual(today.minusDays(1)) -> getString(R.string.date_yesterday)
            else -> SimpleDateFormat("d MMM", locale).format(Date(session.startTime))
        }
        binding.tvWhen.text = getString(
            R.string.review_when_meta,
            relativeDatePrefix,
            time,
            session.durationSeconds / 60
        )
        binding.tvKpiAccuracy.text = "${session.getAccuracyPercent()}%"
        binding.tvKpiStrokes.text = session.strokeCount.toString()
        binding.tvKpiDuration.text = getString(R.string.review_kpi_duration_value, session.durationSeconds / 60)

        val totalMin = session.durationSeconds / 60
        binding.tvXMid.text = getString(R.string.review_x_axis_min, totalMin / 2)
        binding.tvXEnd.text = getString(R.string.review_x_axis_min, totalMin)

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

        val focusAreas = analytics.focusAreas()
        bindFocusAreas(focusAreas)
        binding.tvSummary.text = buildSummaryText(timeline, analytics.peakAccuracy, focusAreas, analytics.summaryText)
    }

    private fun currentLang(): FeedbackLang =
        if (LocaleHelper.getSavedLanguage(requireContext()) == "uk") FeedbackLang.UA else FeedbackLang.EN

    private fun focusDisplayName(type: CorrectionType): String {
        val key = SessionAnalyticsBuilder.displayNameKey(type)
        val nameRes = resources.getIdentifier(key, "string", requireContext().packageName)
        return if (nameRes != 0) getString(nameRes) else type.name
    }

    /**
     * Regenerates the coach summary from persisted entity data at render time so it renders in
     * the active app locale, mirroring SessionAnalyticsBuilder.buildSummary's exact trend
     * thresholds (delta = last - first bucket, rounded). analytics.summaryText (EN, persisted at
     * save time) is kept only as the empty-timeline fallback.
     */
    private fun buildSummaryText(
        timeline: List<Float>,
        peakAccuracy: Float,
        focusAreas: List<FocusArea>,
        fallback: String,
    ): String {
        if (timeline.isEmpty()) return fallback
        val peakPct = peakAccuracy.roundToInt()
        val delta = (timeline.last() - timeline.first()).roundToInt()
        val trendRes = when {
            delta > 0 -> R.string.review_summary_improved
            delta < 0 -> R.string.review_summary_declined
            else -> R.string.review_summary_steady
        }
        val focusPhrase = focusAreas.firstOrNull()
            ?.let { getString(R.string.review_summary_focus, focusDisplayName(it.type)) }
            ?: ""
        return getString(trendRes, peakPct) + focusPhrase
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
            row.tvFocusName.text = focusDisplayName(area.type)
            row.tvFocusCount.text = getString(R.string.review_focus_count, area.count)
            row.barFocus.max = maxCount
            row.barFocus.progress = area.count
            row.chipTopFocus.visibility = if (index == 0) View.VISIBLE else View.GONE
            row.root.setOnClickListener { showFocusAreaExplanation(area) }
            binding.focusContainer.addView(row.root)
        }
        if (areas.size > 3) {
            binding.tvViewAllFocus.visibility = View.VISIBLE
            binding.tvViewAllFocus.text = getString(R.string.review_view_all_focus, areas.size)
        } else {
            binding.tvViewAllFocus.visibility = View.GONE
        }
    }

    /**
     * Same tap-to-explain flow as TrainingUIController.showFeedbackExplanation on the live
     * training screen. TrainingStateManager is an in-memory singleton: right after a session
     * ends this still holds that session's reps (desired); for older sessions opened from
     * history it may reflect a different session's data — acceptable, not gated here.
     */
    private fun showFocusAreaExplanation(area: FocusArea) {
        val lang = currentLang()
        val stateManager = TrainingStateManager.getInstance(requireContext())
        val recentMessages = stateManager.getRecentMessagesFor(area.type).map { message ->
            LiveFeedbackCatalog.resolve(message, short = false, lang = lang) ?: message
        }
        val sheet = FeedbackExplanationSheet.newInstance(
            type = area.type,
            flaggedCount = area.count,
            lang = lang,
            recentMessages = recentMessages
        )
        sheet.show(childFragmentManager, FeedbackExplanationSheet.TAG)
    }

    /**
     * "Overview" = the sessions overview (Progress tab with session history). Explicit
     * navigation instead of navigateUp(): after the finish-training handoff the review screen
     * may have no back stack, and even when it does, "Overview" should always land on Progress.
     */
    private fun navigateToOverview() {
        findNavController().navigate(
            R.id.navigation_progress,
            null,
            androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.navigation_dashboard, false)
                .setLaunchSingleTop(true)
                .build()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
