package org.bataydar.evidence.ui.landlord

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.bataydar.evidence.R
import org.bataydar.evidence.data.remote.dto.LandlordHistoryItemDto
import org.bataydar.evidence.databinding.ItemLandlordHistoryBinding

class LandlordHistoryAdapter(
    private var items: List<LandlordHistoryItemDto> = emptyList(),
    private val onItemClick: (LandlordHistoryItemDto) -> Unit
) : RecyclerView.Adapter<LandlordHistoryAdapter.ViewHolder>() {

    fun submitList(newItems: List<LandlordHistoryItemDto>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLandlordHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemLandlordHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LandlordHistoryItemDto) {
            val context = binding.root.context

            binding.tvHistoryFarmerName.text = "${item.farmerName ?: "Farmer"} (${item.farmerPhone ?: ""})"
            binding.tvHistoryCropField.text = "${item.cropType ?: "Crop"} • ${item.fieldName ?: "Field"}"

            val dateText = item.respondedAt ?: item.requestedAt ?: ""
            binding.tvHistoryRespondedDate.text = if (dateText.isNotBlank()) "Responded: ${dateText.take(10)}" else ""

            if (!item.notes.isNullOrBlank()) {
                binding.tvHistoryNotes.visibility = View.VISIBLE
                binding.tvHistoryNotes.text = "Landlord Remarks: ${item.notes}"
            } else {
                binding.tvHistoryNotes.visibility = View.GONE
            }

            when (item.status) {
                "CONFIRMED_WRITTEN", "CONFIRMED_VERBAL" -> {
                    binding.tvHistoryStatusBadge.text = context.getString(R.string.landlord_filter_confirmed)
                    binding.tvHistoryStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_good))
                    binding.tvHistoryStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_good_light))
                }
                "REFUSED" -> {
                    binding.tvHistoryStatusBadge.text = context.getString(R.string.landlord_filter_declined)
                    binding.tvHistoryStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_bad))
                    binding.tvHistoryStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_bad_light))
                }
                else -> {
                    binding.tvHistoryStatusBadge.text = item.status
                    binding.tvHistoryStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_neutral))
                    binding.tvHistoryStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.status_neutral_light))
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}

