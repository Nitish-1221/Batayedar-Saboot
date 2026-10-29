package org.bataydar.evidence.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.FragmentHomeBinding
import org.bataydar.evidence.ui.detail.RecordDetailActivity
import org.bataydar.evidence.ui.records.AddEditRecordActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val profileRepository by lazy { BataydarApp.instance.profileRepository }
    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }
    private lateinit var adapter: RecentRecordsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = RecentRecordsAdapter { recordWithEvidence ->
            val intent = Intent(requireContext(), RecordDetailActivity::class.java).apply {
                putExtra("RECORD_ID", recordWithEvidence.record.recordId)
            }
            startActivity(intent)
        }
        binding.rvRecentRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentRecords.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnQuickAddEvidence.setOnClickListener {
            val intent = Intent(requireContext(), AddEditRecordActivity::class.java)
            startActivity(intent)
        }

        binding.tvViewAllRecords.setOnClickListener {
            (activity as? MainActivity)?.switchToRecordsTab()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Farmer profile
                launch {
                    profileRepository.profileFlow.collect { profile ->
                        if (profile != null) {
                            binding.tvFarmerName.text = profile.fullName
                            binding.tvGreeting.text = "Namaste, ${profile.village}"
                        } else {
                            binding.tvFarmerName.text = "Kisan Bhai"
                        }
                    }
                }

                // Total fields count
                launch {
                    evidenceRepository.totalFieldsCount.collect { count ->
                        binding.tvCountFields.text = count.toString()
                    }
                }

                // Total evidence photos count
                launch {
                    evidenceRepository.totalEvidenceCount.collect { count ->
                        binding.tvCountPhotos.text = count.toString()
                    }
                }

                // Awaiting landlord count
                launch {
                    evidenceRepository.awaitingLandlordCount.collect { count ->
                        binding.tvCountLandlordAwaiting.text = count.toString()
                    }
                }

                // Pending sync count
                launch {
                    evidenceRepository.pendingSyncCount.collect { count ->
                        if (count > 0) {
                            binding.tvSyncValue.text = "Pending ($count)"
                            binding.chipSyncStatus.text = getString(R.string.sync_status_pending, count)
                        } else {
                            binding.tvSyncValue.text = "Offline Safe"
                            binding.chipSyncStatus.text = getString(R.string.sync_status_offline)
                        }
                    }
                }

                // Recent records
                launch {
                    evidenceRepository.getRecentRecords(5).collect { records ->
                        adapter.submitList(records)
                        binding.tvEmptyRecent.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvRecentRecords.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
