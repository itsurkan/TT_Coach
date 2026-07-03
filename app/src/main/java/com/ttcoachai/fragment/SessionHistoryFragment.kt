package com.ttcoachai.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ttcoachai.R
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.databinding.FragmentSessionHistoryBinding
import com.ttcoachai.databinding.ItemSessionHistoryRowBinding
import com.ttcoachai.helpers.SessionHistoryGrouper
import com.ttcoachai.helpers.SessionHistoryGrouper.Trend
import com.ttcoachai.helpers.SessionHistoryGrouper.WeekGroup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class SessionHistoryFragment : Fragment() {

    private var _binding: FragmentSessionHistoryBinding? = null
    private val binding get() = _binding!!

    private var allRows: List<SessionHistoryGrouper.SessionRow> = emptyList()
    private var filter: String? = null // null = All; else exerciseName substring
    private var earlierExpanded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSessionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnEmptyCta.setOnClickListener {
            findNavController().navigate(R.id.navigation_drills)
        }
        binding.btnShowEarlier.setOnClickListener {
            earlierExpanded = true
            binding.btnShowEarlier.visibility = View.GONE
            render()
        }
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.chip_forehand -> "Forehand"
                R.id.chip_backhand -> "Backhand"
                R.id.chip_topspin -> "Topspin"
                else -> null
            }
            earlierExpanded = false
            render()
        }

        val app = requireActivity().application as TTCoachApplication
        val userId = app.cloudSyncManager.currentUserId ?: run {
            render()
            return
        }
        val now = System.currentTimeMillis()
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.trainingDao().getSessionsForUser(userId).collectLatest { sessions ->
                val kpi = SessionHistoryGrouper.last30DaysKpi(sessions, now)
                binding.tvSubtitle.text = getString(R.string.history_subtitle, kpi.sessionCount)
                binding.tvKpiSessions.text = kpi.sessionCount.toString()
                binding.tvKpiAccuracy.text = "${kpi.avgAccuracyPercent}%"
                binding.tvKpiTime.text = getString(R.string.history_total_hours, kpi.totalHours)
                allRows = SessionHistoryGrouper.group(sessions, now, ZoneId.systemDefault())
                render()
            }
        }
    }

    private fun render() {
        val rows = allRows.filter { filter == null || it.session.exerciseName.contains(filter!!, ignoreCase = true) }
        binding.listContainer.removeAllViews()

        if (rows.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.btnShowEarlier.visibility = View.GONE
            return
        }
        binding.emptyState.visibility = View.GONE

        val earlierCount = rows.count { it.group == WeekGroup.EARLIER }
        val visible = if (earlierExpanded) rows else rows.filter { it.group != WeekGroup.EARLIER }

        var lastGroup: WeekGroup? = null
        for (row in visible) {
            if (row.group != lastGroup) {
                addSectionHeader(row.group, row.session.startTime)
                lastGroup = row.group
            }
            addRow(row)
        }
        binding.btnShowEarlier.visibility =
            if (!earlierExpanded && earlierCount > 0) View.VISIBLE else View.GONE
        if (binding.btnShowEarlier.visibility == View.VISIBLE) {
            binding.btnShowEarlier.text = getString(R.string.history_show_earlier, earlierCount)
        }
    }

    private fun addSectionHeader(group: WeekGroup, startMs: Long) {
        val tv = TextView(requireContext())
        tv.setTextAppearance(R.style.TextAppearance_TTC_Eyebrow)
        tv.setPadding(0, dp(16), 0, dp(6))
        tv.text = when (group) {
            WeekGroup.THIS_WEEK -> getString(R.string.history_section_this_week)
            WeekGroup.LAST_WEEK -> getString(R.string.history_section_last_week)
            WeekGroup.EARLIER -> {
                val month = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
                    .month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                getString(R.string.history_section_earlier, month.uppercase(Locale.getDefault()))
            }
        }
        binding.listContainer.addView(tv)
    }

    private fun addRow(row: SessionHistoryGrouper.SessionRow) {
        val rb = ItemSessionHistoryRowBinding.inflate(layoutInflater, binding.listContainer, false)
        val s = row.session
        rb.tvStrokeName.text = s.exerciseName
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(s.startTime))
        rb.tvMeta.text = getString(R.string.history_row_meta, time, s.durationSeconds / 60)
        rb.tvAccuracy.text = "${s.getAccuracyPercent()}%"
        rb.ivStrokeIcon.setImageResource(strokeIcon(s.exerciseName))
        bindTrend(rb.tvTrend, row.trend, row.trendDelta)
        rb.root.setOnClickListener { openReview(s.id) }
        binding.listContainer.addView(rb.root)
    }

    private fun openReview(sessionId: String) {
        findNavController().navigate(
            R.id.action_history_to_review,
            android.os.Bundle().apply { putString("sessionId", sessionId) }
        )
    }

    private fun bindTrend(tv: TextView, trend: Trend, delta: Int) {
        when (trend) {
            Trend.UP -> {
                tv.text = delta.toString()
                tint(tv, R.color.ttc_trend_up, R.drawable.ic_trend_up)
            }
            Trend.DOWN -> {
                tv.text = delta.toString()
                tint(tv, R.color.ttc_trend_down, R.drawable.ic_trend_down)
            }
            Trend.FLAT -> {
                tv.text = "—"
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.ttc_text_3))
                tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
    }

    private fun tint(tv: TextView, colorRes: Int, iconRes: Int) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        tv.setTextColor(color)
        tv.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        tv.compoundDrawableTintList = ColorStateList.valueOf(color)
    }

    private fun strokeIcon(name: String): Int = when {
        name.contains("Backhand", true) -> R.drawable.ic_skill_backhand
        name.contains("Topspin", true) || name.contains("Service", true) -> R.drawable.ic_skill_topspin
        else -> R.drawable.ic_skill_forehand
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
