package org.bataydar.evidence.ui.main

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bataydar.evidence.R
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.model.RecordWithEvidence
import org.bataydar.evidence.databinding.ItemRecentRecordBinding
import java.io.File

class RecentRecordsAdapter(
    private val onItemClick: (RecordWithEvidence) -> Unit
) : ListAdapter<RecordWithEvidence, RecentRecordsAdapter.RecordViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val binding = ItemRecentRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecordViewHolder(private val binding: ItemRecentRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecordWithEvidence) {
            val context = binding.root.context
            val record = item.record

            binding.tvCropType.text = record.cropType
            binding.tvFieldName.text = record.fieldName
            binding.tvDateAndLocation.text = "Sown: ${record.sowingDate} • ${record.village}"

            // Status Badge
            if (item.evidenceList.isNotEmpty()) {
                val count = item.evidenceList.size
                binding.tvStatusBadge.text = if (count == 1) "1 Photo GPS" else "$count Photos GPS"
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_good))
                binding.tvStatusBadge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_good_light)
            } else {
                binding.tvStatusBadge.text = "Draft"
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_warning))
                binding.tvStatusBadge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_warning_light)
            }

            // Thumbnail loading
            if (item.evidenceList.isNotEmpty()) {
                val photoPath = item.evidenceList[0].watermarkedPhotoPath ?: item.evidenceList[0].originalPhotoPath
                CoroutineScope(Dispatchers.Main).launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            val f = File(photoPath)
                            if (f.exists()) {
                                val opts = BitmapFactory.Options().apply {
                                    inSampleSize = 4
                                }
                                BitmapFactory.decodeFile(photoPath, opts)
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        binding.ivThumbnail.setImageBitmap(bitmap)
                        binding.ivThumbnail.imageTintList = null
                    } else {
                        binding.ivThumbnail.setImageResource(R.drawable.ic_crop_field)
                        binding.ivThumbnail.imageTintList = ContextCompat.getColorStateList(context, R.color.primary_green)
                    }
                }
            } else {
                binding.ivThumbnail.setImageResource(R.drawable.ic_crop_field)
                binding.ivThumbnail.imageTintList = ContextCompat.getColorStateList(context, R.color.primary_green)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RecordWithEvidence>() {
        override fun areItemsTheSame(oldItem: RecordWithEvidence, newItem: RecordWithEvidence): Boolean {
            return oldItem.record.recordId == newItem.record.recordId
        }

        override fun areContentsTheSame(oldItem: RecordWithEvidence, newItem: RecordWithEvidence): Boolean {
            return oldItem == newItem
        }
    }
}
