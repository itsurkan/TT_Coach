/*
 * AI Coach for Table Tennis
 * Community drill detail bottom sheet — preview, rate, copy to local drills
 */

package com.ttcoachai.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.ttcoachai.R
import com.ttcoachai.databinding.SheetCommunityDrillDetailBinding
import com.ttcoachai.db.AppDatabase
import com.ttcoachai.models.CommunityDrill
import com.ttcoachai.repository.CommunityDrillRepository
import com.ttcoachai.repository.CustomDrillRepository
import com.ttcoachai.ui.FOCUS_ORDER
import com.ttcoachai.ui.parseFocusCsv
import com.ttcoachai.util.CommunityDrillCopier
import com.ttcoachai.util.PerPhaseTargetsCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog opened from [com.ttcoachai.ui.CommunityDrillsActivity]'s row tap: previews a single
 * [CommunityDrill], lets a signed-in (non-anonymous) user rate it 1–5, and lets anyone copy it
 * into their local drills via [CommunityDrillCopier].
 */
class CommunityDrillDetailSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "CommunityDrillDetailSheet"
        private const val ARG_ID = "community_id"

        fun newInstance(communityId: String) = CommunityDrillDetailSheet().apply {
            arguments = Bundle().apply { putString(ARG_ID, communityId) }
        }
    }

    private var _binding: SheetCommunityDrillDetailBinding? = null
    private val binding get() = _binding!!

    private val repo by lazy { CommunityDrillRepository() }
    private val customDrillRepo by lazy {
        CustomDrillRepository(AppDatabase.getDatabase(requireContext()).customDrillDao())
    }

    private var loaded: CommunityDrill? = null

    override fun getTheme() = R.style.ThemeOverlay_TTC_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetCommunityDrillDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val communityId = requireArguments().getString(ARG_ID).orEmpty()

        binding.btnCopyToMyDrills.setOnClickListener { copyToMyDrills() }

        viewLifecycleOwner.lifecycleScope.launch {
            val drill = repo.fetchOne(communityId).getOrNull()
            if (_binding == null) return@launch
            if (drill == null) {
                Toast.makeText(requireContext(), R.string.community_load_error, Toast.LENGTH_SHORT).show()
                dismiss()
                return@launch
            }
            loaded = drill
            bind(drill)
            setupRatingInput(communityId)

            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && !user.isAnonymous) {
                val myRating = repo.myRating(communityId, user.uid).getOrNull()
                if (_binding != null && myRating != null) {
                    binding.rbDetailRate.rating = myRating.stars.toFloat()
                }
            }
        }
    }

    private fun bind(drill: CommunityDrill) {
        binding.tvDetailName.text = drill.name
        binding.tvDetailCreator.text = getString(R.string.community_detail_by_format, drill.creatorName)

        if (drill.creatorPhotoUrl.isBlank()) {
            binding.ivDetailAvatar.setImageResource(R.drawable.ic_person)
        } else {
            binding.ivDetailAvatar.load(drill.creatorPhotoUrl) {
                transformations(CircleCropTransformation())
                error(R.drawable.ic_person)
            }
        }

        updateAverage(drill)

        val focusKeys = parseFocusCsv(drill.focusCsv)
        val focusLabels = FOCUS_ORDER.filter { it in focusKeys }.map { focusLabel(it) }
        binding.tvDetailFocus.text = if (focusLabels.isEmpty()) {
            getString(R.string.community_detail_no_focus)
        } else {
            focusLabels.joinToString(", ")
        }

        binding.tvDetailTemplate.text = drill.baseTemplate
        binding.tvDetailStrictness.text =
            getString(R.string.community_detail_strictness_format, drill.strictnessX)

        val targets = PerPhaseTargetsCodec.parse(drill.perPhaseTargetsJson)
        if (targets.isEmpty()) {
            binding.groupDetailPhaseTargets.visibility = View.GONE
        } else {
            binding.groupDetailPhaseTargets.visibility = View.VISIBLE
            binding.tvDetailPhaseTargets.text = targets.entries.joinToString("\n") { (key, minMax) ->
                getString(
                    R.string.community_detail_phase_target_format,
                    key,
                    minMax.first.toInt(),
                    minMax.second.toInt()
                )
            }
        }
    }

    private fun focusLabel(key: String): String = when (key) {
        "arm" -> getString(R.string.focus_arm)
        "shoulders" -> getString(R.string.focus_shoulders)
        "legs" -> getString(R.string.focus_legs)
        "core" -> getString(R.string.focus_core)
        "hips" -> getString(R.string.focus_hips)
        else -> key
    }

    private fun updateAverage(drill: CommunityDrill) {
        binding.tvDetailAverage.text = getString(
            R.string.community_rating_format,
            drill.averageRating,
            drill.ratingCount
        )
    }

    private fun setupRatingInput(communityId: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) {
            binding.rbDetailRate.isEnabled = false
            binding.rbDetailRate.setIsIndicator(true)
            binding.tvSignInToRate.visibility = View.VISIBLE
        } else {
            binding.tvSignInToRate.visibility = View.GONE
            binding.rbDetailRate.setOnRatingBarChangeListener { _, rating, fromUser ->
                if (fromUser && rating >= 1f) {
                    submitRating(communityId, user.uid, rating.toInt())
                }
            }
        }
    }

    private fun submitRating(communityId: String, uid: String, stars: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = repo.rate(communityId, uid, stars, System.currentTimeMillis())
            if (_binding == null) return@launch
            result.onSuccess {
                val refreshed = repo.fetchOne(communityId).getOrNull()
                if (_binding != null && refreshed != null) {
                    loaded = refreshed
                    updateAverage(refreshed)
                }
            }.onFailure {
                Toast.makeText(requireContext(), R.string.community_rate_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToMyDrills() {
        val drill = loaded ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                CommunityDrillCopier.copyToLocal(drill, customDrillRepo, System.currentTimeMillis())
            }
            if (_binding == null) return@launch
            Toast.makeText(requireContext(), R.string.community_copy_success_toast, Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
