package org.bataydar.evidence.ui.detail

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bataydar.evidence.data.model.GeotaggedEvidenceEntity
import org.bataydar.evidence.databinding.ItemEvidenceGalleryBinding
import java.io.File
import java.util.Locale

class EvidenceGalleryAdapter(
    private val onDeleteClick: (GeotaggedEvidenceEntity) -> Unit
) : ListAdapter<GeotaggedEvidenceEntity, EvidenceGalleryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEvidenceGalleryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemEvidenceGalleryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GeotaggedEvidenceEntity) {
            binding.tvGpsCoords.text = String.format(
                Locale.US,
                "%.6f°, %.6f° (±%.1fm)",
                item.latitude,
                item.longitude,
                item.accuracyMeters
            )
            binding.tvCaptureTime.text = "Captured: ${item.capturedAtLocalFormatted}"
            binding.tvSha256.text = "SHA-256: ${item.photoSha256}"

            if (!item.notes.isNullOrEmpty()) {
                binding.tvNotes.visibility = View.VISIBLE
                binding.tvNotes.text = "Notes: ${item.notes}"
            } else {
                binding.tvNotes.visibility = View.GONE
            }

            binding.btnDeleteEvidence.setOnClickListener {
                onDeleteClick(item)
            }

            val photoPath = item.watermarkedPhotoPath ?: item.originalPhotoPath
            CoroutineScope(Dispatchers.Main).launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val file = File(photoPath)
                        if (file.exists()) {
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            BitmapFactory.decodeFile(photoPath, opts)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    binding.ivEvidencePhoto.setImageBitmap(bitmap)
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<GeotaggedEvidenceEntity>() {
        override fun areItemsTheSame(oldItem: GeotaggedEvidenceEntity, newItem: GeotaggedEvidenceEntity): Boolean {
            return oldItem.evidenceId == newItem.evidenceId
        }

        override fun areContentsTheSame(oldItem: GeotaggedEvidenceEntity, newItem: GeotaggedEvidenceEntity): Boolean {
            return oldItem == newItem
        }
    }
}
