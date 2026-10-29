package org.bataydar.evidence.ui.landlord

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.bataydar.evidence.R
import org.bataydar.evidence.data.remote.dto.LandlordRequestItemDto
import org.bataydar.evidence.databinding.ItemLandlordRequestBinding

class LandlordRequestsAdapter(
    private var items: List<LandlordRequestItemDto> = emptyList(),
    private val onItemClick: (LandlordRequestItemDto) -> Unit
) : RecyclerView.Adapter<LandlordRequestsAdapter.ViewHolder>() {

    fun submitList(newItems: List<LandlordRequestItemDto>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLandlordRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemLandlordRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LandlordRequestItemDto) {
            val context = binding.root.context
            val farmer = item.farmer
            val cropRecord = item.cropRecord

            binding.tvRequestFarmerName.text = farmer?.fullName ?: "Farmer"
            binding.tvRequestFarmerPhone.text = farmer?.phone ?: ""

            binding.tvRequestCropType.text = cropRecord?.cropType ?: "Crop Cultivation"
            binding.tvRequestFieldName.text = "Field: ${cropRecord?.fieldName ?: "Plot"} (Farmer-Declared)"

            val sowingText = cropRecord?.sowingDate ?: ""
            binding.tvRequestSowingDate.text = if (sowingText.isNotBlank()) "Sowing Date: ${sowingText.take(10)}" else ""

            val photoCount = item.evidenceCount
            binding.tvEvidenceCountBadge.text = "$photoCount GPS Photos"

            when (item.status) {
                "CONFIRMED_WRITTEN", "CONFIRMED_VERBAL" -> {
                    binding.tvRequestStatusBadge.text = context.getString(R.string.landlord_filter_confirmed)
                    binding.tvRequestStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_good))
                    binding.tvRequestStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_good_light))
                }
                "REFUSED" -> {
                    binding.tvRequestStatusBadge.text = context.getString(R.string.landlord_filter_declined)
                    binding.tvRequestStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_bad))
                    binding.tvRequestStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_bad_light))
                }
                else -> {
                    binding.tvRequestStatusBadge.text = context.getString(R.string.landlord_filter_pending)
                    binding.tvRequestStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_warning))
                    binding.tvRequestStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_warning_light))
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}

