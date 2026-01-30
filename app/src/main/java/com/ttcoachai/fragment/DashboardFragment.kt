package com.ttcoachai.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ttcoachai.R
import com.ttcoachai.TTCoachApplication
import com.ttcoachai.databinding.FragmentDashboardBinding
import com.ttcoachai.helpers.DashboardDataLoader
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

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
        
        setupClickListeners()
        loadDashboardData()
    }
    
    private fun setupClickListeners() {
        val navigateToDrills = {
            val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view)
            bottomNav.selectedItemId = R.id.navigation_drills
        }

        binding.btnBeginSession.setOnClickListener { navigateToDrills() }
        binding.cardStartTrainingContainer.setOnClickListener { navigateToDrills() }
    }
    
    private fun loadDashboardData() {
        val app = requireActivity().application as TTCoachApplication
        val dataLoader = DashboardDataLoader(app.cloudSyncManager)
        
        viewLifecycleOwner.lifecycleScope.launch {
            val dashboardData = dataLoader.loadDashboardData()
            
            if (dashboardData == null) {
                // Show default/zero values if not logged in or no data
                applyDefaultData()
                return@launch
            }
            
            applyDashboardData(dashboardData)
        }
    }
    
    private fun applyDefaultData() {
        binding.tvMinutesValue.text = "0"
        binding.tvAccuracyValue.text = "0%"
        binding.tvStrokesValue.text = "0"
        binding.tvImprovementValue.text = "--"
        binding.tvWeeklyProgress.text = getString(R.string.format_weekly_days, 0, 7)
        binding.progressWeekly.progress = 0
        binding.tvWeeklyDesc.text = getString(R.string.weekly_goal_remaining_desc, 7)
    }
    
    private fun applyDashboardData(data: com.ttcoachai.helpers.DashboardData) {
        // Today's stats
        binding.tvMinutesValue.text = data.todayMinutes.toString()
        binding.tvAccuracyValue.text = "${data.todayAccuracy}%"
        binding.tvStrokesValue.text = data.todayStrokes.toString()
        
        // Improvement vs yesterday
        val improvementText = when {
            data.improvementVsYesterday > 0 -> "+${data.improvementVsYesterday}%"
            data.improvementVsYesterday < 0 -> "${data.improvementVsYesterday}%"
            else -> "--"
        }
        binding.tvImprovementValue.text = improvementText
        
        // Weekly goal
        binding.tvWeeklyProgress.text = getString(R.string.format_weekly_days, data.weeklyDaysTrained, data.weeklyGoal)
        val weeklyProgress = (data.weeklyDaysTrained * 100) / data.weeklyGoal
        binding.progressWeekly.progress = weeklyProgress
        
        val remaining = data.weeklyGoal - data.weeklyDaysTrained
        binding.tvWeeklyDesc.text = getString(R.string.weekly_goal_remaining_desc, remaining.coerceAtLeast(0))
        
        // Achievement
        data.latestAchievement?.let {
            binding.tvAchievementDesc.text = it
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
