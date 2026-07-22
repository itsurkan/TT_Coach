/*
 * AI Coach for Table Tennis
 * Community Drills browse screen
 */

package com.ttcoachai.ui

import android.os.Bundle
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ttcoachai.BaseActivity
import com.ttcoachai.R
import com.ttcoachai.adapter.CommunityDrillAdapter
import com.ttcoachai.databinding.ActivityCommunityDrillsBinding
import com.ttcoachai.models.CommunityDrill
import com.ttcoachai.repository.CommunityDrillRepository
import com.ttcoachai.util.CommunityDrillSort
import com.ttcoachai.util.CommunitySortMode
import kotlinx.coroutines.launch

/**
 * Community browse screen: one bounded [CommunityDrillRepository.fetchAll] cached in memory,
 * filtered client-side by [CommunityDrillSort.search]/[CommunityDrillSort.sort]. Row click opens
 * the [com.ttcoachai.ui.dialogs.CommunityDrillDetailSheet] via [openDetail].
 */
class CommunityDrillsActivity : BaseActivity() {

    private lateinit var binding: ActivityCommunityDrillsBinding
    private lateinit var adapter: CommunityDrillAdapter

    private val communityDrillRepo by lazy { CommunityDrillRepository() }

    private var allDrills: List<CommunityDrill> = emptyList()
    private var currentSort = CommunitySortMode.RATING
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityDrillsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = CommunityDrillAdapter(emptyList()) { drill -> openDetail(drill) }
        binding.rvCommunity.layoutManager = LinearLayoutManager(this)
        binding.rvCommunity.adapter = adapter

        binding.etCommunitySearch.doAfterTextChanged {
            currentQuery = it?.toString().orEmpty()
            applyFilter()
        }

        binding.chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentSort = when (checkedId) {
                R.id.chipSortNewest -> CommunitySortMode.NEWEST
                R.id.chipSortCreator -> CommunitySortMode.CREATOR
                else -> CommunitySortMode.RATING
            }
            applyFilter()
        }

        loadDrills()
    }

    private fun loadDrills() {
        binding.progressCommunity.visibility = android.view.View.VISIBLE
        binding.tvCommunityEmpty.visibility = android.view.View.GONE
        lifecycleScope.launch {
            val result = communityDrillRepo.fetchAll()
            binding.progressCommunity.visibility = android.view.View.GONE
            result.onSuccess { drills ->
                allDrills = drills
                applyFilter()
            }.onFailure {
                allDrills = emptyList()
                binding.tvCommunityEmpty.visibility = android.view.View.VISIBLE
                Toast.makeText(this@CommunityDrillsActivity, R.string.community_load_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilter() {
        val filtered = CommunityDrillSort.sort(CommunityDrillSort.search(allDrills, currentQuery), currentSort)
        adapter.setData(filtered)
        val isLoading = binding.progressCommunity.visibility == android.view.View.VISIBLE
        binding.tvCommunityEmpty.visibility =
            if (!isLoading && filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun openDetail(drill: CommunityDrill) {
        com.ttcoachai.ui.dialogs.CommunityDrillDetailSheet
            .newInstance(drill.id)
            .show(supportFragmentManager, com.ttcoachai.ui.dialogs.CommunityDrillDetailSheet.TAG)
    }
}
