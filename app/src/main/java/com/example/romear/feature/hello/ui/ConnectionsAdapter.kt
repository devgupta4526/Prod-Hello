package com.example.romear.feature.hello.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.romear.databinding.ItemConnectionBinding
import com.example.romear.feature.hello.domain.ConnectionItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ConnectionsAdapter(
    private val onConnectionClicked: (ConnectionItem) -> Unit,
) : ListAdapter<ConnectionItem, ConnectionsAdapter.ViewHolder>(DiffCallback) {

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConnectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemConnectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConnectionItem) {
            binding.partnerName.text = item.partner.displayName
            binding.avatar.load(item.partner.photoUrl, item.partner.displayName, sizeDp = 52)

            val formattedDate = try {
                dateFormatter.format(Instant.parse(item.createdAt))
            } catch (_: Exception) {
                "Recent"
            }

            if (item.id.startsWith("demo")) {
                binding.connectionDate.text = "Awesome! Up for a quick filter coffee hangout?"
                binding.timestampText.text = "2m ago"
                binding.unreadBadge.visibility = android.view.View.VISIBLE
            } else {
                binding.connectionDate.text = "Tap to open chat"
                binding.timestampText.text = formattedDate
                binding.unreadBadge.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener { onConnectionClicked(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ConnectionItem>() {
        override fun areItemsTheSame(oldItem: ConnectionItem, newItem: ConnectionItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ConnectionItem, newItem: ConnectionItem): Boolean =
            oldItem == newItem
    }
}
