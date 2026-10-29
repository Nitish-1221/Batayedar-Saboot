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
import org.bataydar.evidence.databinding.FragmentLandlordHomeBinding

class LandlordHomeFragment : Fragment() {

    private var _binding: FragmentLandlordHomeBinding? = null
    private val binding get() = _binding!!

    private val landlordRepository by lazy { BataydarApp.instance.landlordRepository }
    private val activityAdapter by lazy {
        LandlordActivityAdapter { activityItem ->
            val intent = Intent(requireContext(), LandlordReviewActivity::class.java).apply {
                putExtra(LandlordReviewActivity.EXTRA_CONFIRMATION_ID, activityItem.confirmationId)
            }
            startActivity(intent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLandlordHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvRecentActivity.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentActivity.adapter = activityAdapter

        val landlordName = landlordRepository.getStoredLandlordName()
        if (!landlordName.isNullOrBlank()) {
            binding.tvWelcomeTitle.text = "Welcome, $landlordName"
        }

        binding.btnViewRequests.setOnClickListener {
            (activity as? LandlordDashboardActivity)?.switchToRequestsTab()
        }

        binding.cardPending.setOnClickListener {
            (activity as? LandlordDashboardActivity)?.switchToRequestsTab()
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadDashboard()
        }

        loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = landlordRepository.getDashboard()
            binding.swipeRefresh.isRefreshing = false
            result.onSuccess { data ->
                binding.tvPendingCount.text = data.pendingCount.toString()
                binding.tvCompletedCount.text = data.completedCount.toString()
                binding.tvTotalCount.text = data.totalRequests.toString()

                if (data.recentActivity.isEmpty()) {
                    binding.tvEmptyActivity.visibility = View.VISIBLE
                    binding.rvRecentActivity.visibility = View.GONE
                } else {
                    binding.tvEmptyActivity.visibility = View.GONE
                    binding.rvRecentActivity.visibility = View.VISIBLE
                    activityAdapter.submitList(data.recentActivity)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

