package org.bataydar.evidence.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.data.local.AppDatabase
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.databinding.FragmentHomeBinding
import org.bataydar.evidence.sync.CloudSyncState
import org.bataydar.evidence.sync.SyncStatusInfo
import org.bataydar.evidence.ui.auth.LoginActivity
import org.bataydar.evidence.ui.detail.RecordDetailActivity
import org.bataydar.evidence.ui.records.AddEditRecordActivity
import org.bataydar.evidence.util.NetworkMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }
    private val profileRepository by lazy { BataydarApp.instance.profileRepository }
    private val authRepository by lazy { BataydarApp.instance.authRepository }
    private val cloudSyncEngine by lazy { BataydarApp.instance.cloudSyncEngine }
    private val cloudRestoreManager by lazy { BataydarApp.instance.cloudRestoreManager }
    private val networkMonitor by lazy { NetworkMonitor(requireContext()) }
    private val tokenManager by lazy { TokenManager.getInstance(requireContext()) }
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    private lateinit var adapter: RecentRecordsAdapter
    private val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

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
        viewLifecycleOwner.lifecycleScope.launch {
            cloudSyncEngine.refreshSyncState()
            refreshDatabaseCounts()
        }
    }

    private suspend fun refreshDatabaseCounts() = withContext(Dispatchers.IO) {
        val totalRec = db.cropRecordDao().getTotalRecordsCountDirect()
        val syncedRec = db.cropRecordDao().getSyncedRecordsCountDirect()
        val totalEv = db.geotaggedEvidenceDao().getTotalEvidenceCountDirect()
        val syncedEv = db.geotaggedEvidenceDao().getSyncedEvidenceCountDirect()
        val failedEv = db.geotaggedEvidenceDao().getFailedEvidenceCountDirect()

        withContext(Dispatchers.Main) {
            if (_binding == null) return@withContext
            if (!cloudSyncEngine.uploadProgressFlow.value.isUploading) {
                binding.tvUploadProgressCounts.text = String.format(
                    Locale.getDefault(),
                    "Records uploaded: %d/%d  •  Photos uploaded: %d/%d",
                    syncedRec,
                    totalRec,
                    syncedEv,
                    totalEv
                )
                if (failedEv > 0) {
                    binding.tvFailedPhotosCount.visibility = View.VISIBLE
                    binding.tvFailedPhotosCount.text = "Failed photos: $failedEv"
                } else {
                    binding.tvFailedPhotosCount.visibility = View.GONE
                }
            }
        }
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

        // PART A: Explicit Upload Data Button
        binding.btnUploadData.setOnClickListener {
            performCloudUpload()
        }

        // PART A: Explicit Restore Data Button
        binding.btnRestoreData.setOnClickListener {
            performCloudRestore()
        }
    }

    private fun handleSyncClick() {
        val isLoggedIn = authRepository.isLoggedIn()
        val isOnline = networkMonitor.isOnline()
        val currentInfo = cloudSyncEngine.syncStatusFlow.value

        val lastSyncStr = if (currentInfo.lastSyncTimestamp > 0L) {
            timeFormat.format(Date(currentInfo.lastSyncTimestamp))
        } else {
            getString(R.string.sync_never_synced)
        }

        if (!isLoggedIn) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sync_dialog_title)
                .setMessage("You are currently in Offline-First Mode.\n\nAll records and GPS-verified photographs remain safe on this phone.\n\nLog in with your email and password to synchronize your evidence across devices with MongoDB Atlas and Cloudinary.")
                .setPositiveButton("Log In") { _, _ ->
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            return
        }

        if (!isOnline) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sync_dialog_title)
                .setMessage("Current Status: Offline (No Internet Connection)\n\nPending Uploads: ${currentInfo.pendingUploads}\nLast Successful Sync: $lastSyncStr\n\nYour local evidence remains safely stored in the on-device database until network connection is restored.")
                .setPositiveButton("Retry Connection") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        cloudSyncEngine.refreshSyncState()
                    }
                }
                .setNegativeButton(R.string.action_close, null)
                .show()
            return
        }

        val statusText = when (currentInfo.state) {
            CloudSyncState.OFFLINE -> getString(R.string.sync_state_offline)
            CloudSyncState.ONLINE_NOT_SYNCED -> getString(R.string.sync_state_online_not_synced)
            CloudSyncState.SYNCING -> getString(R.string.sync_state_syncing)
            CloudSyncState.SYNCED -> getString(R.string.sync_state_synced)
            CloudSyncState.SYNC_FAILED -> "${getString(R.string.sync_state_sync_failed)}: ${currentInfo.message ?: ""}"
        }

        val message = StringBuilder()
            .append("• Current Status: ").append(statusText).append("\n")
            .append("• Pending Uploads: ").append(currentInfo.pendingUploads).append("\n")
            .append("• Last Sync: ").append(lastSyncStr).append("\n\n")
            .append("Use the 'Upload Data' button to push pending records and photos, or 'Restore Data' to pull cloud backup.")
            .toString()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sync_dialog_title)
            .setMessage(message)
            .setPositiveButton("Upload Now") { _, _ -> performCloudUpload() }
            .setNeutralButton("Restore Backup") { _, _ -> performCloudRestore() }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun performCloudUpload() {
        val isLoggedIn = authRepository.isLoggedIn()
        if (!isLoggedIn) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Login Required")
                .setMessage("Please log in to upload your crop records and photos to your cloud account.")
                .setPositiveButton("Log In") { _, _ ->
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            return
        }

        if (!networkMonitor.isOnline()) {
            Toast.makeText(requireContext(), "No internet connection. Evidence remains safe offline.", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog: AlertDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.btn_upload_data)
            .setMessage("Uploading crop records and original photos to cloud...")
            .setCancelable(false)
            .create()

        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = cloudSyncEngine.performUpload()
            progressDialog.dismiss()
            refreshDatabaseCounts()

            if (result.success) {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Upload Status")
                    .setMessage(result.message)
                    .setPositiveButton(R.string.action_retry) { _, _ -> performCloudUpload() }
                    .setNegativeButton(R.string.action_close, null)
                    .show()
            }
        }
    }

    private fun performCloudRestore() {
        val isLoggedIn = authRepository.isLoggedIn()
        if (!isLoggedIn) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Login Required")
                .setMessage("Please log in to restore your cloud backup.")
                .setPositiveButton("Log In") { _, _ ->
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            return
        }

        if (!networkMonitor.isOnline()) {
            Toast.makeText(requireContext(), "No internet connection. Cannot restore cloud backup.", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog: AlertDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_restoring_title)
            .setMessage(getString(R.string.backup_restoring_msg))
            .setCancelable(false)
            .create()

        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val restoreResult = cloudRestoreManager.restoreUserData()
            progressDialog.dismiss()
            refreshDatabaseCounts()

            restoreResult.onSuccess { counts ->
                Toast.makeText(
                    requireContext(),
                    "Restored ${counts.cropRecords} records and ${counts.evidenceList} verified photos from cloud.",
                    Toast.LENGTH_LONG
                ).show()
                cloudSyncEngine.refreshSyncState()
            }.onFailure { err ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Restore Failed")
                    .setMessage("Could not restore cloud backup: ${err.message}")
                    .setPositiveButton(R.string.action_retry) { _, _ -> performCloudRestore() }
                    .setNegativeButton(R.string.action_close, null)
                    .show()
            }
        }
    }

    private fun updateSyncUi(info: SyncStatusInfo) {
        if (_binding == null) return

        when (info.state) {
            CloudSyncState.OFFLINE -> {
                binding.chipSyncStatus.text = getString(R.string.sync_state_offline)
                binding.chipSyncStatus.setChipIconResource(R.drawable.ic_cloud_off)
                binding.chipSyncStatus.setChipBackgroundColorResource(R.color.primary_green_dark)
                binding.chipSyncStatus.setChipStrokeColorResource(R.color.primary_green_light)

                binding.ivSyncIcon.setImageResource(R.drawable.ic_cloud_off)
                binding.ivSyncIcon.setColorFilter(requireContext().getColor(R.color.text_secondary))
                binding.tvSyncValue.text = getString(R.string.sync_state_offline)
                binding.tvSyncSubtext.text = "Offline Mode"
            }
            CloudSyncState.ONLINE_NOT_SYNCED -> {
                binding.chipSyncStatus.text = getString(R.string.sync_state_online_not_synced)
                binding.chipSyncStatus.setChipIconResource(R.drawable.ic_sync)
                binding.chipSyncStatus.setChipBackgroundColorResource(R.color.primary_green_dark)
                binding.chipSyncStatus.setChipStrokeColorResource(R.color.accent_amber)

                binding.ivSyncIcon.setImageResource(R.drawable.ic_sync)
                binding.ivSyncIcon.setColorFilter(requireContext().getColor(R.color.accent_amber))
                binding.tvSyncValue.text = "Not Synced"
                binding.tvSyncSubtext.text = "${info.pendingUploads} pending upload(s)"
            }
            CloudSyncState.SYNCING -> {
                binding.chipSyncStatus.text = getString(R.string.sync_state_syncing)
                binding.chipSyncStatus.setChipIconResource(R.drawable.ic_sync)
                binding.chipSyncStatus.setChipBackgroundColorResource(R.color.primary_green)
                binding.chipSyncStatus.setChipStrokeColorResource(R.color.primary_green_light)

                binding.ivSyncIcon.setImageResource(R.drawable.ic_sync)
                binding.ivSyncIcon.setColorFilter(requireContext().getColor(R.color.primary_green))
                binding.tvSyncValue.text = getString(R.string.sync_state_syncing)
                binding.tvSyncSubtext.text = "Syncing with cloud"
            }
            CloudSyncState.SYNCED -> {
                binding.chipSyncStatus.text = getString(R.string.sync_state_synced)
                binding.chipSyncStatus.setChipIconResource(R.drawable.ic_cloud_done)
                binding.chipSyncStatus.setChipBackgroundColorResource(R.color.primary_green)
                binding.chipSyncStatus.setChipStrokeColorResource(R.color.primary_green_light)

                binding.ivSyncIcon.setImageResource(R.drawable.ic_cloud_done)
                binding.ivSyncIcon.setColorFilter(requireContext().getColor(R.color.primary_green))
                binding.tvSyncValue.text = getString(R.string.sync_state_synced)
                binding.tvSyncSubtext.text = if (info.lastSyncTimestamp > 0L) {
                    "Last: ${timeFormat.format(Date(info.lastSyncTimestamp))}"
                } else {
                    "All changes synced"
                }
            }
            CloudSyncState.SYNC_FAILED -> {
                binding.chipSyncStatus.text = getString(R.string.sync_state_sync_failed)
                binding.chipSyncStatus.setChipIconResource(R.drawable.ic_cloud_off)
                binding.chipSyncStatus.setChipBackgroundColorResource(R.color.primary_green_dark)
                binding.chipSyncStatus.setChipStrokeColorResource(requireContext().getColor(android.R.color.holo_red_light))

                binding.ivSyncIcon.setImageResource(R.drawable.ic_cloud_off)
                binding.ivSyncIcon.setColorFilter(requireContext().getColor(android.R.color.holo_red_light))
                binding.tvSyncValue.text = getString(R.string.sync_state_sync_failed)
                binding.tvSyncSubtext.text = "Tap to retry"
            }
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

                // Real Cloud Sync Status flow
                launch {
                    cloudSyncEngine.syncStatusFlow.collect { info ->
                        updateSyncUi(info)
                    }
                }

                // Network monitor state changes
                launch {
                    networkMonitor.isOnlineFlow.collect { isOnline ->
                        cloudSyncEngine.refreshSyncState()
                        binding.tvCloudSyncStatusBadge.text = if (isOnline) "Online" else "Offline"
                        binding.tvCloudSyncStatusBadge.setTextColor(
                            requireContext().getColor(if (isOnline) R.color.primary_green else R.color.text_secondary)
                        )
                    }
                }

                // Upload Progress Flow
                launch {
                    cloudSyncEngine.uploadProgressFlow.collect { uploadInfo ->
                        val isRestoring = cloudRestoreManager.restoreProgressFlow.value.isRestoring
                        val inAction = uploadInfo.isUploading || isRestoring

                        binding.btnUploadData.isEnabled = !inAction
                        binding.btnRestoreData.isEnabled = !inAction
                        binding.pbCloudSyncProgress.visibility = if (inAction) View.VISIBLE else View.GONE
                        binding.tvCloudSyncProgressMsg.visibility = if (inAction) View.VISIBLE else View.GONE

                        if (uploadInfo.isUploading) {
                            binding.tvCloudSyncProgressMsg.text = uploadInfo.currentStepMessage ?: "Uploading data..."
                            binding.tvUploadProgressCounts.text = String.format(
                                Locale.getDefault(),
                                "Records uploaded: %d/%d  •  Photos uploaded: %d/%d",
                                uploadInfo.uploadedRecords,
                                uploadInfo.totalRecords,
                                uploadInfo.uploadedPhotos,
                                uploadInfo.totalPhotos
                            )
                        }

                        if (uploadInfo.failedPhotos > 0) {
                            binding.tvFailedPhotosCount.visibility = View.VISIBLE
                            binding.tvFailedPhotosCount.text = "Failed photos: ${uploadInfo.failedPhotos}"
                        } else {
                            binding.tvFailedPhotosCount.visibility = View.GONE
                        }

                        val lastUpload = if (uploadInfo.lastUploadTimestamp > 0L) {
                            uploadInfo.lastUploadTimestamp
                        } else {
                            tokenManager.getLastUploadTimestamp()
                        }
                        binding.tvLastUploadTimestamp.text = if (lastUpload > 0L) {
                            "Last Upload: ${timeFormat.format(Date(lastUpload))}"
                        } else {
                            "Last Upload: Never"
                        }
                    }
                }

                // Restore Progress Flow
                launch {
                    cloudRestoreManager.restoreProgressFlow.collect { restoreInfo ->
                        val isUploading = cloudSyncEngine.uploadProgressFlow.value.isUploading
                        val inAction = isUploading || restoreInfo.isRestoring

                        binding.btnUploadData.isEnabled = !inAction
                        binding.btnRestoreData.isEnabled = !inAction
                        binding.pbCloudSyncProgress.visibility = if (inAction) View.VISIBLE else View.GONE
                        binding.tvCloudSyncProgressMsg.visibility = if (inAction) View.VISIBLE else View.GONE

                        if (restoreInfo.isRestoring) {
                            binding.tvCloudSyncProgressMsg.text = restoreInfo.currentStepMessage ?: "Restoring data..."
                        }

                        val summary = restoreInfo.lastSummary ?: tokenManager.getLastRestoreSummary()
                        binding.tvRestoreProgressCounts.text = if (!summary.isNullOrBlank()) {
                            summary
                        } else if (restoreInfo.restoredRecords > 0 || restoreInfo.downloadedPhotos > 0) {
                            "Records restored: ${restoreInfo.restoredRecords}  •  Photos downloaded: ${restoreInfo.downloadedPhotos}"
                        } else {
                            "Restore progress: Not restored yet"
                        }

                        val lastRestore = if (restoreInfo.lastRestoreTimestamp > 0L) {
                            restoreInfo.lastRestoreTimestamp
                        } else {
                            tokenManager.getLastRestoreTimestamp()
                        }
                        binding.tvLastRestoreTimestamp.text = if (lastRestore > 0L) {
                            "Last Restore: ${timeFormat.format(Date(lastRestore))}"
                        } else {
                            "Last Restore: Never"
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
