package org.bataydar.evidence.ui.records

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
import org.bataydar.evidence.databinding.ItemRecordDetailedBinding
import java.io.File
import java.util.Locale

class RecordsAdapter(
    private val onItemClick: (RecordWithEvidence) -> Unit
) : ListAdapter<RecordWithEvidence, RecordsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordDetailedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecordDetailedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecordWithEvidence) {
            val context = binding.root.context
            val record = item.record

            binding.tvCropType.text = record.cropType
            binding.tvFieldName.text = "Field: ${record.fieldName}"
            binding.tvDateAndLocation.text = "Sown: ${record.sowingDate} • ${record.village}, ${record.district}"

            // Landlord status
            val landlordStatus = record.landlordConfirmationStatus
            binding.tvLandlordStatus.text = landlordStatus.getDisplayName(context)
            when (landlordStatus) {
                LandlordStatus.CONFIRMED_VERBAL, LandlordStatus.CONFIRMED_WRITTEN -> {
                    binding.tvLandlordStatus.setTextColor(ContextCompat.getColor(context, R.color.status_good))
                    binding.tvLandlordStatus.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_good_light)
                }
                LandlordStatus.REQUESTED -> {
                    binding.tvLandlordStatus.setTextColor(ContextCompat.getColor(context, R.color.status_warning))
                    binding.tvLandlordStatus.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_warning_light)
                }
                LandlordStatus.REFUSED -> {
                    binding.tvLandlordStatus.setTextColor(ContextCompat.getColor(context, R.color.status_bad))
                    binding.tvLandlordStatus.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_bad_light)
                }
                else -> {
                    binding.tvLandlordStatus.setTextColor(ContextCompat.getColor(context, R.color.status_neutral))
                    binding.tvLandlordStatus.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_neutral_light)
                }
            }

            // Evidence photos & GPS
            if (item.evidenceList.isNotEmpty()) {
                binding.layoutPhotoAndGps.visibility = View.VISIBLE
                binding.tvNoPhotoWarning.visibility = View.GONE

                val firstEvidence = item.evidenceList[0]
                binding.tvGpsCoords.text = String.format(
                    Locale.US,
                    "%.6f°, %.6f° (±%.1fm)",
                    firstEvidence.latitude,
                    firstEvidence.longitude,
                    firstEvidence.accuracyMeters
                )
                binding.tvCapturedTime.text = "Captured: ${firstEvidence.capturedAtLocalFormatted}"

                val photoPath = firstEvidence.watermarkedPhotoPath ?: firstEvidence.originalPhotoPath
                CoroutineScope(Dispatchers.Main).launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            val f = File(photoPath)
                            if (f.exists()) {
                                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                                BitmapFactory.decodeFile(photoPath, opts)
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        binding.ivThumb.setImageBitmap(bitmap)
                        binding.ivThumb.imageTintList = null
                    } else {
                        binding.ivThumb.setImageResource(R.drawable.ic_camera)
                        binding.ivThumb.imageTintList = ContextCompat.getColorStateList(context, R.color.primary_green)
                    }
                }
            } else {
                binding.layoutPhotoAndGps.visibility = View.GONE
                binding.tvNoPhotoWarning.visibility = View.VISIBLE
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
