package org.bataydar.evidence.ui.landlord

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.bataydar.evidence.R
import org.bataydar.evidence.data.remote.dto.LandlordActivityItemDto
import org.bataydar.evidence.databinding.ItemLandlordActivityBinding

class LandlordActivityAdapter(
    private var items: List<LandlordActivityItemDto> = emptyList(),
    private val onItemClick: (LandlordActivityItemDto) -> Unit
) : RecyclerView.Adapter<LandlordActivityAdapter.ViewHolder>() {

    fun submitList(newItems: List<LandlordActivityItemDto>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLandlordActivityBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemLandlordActivityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LandlordActivityItemDto) {
            val context = binding.root.context
            val farmerText = item.farmerName ?: "Farmer"
            binding.tvActivityFarmer.text = "$farmerText (Tenant Farmer)"
            binding.tvActivityCropField.text = "${item.cropType ?: "Crop"} • ${item.fieldName ?: "Field"}"

            val dateText = item.respondedAt ?: item.requestedAt ?: ""
            binding.tvActivityDate.text = if (dateText.isNotBlank()) "Date: ${dateText.take(10)}" else ""

            when (item.status) {
                "CONFIRMED_WRITTEN", "CONFIRMED_VERBAL" -> {
                    binding.tvActivityStatusBadge.text = context.getString(R.string.filter_confirmed, 0).replace(" (0)", "")
                    binding.tvActivityStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_good))
                    binding.tvActivityStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_good_light))
                }
                "REFUSED" -> {
                    binding.tvActivityStatusBadge.text = context.getString(R.string.landlord_filter_declined)
                    binding.tvActivityStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_bad))
                    binding.tvActivityStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_bad_light))
                }
                else -> {
                    binding.tvActivityStatusBadge.text = context.getString(R.string.landlord_filter_pending)
                    binding.tvActivityStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_warning))
                    binding.tvActivityStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_warning_light))
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}

