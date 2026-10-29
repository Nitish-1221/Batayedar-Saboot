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
import org.bataydar.evidence.databinding.FragmentLandlordHistoryBinding

class LandlordHistoryFragment : Fragment() {

    private var _binding: FragmentLandlordHistoryBinding? = null
    private val binding get() = _binding!!

    private val landlordRepository by lazy { BataydarApp.instance.landlordRepository }
    private val historyAdapter by lazy {
        LandlordHistoryAdapter { historyItem ->
            val intent = Intent(requireContext(), LandlordReviewActivity::class.java).apply {
                putExtra(LandlordReviewActivity.EXTRA_CONFIRMATION_ID, historyItem.confirmationId)
            }
            startActivity(intent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLandlordHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter

        binding.swipeRefreshHistory.setOnRefreshListener {
            loadHistory()
        }

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        binding.swipeRefreshHistory.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = landlordRepository.getHistory()
            binding.swipeRefreshHistory.isRefreshing = false
            result.onSuccess { list ->
                if (list.isEmpty()) {
                    binding.layoutEmptyHistory.visibility = View.VISIBLE
                    binding.rvHistory.visibility = View.GONE
                } else {
                    binding.layoutEmptyHistory.visibility = View.GONE
                    binding.rvHistory.visibility = View.VISIBLE
                    historyAdapter.submitList(list)
                }
            }.onFailure {
                binding.layoutEmptyHistory.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
