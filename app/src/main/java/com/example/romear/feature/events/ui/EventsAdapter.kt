package com.example.romear.feature.events.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.romear.databinding.ItemEventBinding
import com.example.romear.feature.events.domain.EventItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EventsAdapter(
    private val onEventClicked: (EventItem) -> Unit,
) : ListAdapter<EventItem, EventsAdapter.ViewHolder>(DiffCallback) {

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: EventItem) {
            binding.eventTitle.text = event.title
            binding.categoryBadge.text = event.category.uppercase()
            binding.distanceBucket.text = "📍 ${event.distanceBucket}"

            val formattedDate = try {
                dateFormatter.format(Instant.parse(event.startsAt))
            } catch (_: Exception) {
                ""
            }
            binding.eventDateVenue.text = "$formattedDate · ${event.venueName}"

            val minPrice = event.tiers.minOfOrNull { it.priceInPaise } ?: 0
            val priceStr = if (minPrice == 0) "Free" else "₹${minPrice / 100}"
            binding.priceLabel.text = "From $priceStr"

            binding.root.setOnClickListener { onEventClicked(event) }
            binding.bookButton.setOnClickListener { onEventClicked(event) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<EventItem>() {
        override fun areItemsTheSame(oldItem: EventItem, newItem: EventItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: EventItem, newItem: EventItem): Boolean =
            oldItem == newItem
    }
}
