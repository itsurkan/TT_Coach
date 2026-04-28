package com.ttcoachai.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.tabs.TabLayout
import com.ttcoachai.R
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.databinding.FragmentProgressBinding
import com.ttcoachai.databinding.ItemMilestoneCardBinding
import com.ttcoachai.databinding.ItemSkillProgressBinding
import kotlinx.coroutines.launch

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCharts()
        setupSegmentedButtons()
        loadCloudData()
    }
    
    private fun loadCloudData() {
        val app = requireActivity().application as TTCoachApplication
        val dataLoader = com.ttcoachai.helpers.ProgressDataLoader(app.cloudSyncManager)
        
        viewLifecycleOwner.lifecycleScope.launch {
            val progressData = dataLoader.loadProgressData()
            
            if (progressData == null) {
                if (!app.cloudSyncManager.isAuthenticated) {
                    loadDummyData()
                } else {
                    // Logged in but no data yet, keep empty/zero state
                    android.util.Log.d("ProgressFragment", "Authenticated but no progress data found. Showing zero state.")
                }
                return@launch
            }
            
            // Apply loaded data to UI
            applyHeaderStats(progressData)
            applyChartData(progressData.weeklyChartData)
            applySkillsData(progressData.skillsData)
            applyMilestonesData(progressData.milestonesData)
        }
    }
    
    private fun applyHeaderStats(progressData: com.ttcoachai.helpers.ProgressData) {
        val progress = progressData.userProgress
        val milestones = progressData.milestonesData
        
        binding.tvDaysStreak.text = progress?.currentStreak?.toString() ?: "0"
        
        // Total Hours (formatted as 1 decimals if needed, or just integer)
        val totalMinutes = progress?.totalTrainingMinutes ?: 0
        val totalHours = totalMinutes / 60f
        binding.tvTotalHours.text = if (totalHours >= 10) {
            totalHours.toInt().toString()
        } else {
            String.format("%.1f", totalHours)
        }
        
        // Achievements (count of achieved milestones)
        val achievementsCount = milestones.count { it.achieved }
        binding.tvAchievements.text = achievementsCount.toString()
    }
    
    private fun applyChartData(chartData: com.ttcoachai.helpers.WeeklyChartData) {
        // Bar Chart Data (Training Time)
        val barEntries = chartData.trainingMinutes.mapIndexed { index, minutes ->
            BarEntry(index.toFloat(), minutes)
        }
        
        val barDataLabel = getString(R.string.tab_training_time)
        val barDataSet = BarDataSet(barEntries, barDataLabel).apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            setDrawValues(false)
        }
        
        binding.trainingBarChart.data = BarData(barDataSet)
        binding.trainingBarChart.invalidate()

        // Line Chart Data (Accuracy)
        val lineEntries = chartData.accuracyPercentages.mapIndexed { index, accuracy ->
            Entry(index.toFloat(), accuracy)
        }
        
        val lineDataLabel = getString(R.string.tab_accuracy)
        val lineDataSet = LineDataSet(lineEntries, lineDataLabel).apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorTertiary)
            lineWidth = 3f
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.colorTertiary))
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawCircleHole(false)
        }

        binding.accuracyLineChart.data = LineData(lineDataSet)
        binding.accuracyLineChart.invalidate()
    }
    
    private fun applySkillsData(skillsData: Map<String, Int>) {
        binding.skillsContainer.removeAllViews()
        
        val skills = listOf(
            Triple(getString(R.string.exercise_forehand_name), skillsData["forehand_drive"] ?: 0, R.color.blue_600),
            Triple(getString(R.string.exercise_backhand_name), skillsData["backhand_drive"] ?: 0, R.color.blue_600),
            Triple(getString(R.string.exercise_service_name), skillsData["service"] ?: 0, R.color.blue_600),
            Triple(getString(R.string.exercise_footwork_name), skillsData["footwork"] ?: 0, R.color.blue_600)
        )

        skills.forEach { (name, level, _) ->
            val skillBinding = ItemSkillProgressBinding.inflate(layoutInflater, binding.skillsContainer, false)
            skillBinding.tvSkillName.text = name
            skillBinding.tvSkillLevel.text = "$level%"
            skillBinding.progressSkill.progress = level
            binding.skillsContainer.addView(skillBinding.root)
        }
    }
    
    private fun applyMilestonesData(milestonesData: List<com.ttcoachai.helpers.MilestoneData>) {
        binding.milestonesContainer.removeAllViews()

        milestonesData.forEach { milestone ->
            val (title, date, bgTint, iconTint) = when (milestone.type) {
                com.ttcoachai.helpers.MilestoneType.HITS_100 -> {
                    val statusText = if (milestone.achieved) getString(R.string.milestone_achieved) else getString(R.string.milestone_in_progress)
                    listOf(getString(R.string.milestone_100_hits), statusText, R.color.bg_card_target, R.color.text_card_target)
                }
                com.ttcoachai.helpers.MilestoneType.HITS_500 -> {
                    listOf("500 Hits", getString(R.string.milestone_achieved), R.color.bg_card_target, R.color.text_card_target)
                }
                com.ttcoachai.helpers.MilestoneType.HITS_1000 -> {
                    listOf("1000 Hits", getString(R.string.milestone_achieved), R.color.bg_card_target, R.color.text_card_target)
                }
                com.ttcoachai.helpers.MilestoneType.STREAK_DAYS -> {
                    listOf(getString(R.string.milestone_30_days), "${milestone.value} ${getString(R.string.days_streak)}", R.color.bg_card_calendar, R.color.text_card_calendar)
                }
                com.ttcoachai.helpers.MilestoneType.MASTER_SERVER -> {
                    listOf(getString(R.string.milestone_master_server), getString(R.string.milestone_achieved), R.color.bg_card_award, R.color.text_card_award)
                }
                com.ttcoachai.helpers.MilestoneType.TIME_1_HOUR -> {
                    listOf("1 Hour Trained", getString(R.string.milestone_achieved), R.color.bg_card_award, R.color.text_card_award)
                }
                com.ttcoachai.helpers.MilestoneType.TIME_10_HOURS -> {
                    listOf("10 Hours Trained", getString(R.string.milestone_achieved), R.color.bg_card_award, R.color.text_card_award)
                }
            }
            
            val msBinding = ItemMilestoneCardBinding.inflate(layoutInflater, binding.milestonesContainer, false)
            msBinding.tvMilestoneTitle.text = title as String
            msBinding.tvMilestoneDate.text = date as String
            msBinding.ivMilestoneIcon.setImageResource(R.drawable.ic_award)
            msBinding.ivMilestoneIcon.setColorFilter(ContextCompat.getColor(requireContext(), iconTint as Int))
            msBinding.root.backgroundTintList = ContextCompat.getColorStateList(requireContext(), bgTint as Int)
            binding.milestonesContainer.addView(msBinding.root)
        }
    }

    private fun setupCharts() {
        val days = listOf(
            getString(R.string.days_mon),
            getString(R.string.days_tue),
            getString(R.string.days_wed),
            getString(R.string.days_thu),
            getString(R.string.days_fri),
            getString(R.string.days_sat),
            getString(R.string.days_sun)
        )
        
        // Setup BarChart (Training Time)
        binding.trainingBarChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(false) // Disable interaction for simple view
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                valueFormatter = IndexAxisValueFormatter(days)
                textColor = ContextCompat.getColor(context, R.color.text_muted)
                textSize = 12f
            }
            
            axisLeft.apply {
                setDrawGridLines(false) // Clean look
                setDrawAxisLine(false)
                setDrawLabels(true)
                textColor = ContextCompat.getColor(context, R.color.text_muted)
            }
            axisRight.isEnabled = false
        }

        // Setup LineChart (Accuracy)
        binding.accuracyLineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false) // Vertical grid lines off
                setDrawAxisLine(false)
                valueFormatter = IndexAxisValueFormatter(days)
                textColor = ContextCompat.getColor(context, R.color.text_muted)
                textSize = 12f
            }

            axisLeft.apply {
                setDrawGridLines(true) // Horizontal grid lines on
                gridColor = ContextCompat.getColor(context, R.color.toolbar_background) // Light gray
                setDrawAxisLine(false)
                axisMinimum = 70f
                axisMaximum = 100f
                textColor = ContextCompat.getColor(context, R.color.text_muted)
            }
            axisRight.isEnabled = false
        }
    }

    private fun setupSegmentedButtons() {
        binding.toggleGroupChart.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_training_time -> {
                        binding.trainingBarChart.visibility = View.VISIBLE
                        binding.accuracyLineChart.visibility = View.GONE
                        binding.tvChartTitle.text = getString(R.string.chart_training_title)
                        binding.tvChartSubtitle.text = getString(R.string.chart_training_subtitle)
                        binding.trainingBarChart.animateY(1000)
                    }
                    R.id.btn_accuracy -> {
                        binding.trainingBarChart.visibility = View.GONE
                        binding.accuracyLineChart.visibility = View.VISIBLE
                        binding.tvChartTitle.text = getString(R.string.chart_accuracy_title)
                        binding.tvChartSubtitle.text = getString(R.string.chart_accuracy_subtitle)
                        binding.accuracyLineChart.animateX(1000)
                    }
                }
            }
        }
    }

    private fun loadDummyData() {
        // Bar Chart Data (Training Time)
        val barEntries = listOf(
            BarEntry(0f, 25f),
            BarEntry(1f, 30f),
            BarEntry(2f, 0f),
            BarEntry(3f, 35f),
            BarEntry(4f, 28f),
            BarEntry(5f, 45f),
            BarEntry(6f, 23f)
        )
        
        val barDataLabel = getString(R.string.tab_training_time)
        val barDataSet = BarDataSet(barEntries, barDataLabel).apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            setDrawValues(false)
        }
        
        binding.trainingBarChart.data = BarData(barDataSet)
        binding.trainingBarChart.invalidate()


        // Line Chart Data (Accuracy)
        val lineEntries = listOf(
            Entry(0f, 82f),
            Entry(1f, 85f),
            Entry(2f, 0f), 
            Entry(3f, 88f),
            Entry(4f, 87f),
            Entry(5f, 91f),
            Entry(6f, 87f)
        )
        
        val lineDataLabel = getString(R.string.tab_accuracy)
        val lineDataSet = LineDataSet(lineEntries, lineDataLabel).apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorTertiary)
            lineWidth = 3f
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.colorTertiary))
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawCircleHole(false)
        }

        binding.accuracyLineChart.data = LineData(lineDataSet)
        binding.accuracyLineChart.invalidate()


        // LOAD SKILLS
        val skills = listOf(
            Triple(getString(R.string.exercise_forehand_name), 85, R.color.blue_600),
            Triple(getString(R.string.exercise_backhand_name), 78, R.color.blue_600),
            Triple(getString(R.string.exercise_service_name), 92, R.color.blue_600),
            Triple(getString(R.string.exercise_footwork_name), 73, R.color.blue_600)
        )

        skills.forEach { (name, level, colorRes) ->
            val skillBinding = ItemSkillProgressBinding.inflate(layoutInflater, binding.skillsContainer, false)
            skillBinding.tvSkillName.text = name
            skillBinding.tvSkillLevel.text = "$level%"
            skillBinding.progressSkill.progress = level
            // We could change progress color dynamically but we set a drawable with gradient already.
            binding.skillsContainer.addView(skillBinding.root)
        }


        // LOAD MILESTONES
        data class Milestone(val title: String, val date: String, val iconRes: Int, val bgTint: Int, val iconTint: Int)
        
        val milestones = listOf(
            Milestone(getString(R.string.milestone_100_hits), getString(R.string.time_ago_2_days), R.drawable.ic_award, R.color.bg_card_target, R.color.text_card_target),
            Milestone(getString(R.string.milestone_30_days), getString(R.string.time_ago_5_days), R.drawable.ic_award, R.color.bg_card_calendar, R.color.text_card_calendar),
            Milestone(getString(R.string.milestone_master_server), getString(R.string.time_ago_1_week), R.drawable.ic_award, R.color.bg_card_award, R.color.text_card_award)
        )

        milestones.forEach { milestone ->
            val msBinding = ItemMilestoneCardBinding.inflate(layoutInflater, binding.milestonesContainer, false)
            msBinding.tvMilestoneTitle.text = milestone.title
            msBinding.tvMilestoneDate.text = milestone.date
            msBinding.ivMilestoneIcon.setImageResource(milestone.iconRes)
            msBinding.ivMilestoneIcon.setColorFilter(ContextCompat.getColor(requireContext(), milestone.iconTint))
            msBinding.root.background.setTint(ContextCompat.getColor(requireContext(), milestone.bgTint))
            // Note: Background tint on layer-list might cover everything if not careful. 
            // The item_milestone_card uses bg_card_rounded which is a shape. setTint works on the drawable.
            // Better to find the shape and tint it, or set background color.
            // But we can just set backgroundTint if AP level allows or use ViewCompat.
             msBinding.root.backgroundTintList = ContextCompat.getColorStateList(requireContext(), milestone.bgTint)

            binding.milestonesContainer.addView(msBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
