package com.ttcoachai.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ttcoachai.databinding.FragmentDashboardBinding

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
        
        val navigateToDrills = {
            val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.ttcoachai.R.id.nav_view)
            bottomNav.selectedItemId = com.ttcoachai.R.id.navigation_drills
        }

        binding.btnBeginSession.setOnClickListener { navigateToDrills() }
        binding.cardStartTrainingContainer.setOnClickListener { navigateToDrills() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
