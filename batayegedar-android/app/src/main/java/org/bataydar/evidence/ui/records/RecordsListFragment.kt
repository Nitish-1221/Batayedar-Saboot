package org.bataydar.evidence.ui.records

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.model.RecordWithEvidence
import org.bataydar.evidence.databinding.FragmentRecordsListBinding
import org.bataydar.evidence.ui.detail.RecordDetailActivity

class RecordsListFragment : Fragment() {

    private var _binding: FragmentRecordsListBinding? = null
    private val binding get() = _binding!!

    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }
    private lateinit var adapter: RecordsAdapter

    private var allRecords: List<RecordWithEvidence> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentFilterMode: FilterMode = FilterMode.ALL

    enum class FilterMode {
        ALL,
        WITH_PHOTOS,
        AWAITING_LANDLORD,
        CONFIRMED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchAndFilters()
        observeRecords()
    }

    private fun setupRecyclerView() {
        adapter = RecordsAdapter { item ->
            val intent = Intent(requireContext(), RecordDetailActivity::class.java).apply {
                putExtra("RECORD_ID", item.record.recordId)
            }
            startActivity(intent)
        }
        binding.rvRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecords.adapter = adapter
    }

    private fun setupSearchAndFilters() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                applyFiltersAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipFilterAll.setOnClickListener {
            currentFilterMode = FilterMode.ALL
            applyFiltersAndSearch()
        }
        binding.chipFilterWithPhotos.setOnClickListener {
            currentFilterMode = FilterMode.WITH_PHOTOS
            applyFiltersAndSearch()
        }
        binding.chipFilterAwaitingLandlord.setOnClickListener {
            currentFilterMode = FilterMode.AWAITING_LANDLORD
            applyFiltersAndSearch()
        }
        binding.chipFilterConfirmed.setOnClickListener {
            currentFilterMode = FilterMode.CONFIRMED
            applyFiltersAndSearch()
        }
    }

    private fun observeRecords() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                evidenceRepository.allRecordsFlow.collect { records ->
                    allRecords = records
                    updateFilterChipCounts(records)
                    applyFiltersAndSearch()
                }
            }
        }
    }

    private fun updateFilterChipCounts(records: List<RecordWithEvidence>) {
        val withPhotos = records.count { it.evidenceList.isNotEmpty() }
        val awaiting = records.count { it.record.landlordConfirmationStatus == LandlordStatus.REQUESTED }
        val confirmed = records.count { it.record.landlordConfirmationStatus.isConfirmed() }

        binding.chipFilterWithPhotos.text = "With Evidence ($withPhotos)"
        binding.chipFilterAwaitingLandlord.text = "Awaiting Landlord ($awaiting)"
        binding.chipFilterConfirmed.text = "Confirmed ($confirmed)"
    }

    private fun applyFiltersAndSearch() {
        var filtered = when (currentFilterMode) {
            FilterMode.ALL -> allRecords
            FilterMode.WITH_PHOTOS -> allRecords.filter { it.evidenceList.isNotEmpty() }
            FilterMode.AWAITING_LANDLORD -> allRecords.filter { it.record.landlordConfirmationStatus == LandlordStatus.REQUESTED }
            FilterMode.CONFIRMED -> allRecords.filter { it.record.landlordConfirmationStatus.isConfirmed() }
        }

        if (currentSearchQuery.isNotEmpty()) {
            val q = currentSearchQuery.lowercase()
            filtered = filtered.filter { item ->
                item.record.cropType.lowercase().contains(q) ||
                        item.record.fieldName.lowercase().contains(q) ||
                        item.record.village.lowercase().contains(q) ||
                        item.record.sowingDate.lowercase().contains(q)
            }
        }

        adapter.submitList(filtered)
        if (filtered.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.tvEmptyState.text = if (allRecords.isEmpty()) {
                getString(R.string.empty_records_total)
            } else {
                getString(R.string.empty_records_search)
            }
        } else {
            binding.tvEmptyState.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
