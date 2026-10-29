package org.bataydar.evidence.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.FragmentHomeBinding
import org.bataydar.evidence.ui.auth.LoginActivity
import org.bataydar.evidence.ui.detail.RecordDetailActivity
import org.bataydar.evidence.ui.records.AddEditRecordActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val profileRepository by lazy { BataydarApp.instance.profileRepository }
    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }
    private val authRepository by lazy { BataydarApp.instance.authRepository }
    private val networkMonitor by lazy { BataydarApp.instance.networkMonitor }
    private val cloudSyncEngine by lazy { BataydarApp.instance.cloudSyncEngine }

    private lateinit var adapter: RecentRecordsAdapter
    private var currentPendingCount = 0
    private var isNetworkOnline = false

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

    override fun onResume() {
        super.onResume()
        refreshSyncAndOnlineState()
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

        binding.chipSyncStatus.setOnClickListener {
            handleSyncClick()
        }

        binding.cardCloudSync.setOnClickListener {
            handleSyncClick()
        }
    }

    private fun handleSyncClick() {
        val isLoggedIn = authRepository.isLoggedIn()
        val isOnline = networkMonitor.isOnline()

        if (isLoggedIn && isOnline) {
            Toast.makeText(requireContext(), R.string.sync_in_progress, Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch {
                val result = cloudSyncEngine.triggerSync()
                when (result) {
                    is org.bataydar.evidence.sync.SyncResult.Success -> {
                        Toast.makeText(requireContext(), R.string.sync_complete, Toast.LENGTH_SHORT).show()
                    }
                    is org.bataydar.evidence.sync.SyncResult.NetworkUnavailable -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    }
                    is org.bataydar.evidence.sync.SyncResult.Failure -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        } else if (isLoggedIn && !isOnline) {
            Toast.makeText(
                requireContext(),
                "Offline Mode: No internet connection. Evidence remains safe offline on this device.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sync_status_offline)
                .setMessage("You are currently in Offline Mode. Log in with your email and password to sync your cultivation records with the cloud.")
                .setPositiveButton("Log In") { _, _ ->
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun refreshSyncAndOnlineState() {
        val isLoggedIn = authRepository.isLoggedIn()
        val isOnline = networkMonitor.isOnline()
        updateSyncStatusUi(isLoggedIn, isOnline, currentPendingCount)
    }

    private fun updateSyncStatusUi(isLoggedIn: Boolean, isOnline: Boolean, pendingCount: Int) {
        if (_binding == null) return

        if (isLoggedIn && isOnline) {
            // ONLINE MODE: Logged in via email/password + connected to internet
            if (pendingCount > 0) {
                binding.chipSyncStatus.text = getString(R.string.sync_status_online_syncing, pendingCount)
                binding.chipSyncStatus.setChipIconResource(R.drawable.ic_sync)
                binding.tvSyncValue.text = "Syncing ($pendingCount)"
            } else {
                binding.chipSyncStatus.text = getString(R.string.sync_status_online)
                binding.chipSyncStatus.setChipIconResource(R.drawable.ic_cloud_done)
                binding.tvSyncValue.text = getString(R.string.sync_value_online)
            }
            binding.chipSyncStatus.setChipBackgroundColorResource(R.color.primary_green)
            binding.chipSyncStatus.setChipStrokeColorResource(R.color.primary_green_light)
            binding.ivSyncIcon.setImageResource(R.drawable.ic_cloud_done)
            binding.ivSyncIcon.setColorFilter(requireContext().getColor(R.color.primary_green))
        } else if (isLoggedIn && !isOnline) {
            // LOGGED IN BUT NO INTERNET
            if (pendingCount > 0) {
                binding.chipSyncStatus.text = getString(R.string.sync_status_pending, pendingCount)
                binding.chipSyncStatus.setChipIconResource(R.drawable.ic_sync)
                binding.tvSyncValue.text = "Pending ($pendingCount)"
            } else {
                binding.chipSyncStatus.text = getString(R.string.sync_status_offline_no_net)
                binding.chipSyncStatus.setChipIconResource(R.drawable.ic_cloud_off)
                binding.tvSyncValue.text = getString(R.string.sync_value_offline)
            }
            binding.chipSyncStatus.setChipBackgroundColorResource(R.color.primary_green_dark)
            binding.chipSyncStatus.setChipStrokeColorResource(R.color.accent_amber)
            binding.ivSyncIcon.setImageResource(R.drawable.ic_cloud_off)
            binding.ivSyncIcon.setColorFilter(requireContext().getColor(R.color.accent_amber))
        } else {
            // OFFLINE MODE: Guest / not logged in
            binding.chipSyncStatus.text = getString(R.string.sync_status_offline)
            binding.chipSyncStatus.setChipIconResource(R.drawable.ic_cloud_off)
            binding.tvSyncValue.text = getString(R.string.sync_value_offline)
            binding.chipSyncStatus.setChipBackgroundColorResource(R.color.primary_green_dark)
            binding.chipSyncStatus.setChipStrokeColorResource(R.color.primary_green_light)
            binding.ivSyncIcon.setImageResource(R.drawable.ic_cloud_off)
            binding.ivSyncIcon.setColorFilter(requireContext().getColor(R.color.text_secondary))
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

                // Online/Offline network state
                launch {
                    networkMonitor.isOnlineFlow.collect { isOnline ->
                        isNetworkOnline = isOnline
                        refreshSyncAndOnlineState()
                    }
                }

                // Pending sync count
                launch {
                    evidenceRepository.pendingSyncCount.collect { count ->
                        currentPendingCount = count
                        refreshSyncAndOnlineState()
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
