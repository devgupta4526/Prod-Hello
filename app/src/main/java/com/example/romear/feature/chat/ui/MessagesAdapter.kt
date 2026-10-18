package com.example.romear.feature.chat.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.romear.databinding.ItemMessageBinding
import com.example.romear.feature.chat.domain.ChatMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MessagesAdapter : ListAdapter<ChatMessage, MessagesAdapter.ViewHolder>(DiffCallback) {

    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            val formattedTime = try {
                timeFormatter.format(Instant.parse(message.createdAt))
            } catch (_: Exception) {
                ""
            }
            val delivery = if (message.isOutgoing) {
                if (message.readAt != null) "Read · $formattedTime" else "Sent · $formattedTime"
            } else {
                formattedTime
            }

            binding.bubbleView.bind(message.body, delivery, message.isOutgoing)

            val params = binding.bubbleView.layoutParams as FrameLayout.LayoutParams
            params.gravity = if (message.isOutgoing) Gravity.END else Gravity.START
            binding.bubbleView.layoutParams = params
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem == newItem
    }
}
