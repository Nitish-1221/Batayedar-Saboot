package org.bataydar.evidence.ui.landlord

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.data.remote.dto.LandlordRequestItemDto
import org.bataydar.evidence.databinding.FragmentLandlordRequestsBinding

class LandlordRequestsFragment : Fragment() {

    private var _binding: FragmentLandlordRequestsBinding? = null
    private val binding get() = _binding!!

    private val landlordRepository by lazy { BataydarApp.instance.landlordRepository }
    private var allRequests: List<LandlordRequestItemDto> = emptyList()

    private val requestsAdapter by lazy {
        LandlordRequestsAdapter { requestItem ->
            val intent = Intent(requireContext(), LandlordReviewActivity::class.java).apply {
                putExtra(LandlordReviewActivity.EXTRA_CONFIRMATION_ID, requestItem.confirmationId)
            }
            startActivity(intent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLandlordRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRequests.adapter = requestsAdapter

        setupFilterChips()

        binding.swipeRefreshRequests.setOnRefreshListener {
            loadRequests()
        }

        loadRequests()
    }

    override fun onResume() {
        super.onResume()
        loadRequests()
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            applyFilter(checkedIds.firstOrNull())
        }
    }

    private fun loadRequests() {
        binding.swipeRefreshRequests.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = landlordRepository.getRequests()
            binding.swipeRefreshRequests.isRefreshing = false
            result.onSuccess { list ->
                allRequests = list
                applyFilter(binding.chipGroupFilter.checkedChipId)
            }.onFailure {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.rvRequests.visibility = View.GONE
            }
        }
    }

    private fun applyFilter(checkedChipId: Int?) {
        val filtered = when (checkedChipId) {
            R.id.chipPending -> allRequests.filter { it.status == "REQUESTED" }
            R.id.chipConfirmed -> allRequests.filter { it.status == "CONFIRMED_WRITTEN" || it.status == "CONFIRMED_VERBAL" }
            R.id.chipDeclined -> allRequests.filter { it.status == "REFUSED" }
            else -> allRequests
        }

        if (filtered.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvRequests.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvRequests.visibility = View.VISIBLE
            requestsAdapter.submitList(filtered)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

