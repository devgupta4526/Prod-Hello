package com.example.romear.feature.events.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.romear.databinding.ItemUserTicketBinding
import com.example.romear.feature.events.domain.UserTicketItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class UserTicketsAdapter(
    private val onViewQrClicked: (UserTicketItem) -> Unit,
) : ListAdapter<UserTicketItem, UserTicketsAdapter.ViewHolder>(DiffCallback) {

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserTicketBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemUserTicketBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ticket: UserTicketItem) {
            binding.ticketEventTitle.text = ticket.eventTitle
            binding.ticketTierBadge.text = ticket.tierName.uppercase()
            binding.ticketStatus.text = when (ticket.status.uppercase()) {
                "CONFIRMED", "VALID", "ACTIVE" -> "Confirmed"
                "USED" -> "Used"
                "CANCELLED" -> "Cancelled"
                else -> ticket.status
            }

            val formattedDate = try {
                dateFormatter.format(Instant.parse(ticket.startsAt))
            } catch (_: Exception) {
                ""
            }
            binding.ticketVenueDate.text = if (formattedDate.isNotEmpty()) {
                "🗓️ $formattedDate\n📍 ${ticket.venueName}"
            } else {
                "📍 ${ticket.venueName}"
            }

            binding.viewQrButton.setOnClickListener { onViewQrClicked(ticket) }
            binding.root.setOnClickListener { onViewQrClicked(ticket) }
        }

    }

    companion object DiffCallback : DiffUtil.ItemCallback<UserTicketItem>() {
        override fun areItemsTheSame(oldItem: UserTicketItem, newItem: UserTicketItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: UserTicketItem, newItem: UserTicketItem): Boolean =
            oldItem == newItem
    }
}
