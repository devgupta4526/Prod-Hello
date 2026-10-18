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
            binding.avatar.load(item.partner.photoUrl, item.partner.displayName)

            val formattedDate = try {
                "Connected " + dateFormatter.format(Instant.parse(item.createdAt))
            } catch (_: Exception) {
                "Connected"
            }
            binding.connectionDate.text = formattedDate

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
