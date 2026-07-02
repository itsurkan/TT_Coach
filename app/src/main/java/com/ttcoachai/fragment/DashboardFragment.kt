package com.ttcoachai.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.ttcoachai.R
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.databinding.FragmentDashboardBinding
import com.ttcoachai.helpers.DashboardData
import com.ttcoachai.helpers.DashboardDataLoader
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val bars by lazy {
        listOf(
            binding.bar0, binding.bar1, binding.bar2, binding.bar3,
            binding.bar4, binding.bar5, binding.bar6
        )
    }
    private val labels by lazy {
        listOf(
            binding.lbl0, binding.lbl1, binding.lbl2, binding.lbl3,
            binding.lbl4, binding.lbl5, binding.lbl6
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvGreeting.text = greetingForTimeOfDay()
        setupClickListeners()
        loadDashboardData()
    }

    private fun setupClickListeners() {
        // FAB → start a new session (existing behaviour: switch to the Drills tab).
        binding.btnBeginSession.setOnClickListener { navigateToTab(R.id.navigation_drills) }
        // View full report → Progress tab.
        binding.rowFullReport.setOnClickListener { navigateToTab(R.id.navigation_progress) }
        // Avatar → Profile tab.
        binding.avatarContainer.setOnClickListener { navigateToTab(R.id.navigation_profile) }
    }

    private fun navigateToTab(itemId: Int) {
        val bottomNav = requireActivity()
            .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view)
        bottomNav?.selectedItemId = itemId
    }

    private fun greetingForTimeOfDay(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val resId = when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
        return getString(resId)
    }

    private fun loadDashboardData() {
        val app = requireActivity().application as TTCoachApplication
        val dataLoader = DashboardDataLoader(app.cloudSyncManager)

        viewLifecycleOwner.lifecycleScope.launch {
            val dashboardData = dataLoader.loadDashboardData()
            if (_binding == null) return@launch

            if (dashboardData == null) {
                applyDefaultData()
            } else {
                applyDashboardData(dashboardData)
            }
        }
    }

    private fun applyDefaultData() {
        binding.tvGreetingName.text = getString(R.string.greeting_name_fallback)
        applyAvatar(getString(R.string.greeting_name_fallback), null)

        // Last session: no data.
        binding.tvLastWhen.text = getString(R.string.placeholder_dash_dash)
        binding.tvLastDrill.text = getString(R.string.placeholder_no_sessions)
        binding.tvLsStrokes.text = getString(R.string.placeholder_dash_dash)
        binding.tvLsAccuracy.text = getString(R.string.placeholder_dash_dash)
        binding.tvLsConsistency.text = getString(R.string.placeholder_dash_dash)

        // This week: empty bars, no streak, zero goal.
        applyWeekBars(List(7) { 0f }, todayDowIndex(-1))
        binding.pillStreak.visibility = View.GONE
        binding.tvWeeklySessions.text = getString(R.string.format_weekly_sessions, 0, 7)
        binding.progressWeekly.progress = 0
    }

    private fun applyDashboardData(data: DashboardData) {
        // Greeting name + avatar.
        binding.tvGreetingName.text = data.greetingName ?: getString(R.string.greeting_name_fallback)
        applyAvatar(data.avatarInitial, data.avatarPhotoUrl)

        // Last session card.
        val ls = data.lastSession
        if (ls != null) {
            binding.tvLastWhen.text = formatWhenLabel(ls.startTime)
            binding.tvLastDrill.text = ls.exerciseName.ifBlank { getString(R.string.placeholder_no_sessions) }
            binding.tvLsStrokes.text = ls.strokes.toString()
            binding.tvLsAccuracy.text = getString(R.string.format_percent, ls.accuracyPct)
            binding.tvLsConsistency.text = getString(R.string.format_percent, ls.consistencyPct)
        } else {
            binding.tvLastWhen.text = getString(R.string.placeholder_dash_dash)
            binding.tvLastDrill.text = getString(R.string.placeholder_no_sessions)
            binding.tvLsStrokes.text = getString(R.string.placeholder_dash_dash)
            binding.tvLsAccuracy.text = getString(R.string.placeholder_dash_dash)
            binding.tvLsConsistency.text = getString(R.string.placeholder_dash_dash)
        }

        // This week bars + today highlight.
        applyWeekBars(data.weekBars, data.todayDowIndex)

        // Streak pill.
        if (data.streakDays > 0) {
            binding.pillStreak.visibility = View.VISIBLE
            binding.tvStreak.text = getString(R.string.format_streak_days, data.streakDays)
        } else {
            binding.pillStreak.visibility = View.GONE
        }

        // Weekly goal.
        binding.tvWeeklySessions.text =
            getString(R.string.format_weekly_sessions, data.weeklyDone, data.weeklyGoal)
        binding.progressWeekly.progress = data.weeklyProgressPct
    }

    private fun applyAvatar(initial: String, photoUrl: String?) {
        binding.tvAvatarInitial.text = if (initial.isNotBlank()) initial else "U"
        if (photoUrl != null) {
            binding.ivAvatar.visibility = View.VISIBLE
            binding.tvAvatarInitial.visibility = View.GONE
            binding.ivAvatar.load(photoUrl) {
                transformations(CircleCropTransformation())
                crossfade(true)
            }
        } else {
            binding.ivAvatar.visibility = View.GONE
            binding.tvAvatarInitial.visibility = View.VISIBLE
        }
    }

    /** Sets bar heights by scaling 0..1 values to the 56dp track and highlights today's label. */
    private fun applyWeekBars(values: List<Float>, todayDowIndex: Int) {
        val density = resources.displayMetrics.density
        val trackPx = (56 * density).toInt()
        val minVisiblePx = (4 * density).toInt()

        for (i in 0 until 7) {
            val v = values.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val bar = bars[i]
            val lp = bar.layoutParams
            if (v <= 0f) {
                lp.height = minVisiblePx
                bar.setBackgroundResource(R.drawable.bg_bar_empty)
            } else {
                lp.height = (minVisiblePx + (trackPx - minVisiblePx) * v).toInt()
                bar.setBackgroundResource(R.drawable.bg_bar)
            }
            bar.layoutParams = lp

            // Day label highlight for today.
            val lbl = labels[i]
            if (i == todayDowIndex) {
                lbl.setTextColor(requireContext().getColor(R.color.ttc_gold_accent))
                lbl.setTypeface(lbl.typeface, android.graphics.Typeface.BOLD)
            } else {
                lbl.setTextColor(requireContext().getColor(R.color.ttc_text_3))
                lbl.setTypeface(android.graphics.Typeface.create(lbl.typeface, android.graphics.Typeface.NORMAL))
            }
        }
    }

    private fun todayDowIndex(fallback: Int): Int {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return if (fallback >= 0) fallback else (dow + 5) % 7
    }

    /** Formats a timestamp as "Today · HH:mm" / "Yesterday · HH:mm" / "Mon · HH:mm". */
    private fun formatWhenLabel(timeMillis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timeMillis }

        fun midnight(c: Calendar): Long {
            val x = c.clone() as Calendar
            x.set(Calendar.HOUR_OF_DAY, 0)
            x.set(Calendar.MINUTE, 0)
            x.set(Calendar.SECOND, 0)
            x.set(Calendar.MILLISECOND, 0)
            return x.timeInMillis
        }

        val dayMs = 24L * 60 * 60 * 1000
        val diffDays = ((midnight(now) - midnight(then)) / dayMs).toInt()

        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFmt.format(then.time)

        val dayLabel = when (diffDays) {
            0 -> getString(R.string.day_today)
            1 -> getString(R.string.day_yesterday)
            else -> SimpleDateFormat("EEE", Locale.getDefault()).format(then.time)
        }
        return getString(R.string.format_when_label, dayLabel, timeStr)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
